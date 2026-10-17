/*
 * The Orbitalis Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cbi.orbitalis.neoforge

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.vehicle.Minecart
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.integration.sable.AutomaticShipTravel
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferJournal
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPassengers
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPhase
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferRecord
import org.joml.Vector3d
import java.nio.file.Files
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object AutomaticTravelGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "automatic_empty", timeoutTicks = 1200)
    fun automaticEmptyShipRoundTrip(helper: GameTestHelper) = ship(helper, false)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "automatic_mounted", timeoutTicks = 1200)
    fun automaticMountedSurvivalPassenger(helper: GameTestHelper) = ship(helper, true)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "automatic_player", timeoutTicks = 1200)
    fun independentSurvivalPlayerRoundTrip(helper: GameTestHelper) {
        val packets = mutableListOf<net.minecraft.network.protocol.Packet<*>>()
        val p = GameTestPlayers.create(helper) { packets.add(it) }
        p.setGameMode(GameType.SURVIVAL)
        val surface = helper.level.server.overworld()
        check(p.teleportTo(surface, 100.0, (FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT + 1.0), 100.0, emptySet(), 31f, -12f))
        p.deltaMovement = Vec3(8.0, 0.4, -7.0)
        packets.clear()
        var returning = false
        helper.onEachTick {
            if (!returning) {
                if (!PlayerSpaceTravel.transfer(p)) {
                    // Accelerated test ticks must allow native chunk/entity I/O to complete.
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(2_000_000L)
                    return@onEachTick
                }
                helper.assertTrue(
                    p.level().dimension() == FlatPlanetSpace.SPACE &&
                        abs(p.y - (4096.0 + FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT + 1.0)) < 1e-6,
                    "Player mapping incorrect",
                )
                helper.assertTrue(p.deltaMovement.distanceTo(Vec3(8.0, 0.4, -7.0)) < 1e-8 && !p.isCreative, "Player motion/mode changed")
                val motion = packets.filterIsInstance<net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket>().lastOrNull()
                helper.assertTrue(
                    motion != null && abs(motion.xa - 3.9) < 0.001 && abs(motion.ya - 0.4) < 0.001,
                    "Native client velocity was not restored after the absolute teleport",
                )
                helper.assertTrue(
                    packets.indexOf(motion!!) >
                        packets.indexOfLast { it is net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket },
                    "Velocity packet preceded the position reset",
                )
                val exactMotion =
                    packets
                        .filterIsInstance<net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket>()
                        .map { it.payload() }
                        .filterIsInstance<org.cbi.orbitalis.minecraft.flight.FlightMotionPayload>()
                        .lastOrNull()
                helper.assertTrue(
                    exactMotion != null && exactMotion.velocity == Vector3(8.0, 0.4, -7.0) &&
                        exactMotion.dimension == FlatPlanetSpace.SPACE.location(),
                    "Unclamped local-player flight motion packet was lost",
                )
                val buffer =
                    net.minecraft.network.FriendlyByteBuf(
                        io.netty.buffer.Unpooled
                            .buffer(),
                    )
                try {
                    org.cbi.orbitalis.minecraft.flight.FlightMotionPayload.CODEC
                        .encode(buffer, checkNotNull(exactMotion))
                    helper.assertTrue(
                        org.cbi.orbitalis.minecraft.flight.FlightMotionPayload.CODEC
                            .decode(buffer) == exactMotion,
                        "Flight motion codec changed high-speed velocity",
                    )
                } finally {
                    buffer.release()
                }
                helper.assertTrue(!PlayerSpaceTravel.transfer(p), "Player bounced back at ascent boundary")
                p.setPos(100.0, (4096.0 + FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT - 1.0), 100.0)
                p.deltaMovement = Vec3(0.1, -0.4, 0.2)
                returning = true
                return@onEachTick
            }
            if (!PlayerSpaceTravel.transfer(p)) {
                // Accelerated test ticks must allow native chunk/entity I/O to complete.
                java.util.concurrent.locks.LockSupport
                    .parkNanos(2_000_000L)
                return@onEachTick
            }
            helper.assertTrue(
                p.level() === surface && abs(p.y - (FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT - 1.0)) < 1e-6 &&
                    p.deltaMovement.y == -0.4,
                "Return mapping changed motion",
            )
            helper.assertTrue(!PlayerSpaceTravel.transfer(p), "Player bounced into space after return")
            p.discard()
            helper.succeed()
        }
    }

    private fun verifyEntityCheckpoint(
        helper: GameTestHelper,
        world: net.minecraft.server.level.ServerLevel,
        vehicle: net.minecraft.world.entity.vehicle.MinecartChest,
    ) {
        val folder =
            net.minecraft.world.level.dimension.DimensionType
                .getStorageFolder(
                    world.dimension(),
                    world.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),
                ).resolve("entities")
        val chunk =
            net.minecraft.world.level
                .ChunkPos(vehicle.blockPosition())
        val path = folder.resolve("r.${chunk.regionX}.${chunk.regionZ}.mca")
        helper.assertTrue(Files.isRegularFile(path), "Native entity region was not checkpointed")
        net.minecraft.world.level.chunk.storage
            .RegionFile(
                net.minecraft.world.level.chunk.storage
                    .RegionStorageInfo("orbitalis-test", world.dimension(), "entities"),
                path,
                folder,
                false,
            ).use { region ->
                val input = checkNotNull(region.getChunkDataInputStream(chunk)) { "Native entity chunk is absent" }
                val data =
                    input.use {
                        net.minecraft.nbt.NbtIo
                            .read(it)
                    }
                val saved =
                    data.getList("Entities", 10).map { it as net.minecraft.nbt.CompoundTag }.singleOrNull {
                        it.hasUUID("UUID") &&
                            it.getUUID("UUID") == vehicle.uuid
                    }
                helper.assertTrue(saved != null && saved.getList("Items", 10).size == 1, "Native entity checkpoint lost cart inventory")
            }
    }

    private fun ship(
        helper: GameTestHelper,
        mounted: Boolean,
    ) {
        val server = helper.level.server
        val surface = server.overworld()
        val space = checkNotNull(server.getLevel(FlatPlanetSpace.SPACE))
        val from = checkNotNull(SubLevelContainer.getContainer(surface))
        val to = checkNotNull(SubLevelContainer.getContainer(space))
        val pose = Pose3d().also { it.position().set(-4500.0, FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT + 36.0, -500.0) }
        val source = from.allocateNewSubLevel(pose) as ServerSubLevel
        val id = source.uniqueId
        val owner = Any()
        SpaceTravelReservations.reserve(id, owner)
        source.plot.newEmptyChunk(source.plot.centerChunk)
        source.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
        source.plot.embeddedLevelAccessor.setBlock(BlockPos(0, 1, 0), Blocks.RAIL.defaultBlockState(), 3)
        source.updateLastPose()
        from.addForceLoadTicket(source, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
        val player = if (mounted) GameTestPlayers.create(helper).also { it.setGameMode(GameType.SURVIVAL) } else null
        var cart: net.minecraft.world.entity.vehicle.AbstractMinecart? = null
        val folder = Files.createTempDirectory("orbitalis-automatic-travel-")
        val pilotSync =
            object : ShipTransferPilotSync {
                override fun hasShip(
                    player: ServerPlayer,
                    body: ServerSubLevel,
                ) = true

                override fun isReady(
                    player: ServerPlayer,
                    body: ServerSubLevel,
                ): Boolean {
                    runCatching { GameTestPlayers.acknowledgeTeleport(player) }
                    return true
                }
            }
        var manager = SableShipTransfer(server, folder, pilotSync)
        val automatic = AutomaticShipTravel(server, manager, owner)
        var phase = 0
        var recoveryWrite: java.util.concurrent.CompletableFuture<kotlin.Unit>? = null
        val recoveryJournal = ShipTransferJournal(folder)
        var expectedVelocity = Vector3d()
        var expectedCart: java.util.UUID? = null

        fun cleanup() {
            recoveryJournal.close()
            automatic.close()
            manager.close()
            for (world in listOf(surface, space)) {
                val container = checkNotNull(SubLevelContainer.getContainer(world))
                (container.getSubLevel(id) as? ServerSubLevel)?.let {
                    container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
                    container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
                }
                expectedCart?.let { world.getEntity(it)?.remove(net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) }
                SubLevelPhysicsSystem.require(world).paused = false
            }
            player?.discard()
            SpaceTravelReservations.release(id, owner)
        }
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testPassed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()

                override fun testFailed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}
            },
        )

        fun run() {
            surface.resetEmptyTime()
            space.resetEmptyTime()
            player?.let { runCatching { GameTestPlayers.acknowledgeTeleport(it) } }
            if (phase != 3) manager.tick()
            helper.assertTrue(manager.error == null, "Transfer failed at phase $phase: ${manager.error}")
            if (phase == 0 && !manager.busy && source.massTracker.centerOfMass != null) {
                val reading = SableFlightGravity.reading(source)
                val handle = SubLevelPhysicsSystem.require(surface).getPhysicsHandle(source)
                handle.addLinearAndAngularVelocity(Vector3d(1.0, 20.0, 2.0).sub(reading.velocity), Vector3d())
                val b = source.boundingBox()
                val local = source.plot.centerBlock
                cart =
                    (
                        if (mounted) {
                            Minecart(surface, local.x + 0.5, local.y + 1.1, local.z + 0.5)
                        } else {
                            net.minecraft.world.entity.vehicle
                                .MinecartChest(surface, local.x + 0.5, local.y + 1.1, local.z + 0.5)
                        }
                    ).also { c ->
                        c.setNoGravity(true)
                        (c as? net.minecraft.world.entity.vehicle.MinecartChest)?.setItem(
                            0,
                            net.minecraft.world.item
                                .ItemStack(net.minecraft.world.item.Items.DIAMOND, 4),
                        )
                        check(surface.addFreshEntity(c))
                        if (player != null) {
                            check(player.teleportTo(surface, -4500.0, b.maxY() + 0.5, -500.0, emptySet(), 0f, 0f))
                            check(player.startRiding(c, true))
                        }
                        expectedCart = c.uuid
                    }
                expectedVelocity = SableFlightGravity.reading(source).velocity
                phase = 10
            } else if (phase == 10 && (expectedCart == null || surface.getEntity(expectedCart!!) != null)) {
                if (player != null) {
                    helper.assertTrue(player in ShipTransferPassengers.nearby(source), "Mounted player missing from ship selection")
                }
                expectedVelocity = SableFlightGravity.reading(source).velocity
                automatic.tick()
                if (manager.busy) phase = 1
            } else if (phase == 1 && !manager.busy) {
                val body = to.getSubLevel(id) as? ServerSubLevel
                helper.assertTrue(body != null && from.getSubLevel(id) == null, "Empty/mounted ship did not transfer exactly once")
                body!!
                helper.assertTrue(
                    listOf(surface, space).all { world ->
                        world.getAllEntities().filterIsInstance<net.minecraft.world.entity.item.ItemEntity>().none {
                            it.item.`is`(net.minecraft.world.item.Items.DIAMOND)
                        }
                    },
                    "Vehicle removal duplicated inventory as dropped items",
                )
                helper.assertTrue(
                    SableFlightGravity.reading(body).velocity.distance(expectedVelocity) < 0.05,
                    "Ship velocity changed during handoff",
                )
                if (player != null) {
                    helper.assertTrue(
                        player.level() === space && player.vehicle?.uuid == expectedCart && !player.isCreative,
                        "Survival passenger lost seat or changed dimension/mode",
                    )
                    helper.assertTrue(surface.getEntity(expectedCart!!) == null, "Source cart was duplicated")
                }
                val s = SableFlightGravity.reading(body)
                val dy = 4096.0 + FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT - 14.0 - s.position.y
                val handle = SubLevelPhysicsSystem.require(space).getPhysicsHandle(body)
                handle.teleport(Vector3d(body.logicalPose().position()).add(0.0, dy, 0.0), body.logicalPose().orientation())
                handle.addLinearAndAngularVelocity(Vector3d(1.0, -10.0, 2.0).sub(s.velocity), Vector3d())
                val c = checkNotNull(space.getEntity(expectedCart!!))
                if (!body.plot.contains(c.position())) c.setPos(c.x, c.y + dy, c.z)
                player?.setPos(player.x, player.y + dy, player.z)
                expectedVelocity = SableFlightGravity.reading(body).velocity
                phase = 11
            } else if (phase == 11) {
                val body = to.getSubLevel(id) as ServerSubLevel
                expectedVelocity = SableFlightGravity.reading(body).velocity
                automatic.tick()
                if (manager.busy) phase = 2
            } else if (phase == 2 && !manager.busy) {
                val body = from.getSubLevel(id) as? ServerSubLevel
                helper.assertTrue(body != null && to.getSubLevel(id) == null, "Ship failed to return exactly once")
                if (player !=
                    null
                ) {
                    helper.assertTrue(
                        player.level() === surface && player.vehicle?.uuid == expectedCart,
                        "Returning passenger lost seat",
                    )
                }
                val party = ShipTransferPassengers.nearby(body!!).toMutableSet()
                player?.let { party.add(it) }
                val passengers = ShipTransferPassengers.capture(body, party)
                val snapshot = SableShipSnapshot.capture(body, ShipTransferPassengers.ids(passengers))
                val destination =
                    SableShipSnapshot.forDestination(
                        SableShipSnapshot.translated(snapshot, 4096.0, FlatPlanetSpace.SPACE),
                        surface,
                        space,
                    )
                val record =
                    ShipTransferRecord(
                        java.util.UUID.randomUUID(),
                        id,
                        player?.uuid,
                        false,
                        snapshot,
                        destination,
                        player?.let { SableShipTransfer.playerState(it) } ?: net.minecraft.nbt.CompoundTag(),
                        (player?.let { SableShipTransfer.playerState(it) } ?: net.minecraft.nbt.CompoundTag()).also {
                            if (player != null) it.putDouble("y", it.getDouble("y") + 4096.0)
                        },
                        net.minecraft.nbt.CompoundTag(),
                        ShipTransferPhase.COMMITTED,
                        orbitalDimension = FlatPlanetSpace.SPACE,
                        surfaceDimension = Level.OVERWORLD,
                        sourcePassengers = passengers,
                        destinationPassengers = ShipTransferPassengers.translated(passengers, snapshot, destination),
                    )
                helper.assertTrue(ShipTransferRecord.decode(record.encode()).pilot == player?.uuid, "Recovery pilot identity changed")
                manager.close()
                recoveryWrite = recoveryJournal.write(record)
                phase = 3
            } else if (phase == 3 && recoveryWrite!!.isDone) {
                recoveryWrite!!.join()
                manager = SableShipTransfer(server, folder, pilotSync)
                phase = 4
            } else if (phase == 4 && !manager.busy) {
                helper.assertTrue(to.getSubLevel(id) != null && from.getSubLevel(id) == null, "Unmanned recovery lost authority")
                if (player == null) {
                    helper.assertTrue(
                        !SubLevelPhysicsSystem.require(surface).paused && !SubLevelPhysicsSystem.require(space).paused,
                        "Unmanned recovery waited for a nonexistent pilot",
                    )
                    helper.succeed()
                    return
                }
                manager.loggedIn(player)
                phase = 5
            } else if (phase == 5 && !manager.busy) {
                helper.assertTrue(
                    player!!.level() === space && player.vehicle?.uuid == expectedCart,
                    "Recovered passenger did not resume the saved mount graph",
                )
                helper.assertTrue(
                    surface.getEntity(expectedCart!!) == null && space.getEntity(expectedCart!!) != null,
                    "Recovered cart was duplicated or lost",
                )
                helper.succeed()
                return
            }
            helper.runAfterDelay(1) { run() }
        }
        helper.runAfterDelay(5) { run() }
    }
}
