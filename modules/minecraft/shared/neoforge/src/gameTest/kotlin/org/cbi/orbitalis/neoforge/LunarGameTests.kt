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
import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.vehicle.Minecart
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.integration.sable.AutomaticShipTravel
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipFrameTransform
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync
import org.cbi.orbitalis.minecraft.moon.LunarChunkGenerator
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.joml.Vector3d

@PrefixGameTestTemplate(false)
object LunarGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon_player", timeoutTicks = 1600)
    fun fastCreativeLunarPlayer(helper: GameTestHelper) =
        freePlayer(helper, GameType.CREATIVE, Vector3(0.0, 1560.0, 0.0), Vector3(0.0, -400.0, 0.0))

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon_player", timeoutTicks = 1600)
    fun fastSpectatorLunarEdge(helper: GameTestHelper) =
        freePlayer(helper, GameType.SPECTATOR, Vector3(2895.0, 0.0, 0.0), Vector3(-400.0, 0.0, 0.0))

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon_player", timeoutTicks = 1600)
    fun fastSurvivalLunarUnderside(helper: GameTestHelper) =
        freePlayer(helper, GameType.SURVIVAL, Vector3(0.0, -1560.0, 0.0), Vector3(0.0, 400.0, 0.0))

    private fun freePlayer(
        helper: GameTestHelper,
        mode: GameType,
        local: Vector3,
        localVelocity: Vector3,
    ) {
        val packets = mutableListOf<net.minecraft.network.protocol.Packet<*>>()
        val player = GameTestPlayers.create(helper) { packets.add(it) }
        player.setGameMode(mode)
        val space = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val moon = checkNotNull(helper.level.server.getLevel(MoonWorld.KEY))
        val travel = org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
        var returning = false
        var attempts = 0
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testPassed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    travel.close(player)
                    player.discard()
                }

                override fun testFailed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    travel.close(player)
                    player.discard()
                }

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}
            },
        )
        helper.onEachTick {
            // Real asynchronous terrain/entity loading must not race thousands of accelerated fixture ticks.
            Thread.sleep(25)
            repeat(16) { for (level in helper.level.server.allLevels) level.chunkSource.pollTask() }
            val sample = CelestialFlight.sample(helper.level.server)
            val from = if (returning) moon else space
            val target = if (returning) space else moon
            val lunarPosition = if (returning) local.normalized() * (if (local.x != 0.0) 3210.0 else 1874.0) else local
            val state =
                if (returning) {
                    CartesianState(lunarPosition, localVelocity * -1.0)
                } else {
                    sample.state(CartesianState(lunarPosition, localVelocity), MoonWorld.KEY, FlatPlanetSpace.SPACE)
                }
            if (player.level() !==
                from
            ) {
                check(player.teleportTo(from, state.position.x, state.position.y, state.position.z, emptySet(), 31f, -12f))
            }
            player.setPos(state.position.x, state.position.y, state.position.z)
            player.deltaMovement =
                net.minecraft.world.phys
                    .Vec3(state.velocity.x / 20.0, state.velocity.y / 20.0, state.velocity.z / 20.0)
            runCatching { GameTestPlayers.acknowledgeTeleport(player) }
            val look = Vector3(player.lookAngle.x, player.lookAngle.y, player.lookAngle.z)
            packets.clear()
            val expected = sample.state(state, from.dimension(), target.dimension())
            val a =
                org.cbi.orbitalis.minecraft.flight.PlayerGravity
                    .acceleration(player)
            val beforeForce =
                sample.frames.frame(CelestialFlight.world(from.dimension())).toParentAcceleration(state, Vector3(a.x, a.y, a.z) * 400.0)
            if (!travel.transfer(player)) {
                if (++attempts % 200 == 0) {
                    val next = travel.destination(player.level(), state.position, state.velocity)
                    println("LUNAR_PLAYER_WAIT mode=$mode returning=$returning alive=${player.isAlive}")
                    println("LUNAR_PLAYER_WAIT dim=${player.level().dimension()} pos=${player.position()} destination=$next")
                }
                return@onEachTick
            }
            println("LUNAR_PLAYER_HANDOFF mode=$mode returning=$returning")
            helper.assertTrue(player.level() === target, "Lunar player went to the wrong world")
            val b =
                org.cbi.orbitalis.minecraft.flight.PlayerGravity
                    .acceleration(player)
            val afterForce =
                sample.frames
                    .frame(
                        CelestialFlight.world(target.dimension()),
                    ).toParentAcceleration(expected, Vector3(b.x, b.y, b.z) * 400.0)
            helper.assertTrue((beforeForce - afterForce).length() < 1e-7, "Player gravity jumped at the lunar handoff")
            helper.assertTrue(
                (Vector3(player.x, player.y, player.z) - expected.position).length() < 1e-6,
                "Lunar handoff displaced the player",
            )
            helper.assertTrue(
                (
                    Vector3(
                        player.deltaMovement.x,
                        player.deltaMovement.y,
                        player.deltaMovement.z,
                    ) * 20.0 - expected.velocity
                ).length() < 1e-6,
                "Server velocity changed",
            )
            // Native Mth trig lookup quantizes the returned look vector by about 1e-4.
            val transformedLook = sample.direction(look, from.dimension(), target.dimension())
            helper.assertTrue(
                (transformedLook - Vector3(player.lookAngle.x, player.lookAngle.y, player.lookAngle.z)).length() < 2e-4,
                "Aim changed in physical axes return=$returning from=${from.dimension()} " +
                    "expected=$transformedLook actual=${player.lookAngle} yaw=${player.yRot} pitch=${player.xRot}",
            )
            val payload =
                packets
                    .filterIsInstance<net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket>()
                    .map { it.payload() }
                    .filterIsInstance<org.cbi.orbitalis.minecraft.flight.FlightMotionPayload>()
                    .last()
            val captured = state.velocity * 0.05 + Vector3(1.3, -0.7, 2.1)
            val expectedClient = sample.state(state.copy(velocity = captured * 20.0), from.dimension(), target.dimension()).velocity * 0.05
            helper.assertTrue(
                (payload.transformCaptured(captured) - expectedClient).length() < 1e-8,
                "Captured client motion reused old axes or omitted lunar transport velocity",
            )
            val buffer =
                net.minecraft.network.FriendlyByteBuf(
                    io.netty.buffer.Unpooled
                        .buffer(),
                )
            try {
                org.cbi.orbitalis.minecraft.flight.FlightMotionPayload.CODEC
                    .encode(buffer, payload)
                val decoded =
                    org.cbi.orbitalis.minecraft.flight.FlightMotionPayload.CODEC
                        .decode(buffer)
                helper.assertTrue(decoded == payload, "Motion transform codec did not roundtrip")
                buffer.readerIndex(0)
                buffer.setDouble(buffer.writerIndex() - 32, -1.0)
                helper.assertTrue(
                    runCatching {
                        org.cbi.orbitalis.minecraft.flight.FlightMotionPayload.CODEC
                            .decode(buffer)
                    }.isFailure,
                    "Motion codec accepted a reflected frame",
                )
            } finally {
                buffer.release()
            }
            runCatching { GameTestPlayers.acknowledgeTeleport(player) }
            helper.assertTrue(!travel.transfer(player), "Lunar player bounced immediately after arrival")
            helper.assertTrue(player.gameMode.gameModeForPlayer == mode, "Lunar transfer changed game mode")
            if (returning) helper.succeed() else returning = true
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon", timeoutTicks = 400)
    fun nativeLunarTerrain(helper: GameTestHelper) {
        val moon = checkNotNull(helper.level.server.getLevel(MoonWorld.KEY))
        val generator = moon.chunkSource.generator as LunarChunkGenerator
        helper.assertTrue(!moon.dimensionType().hasCeiling() && moon.dimensionType().hasSkyLight(), "Invalid lunar sky type")
        val saved =
            helper.level.server
                .overworld()
                .worldBorder
                .createSettings()
        try {
            helper.level.server
                .overworld()
                .worldBorder.size = 1024.0
            helper.assertTrue(moon.worldBorder.size > 2800.0, "Moon inherited terrestrial wall")
        } finally {
            helper.level.server
                .overworld()
                .worldBorder
                .applySettings(saved)
        }
        for ((x, z) in listOf(0 to 0, -1400 to -1400, 1399 to -1400, -1400 to 1399, 1399 to 1399)) {
            val chunk = moon.getChunk(x shr 4, z shr 4)
            val top = generator.terrain.height(x, z)!!
            helper.assertTrue(chunk.persistedStatus == ChunkStatus.FULL && chunk.isLightCorrect, "Lunar chunk did not complete")
            helper.assertTrue(chunk.getBlockState(BlockPos(x, top, z)).`is`(Blocks.ANDESITE), "Missing lunar top")
            helper.assertTrue(chunk.getBlockState(BlockPos(x, -64, z)).`is`(Blocks.BASALT), "Missing finite lunar underside")
            helper.assertTrue(chunk.getBlockState(BlockPos(x, top + 1, z)).isAir, "Lunar column height changed")
            helper.assertTrue(
                chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x and 15, z and 15) == top,
                "Lunar heightmap differs from terrain",
            )
            helper.assertTrue(chunk.allStarts.isEmpty(), "Lunar structures appeared")
        }
        for ((x, z) in listOf(-1401 to 0, 1400 to 0, 0 to -1401, 0 to 1400, 1456 to 1456)) {
            val chunk = moon.getChunk(x shr 4, z shr 4)
            for (y in -64..319) helper.assertTrue(chunk.getBlockState(BlockPos(x, y, z)).isAir, "Exterior lunar floor generated")
        }
        // Runtime construction stays normal, including outside the initial footprint.
        val marker = BlockPos(1401, 150, 0)
        moon.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 2)
        helper.assertTrue(moon.getBlockState(marker).`is`(Blocks.GOLD_BLOCK), "Native lunar construction rejected")
        moon.removeBlock(marker, false)
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon", timeoutTicks = 300)
    fun nativeLunarGroundContact(helper: GameTestHelper) {
        val moon = checkNotNull(helper.level.server.getLevel(MoonWorld.KEY))
        val container = checkNotNull(SubLevelContainer.getContainer(moon))
        moon.getChunk(50 shr 4, -145 shr 4)
        val ground = moon.getHeight(Heightmap.Types.WORLD_SURFACE, 50, -145)
        val pose = Pose3d().also { it.position().set(50.0, ground + 5.0, -145.0) }
        val body = container.allocateNewSubLevel(pose) as ServerSubLevel
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
        body.updateLastPose()
        val owner = Any()
        SpaceTravelReservations.reserve(body.uniqueId, owner)
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
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

                private fun cleanup() {
                    SpaceTravelReservations.release(body.uniqueId, owner)
                    if (!body.isRemoved) {
                        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
                        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                    }
                }
            },
        )
        var ticks = 0

        fun step() {
            moon.resetEmptyTime()
            java.util.concurrent.locks.LockSupport
                .parkNanos(10_000_000L)
            if (body.massTracker.centerOfMass != null) {
                val reading = SableFlightGravity.reading(body)
                if (ticks == 0) {
                    SubLevelPhysicsSystem.require(moon).getPhysicsHandle(body).addLinearAndAngularVelocity(
                        Vector3d(0.0, -44.0, 0.0).sub(reading.velocity),
                        Vector3d(),
                    )
                    SubLevelPhysicsSystem.require(moon).pipeline.prePhysicsTicks()
                    helper.assertTrue(SableFlightGravity.reading(body).velocity.y < -43.0, "Fast impact fixture did not apply speed")
                }
                helper.assertTrue(
                    reading.position.y >= ground - 2.0,
                    "Native lunar collider absent: ground=$ground reading=$reading bounds=${body.boundingBox()}",
                )
                if (++ticks >= 120) {
                    helper.assertTrue(kotlin.math.abs(reading.velocity.y) < 0.5, "Native lunar body did not rest")
                    helper.succeed()
                    return
                }
            }
            helper.runAfterDelay(1) { step() }
        }
        step()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon_transfer", timeoutTicks = 2400)
    fun lunarShipRoundTrip(helper: GameTestHelper) = roundTrip(helper, Vector3(100.0, 510.0, -150.0), Vector3(0.0, 1.0, 0.0), true)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon_edge", timeoutTicks = 1200)
    fun lunarSideRoundTrip(helper: GameTestHelper) = roundTrip(helper, Vector3(1850.0, 0.0, 0.0), Vector3(1.0, 0.0, 0.0), false)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "moon_under", timeoutTicks = 1200)
    fun lunarUndersideRoundTrip(helper: GameTestHelper) = roundTrip(helper, Vector3(0.0, -514.0, 0.0), Vector3(0.0, -1.0, 0.0), false)

    private fun roundTrip(
        helper: GameTestHelper,
        start: Vector3,
        outward: Vector3,
        groundContact: Boolean,
    ) {
        val server = helper.level.server
        val moon = checkNotNull(server.getLevel(MoonWorld.KEY))
        val space = checkNotNull(server.getLevel(FlatPlanetSpace.SPACE))
        val levels = listOf(moon, space)
        val containers = levels.associateWith { checkNotNull(SubLevelContainer.getContainer(it)) }
        repeat(6000) {
            checkNotNull(
                org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation
                    .data(server),
            ).advanceClock()
        }
        val initial = CelestialFlight.sample(server)
        val anchor = initial.point(start, MoonWorld.KEY, space.dimension())
        val lunarFrame = initial.frames.profile.lunarSurfaceFrame(initial.frames.tick)
        val pose =
            Pose3d().also {
                it.position().set(anchor.x, anchor.y, anchor.z)
                it.orientation().rotateZ(
                    kotlin.math.atan2(lunarFrame.origin.position.y, lunarFrame.origin.position.x) + kotlin.math.PI / 2.0,
                )
            }
        val body = containers.getValue(space).allocateNewSubLevel(pose) as ServerSubLevel
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos(1, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos(0, 1, 0), Blocks.CHEST.defaultBlockState(), 3)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos(1, 1, 0), Blocks.RAIL.defaultBlockState(), 3)

        fun cargo(b: ServerSubLevel): ChestBlockEntity =
            b.plot.loadedChunks
                .flatMap { it.chunk.blockEntities.values }
                .filterIsInstance<ChestBlockEntity>()
                .single()
        cargo(body).setItem(0, ItemStack(Items.COBBLESTONE, 16))
        val owner = Any()
        SpaceTravelReservations.reserve(body.uniqueId, owner)
        containers.getValue(space).addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
        val player = GameTestPlayers.create(helper).also { it.setGameMode(GameType.SURVIVAL) }
        var cart: Minecart? = null
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
        val manager =
            SableShipTransfer(
                server,
                java.nio.file.Files
                    .createTempDirectory("orbitalis-lunar-transfer-"),
                pilotSync,
            )
        val automatic = AutomaticShipTravel(server, manager, owner)
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

                private fun cleanup() {
                    automatic.close()
                    manager.close()
                    player.stopRiding()
                    player.discard()
                    cart?.uuid?.let { id -> levels.forEach { it.getEntity(id)?.discard() } }
                    SpaceTravelReservations.release(body.uniqueId, owner)
                    // A fixture may start during the real coordinator's asynchronous startup pause.
                    // Restoring that transient state would freeze all subsequent batches.
                    CelestialFlight.dimensions.mapNotNull(server::getLevel).forEach { world ->
                        SubLevelPhysicsSystem.require(world).paused = false
                    }
                    containers.forEach { (_, container) ->
                        (container.getSubLevel(body.uniqueId) as? ServerSubLevel)?.let { ship ->
                            container.removeForceLoadTicket(
                                ship,
                                SubLevelLoadingTicketType.COMMAND_FORCED,
                                net.minecraft.util.Unit.INSTANCE,
                            )
                            container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED)
                        }
                    }
                }
            },
        )
        val ideal = Vector3d(91.993217153, -0.749139283, 0.0118129351)
        val rounded = Vector3d(ideal.x.toFloat().toDouble(), ideal.y.toFloat().toDouble(), ideal.z.toFloat().toDouble())
        helper.assertTrue(SableShipSnapshot.matchesNativeVelocity(rounded, ideal), "Native float rounding rejected")
        helper.assertTrue(
            !SableShipSnapshot.matchesNativeVelocity(Vector3d(rounded).add(0.0001, 0.0, 0.0), ideal),
            "Speed corruption accepted",
        )
        var phase = 0
        var expected: CompoundTag? = null
        var landed = false
        var departureDistance = 0.0

        fun expectedAt(
            b: ServerSubLevel,
            target: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
        ): CompoundTag {
            val sample = CelestialFlight.sample(server)
            val snapshot = SableShipSnapshot.capture(b, setOf(player.uuid, checkNotNull(cart).uuid))
            return ShipFrameTransform(sample, b.level.dimension(), target).ship(snapshot, SableFlightGravity.reading(b).position)
        }

        fun assertArrival(
            b: ServerSubLevel,
            target: CompoundTag,
        ) {
            val pose = SableNBTUtils.readPose3d(target.getCompound("pose"))
            helper.assertTrue(b.logicalPose().position().distance(pose.position()) < 1e-6, "Handoff position changed")
            helper.assertTrue(kotlin.math.abs(b.logicalPose().orientation().dot(pose.orientation())) > 0.999999, "Handoff attitude changed")
            val handle = SubLevelPhysicsSystem.require(b.level).getPhysicsHandle(b)
            helper.assertTrue(
                SableShipSnapshot.matchesNativeVelocity(
                    handle.getLinearVelocity(Vector3d()),
                    SableShipSnapshot.vector(target, "exact_linear"),
                ),
                "Handoff speed changed",
            )
            helper.assertTrue(cargo(b).getItem(0).count == 16, "Lunar flight lost cargo")
            helper.assertTrue(
                player.level() === b.level && player.vehicle?.uuid == cart?.uuid && !player.isCreative,
                "Survival pilot lost seat",
            )
        }

        fun run() {
            // Cold lunar terrain generation needs wall time; do not fly through the slab in a
            // headless server running hundreds of physics ticks per real second.
            Thread.sleep(if (groundContact) 2L else 50L)
            repeat(32) { for (world in server.allLevels) world.chunkSource.pollTask() }
            levels.forEach { it.resetEmptyTime() }
            runCatching { GameTestPlayers.acknowledgeTeleport(player) }
            manager.tick()
            helper.assertTrue(manager.error == null, "Lunar transfer failed: ${manager.error}")
            when {
                phase == 0 && !manager.busy && body.massTracker.centerOfMass != null -> {
                    val sample = CelestialFlight.sample(server)
                    val reading = SableFlightGravity.reading(body)
                    val local = sample.point(reading.position, space.dimension(), MoonWorld.KEY)
                    val flight = sample.state(CartesianState(local, outward * -12.0), MoonWorld.KEY, space.dimension())
                    val handle = SubLevelPhysicsSystem.require(space).getPhysicsHandle(body)
                    val spin = sample.angular(Vector3.ZERO, MoonWorld.KEY, space.dimension())
                    handle.addLinearAndAngularVelocity(
                        Vector3d(flight.velocity.x, flight.velocity.y, flight.velocity.z).sub(reading.velocity),
                        Vector3d(spin.x, spin.y, spin.z).sub(handle.getAngularVelocity(Vector3d())),
                    )
                    val p = body.plot.centerBlock
                    cart =
                        Minecart(space, p.x + 1.5, p.y + 1.1, p.z + 0.5).also { c ->
                            c.setNoGravity(true)
                            check(space.addFreshEntity(c))
                            check(player.teleportTo(space, reading.position.x, reading.position.y, reading.position.z, emptySet(), 0f, 0f))
                            check(player.startRiding(c, true))
                        }
                    phase = 10
                }

                phase == 10 -> {
                    if (!groundContact && outward.y < 0.0) {
                        val p =
                            CelestialFlight
                                .sample(
                                    server,
                                ).point(SableFlightGravity.reading(body).position, space.dimension(), MoonWorld.KEY)
                        helper.assertTrue(p.y < -80.0, "Lunar ingress outran native chunk readiness: $p")
                    }
                    expected = expectedAt(body, MoonWorld.KEY)
                    automatic.tick()
                    if (manager.busy) phase = 1
                }

                phase == 1 && !manager.busy -> {
                    val arrived = containers.getValue(moon).getSubLevel(body.uniqueId) as? ServerSubLevel
                    helper.assertTrue(arrived != null, "Automatic lunar descent did not transfer")
                    assertArrival(checkNotNull(arrived), checkNotNull(expected))
                    if (!groundContact && outward.y < 0.0) {
                        helper.assertTrue(
                            SableFlightGravity.reading(arrived).position.y < -80.0,
                            "Underside entry was delayed past the native slab: ${SableFlightGravity.reading(arrived)}",
                        )
                    }
                    val up = arrived.logicalPose().orientation().transform(Vector3d(0.0, 1.0, 0.0))
                    helper.assertTrue(
                        up.y > 0.99,
                        "Lunar fixture arrived tilted: up=$up orientation=${arrived.logicalPose().orientation()}",
                    )
                    if (groundContact) {
                        phase = 20
                    } else {
                        val v = outward * 200.0
                        SubLevelPhysicsSystem.require(moon).getPhysicsHandle(arrived).addLinearAndAngularVelocity(
                            Vector3d(v.x, v.y, v.z).sub(SableFlightGravity.reading(arrived).velocity),
                            Vector3d(),
                        )
                        SubLevelPhysicsSystem.require(moon).pipeline.prePhysicsTicks()
                        val actual = SableFlightGravity.reading(arrived).velocity
                        helper.assertTrue(
                            actual.dot(Vector3d(outward.x, outward.y, outward.z)) > 190.0,
                            "Lunar departure fixture did not apply outward speed: $actual",
                        )
                        landed = true
                        phase = 21
                    }
                }

                phase == 20 -> {
                    val b = containers.getValue(moon).getSubLevel(body.uniqueId) as ServerSubLevel
                    val reading = SableFlightGravity.reading(b)
                    val ground = moon.getHeight(Heightmap.Types.WORLD_SURFACE, reading.position.x.toInt(), reading.position.z.toInt())
                    helper.assertTrue(
                        reading.position.y >= ground - 16.0,
                        "Transferred hull passed through lunar terrain: $reading ground=$ground",
                    )
                    if (b.boundingBox().minY() in (ground - 2.0)..(ground + 0.5) && kotlin.math.abs(reading.velocity.y) < 0.5) {
                        println("ORBITALIS_LUNAR_CONTACT position=${reading.position} ground=$ground speed=${reading.velocity.length()}")
                        landed = true
                        val handle = SubLevelPhysicsSystem.require(moon).getPhysicsHandle(b)
                        handle.addLinearAndAngularVelocity(Vector3d(0.0, 65.0, 0.0).sub(reading.velocity), Vector3d())
                        phase = 21
                    }
                }

                phase == 21 -> {
                    val b = containers.getValue(moon).getSubLevel(body.uniqueId) as ServerSubLevel
                    val distance =
                        org.cbi.orbitalis.core.planet.PlanetFlightEnvelope.moon
                            .distance(SableFlightGravity.reading(b).position)
                    if (!groundContact) {
                        helper.assertTrue(
                            distance <= org.cbi.orbitalis.core.planet.LunarSurface.ASCENT_HEIGHT + 512.0,
                            "Lunar departure outran native chunk readiness: distance=$distance",
                        )
                    }
                    expected = expectedAt(b, space.dimension())
                    automatic.tick()
                    if (manager.busy) {
                        departureDistance = distance
                        phase = 2
                    }
                }

                phase == 2 && !manager.busy -> {
                    val returned = containers.getValue(space).getSubLevel(body.uniqueId) as? ServerSubLevel
                    helper.assertTrue(returned != null && landed, "Lunar landing and ascent did not complete")
                    assertArrival(checkNotNull(returned), checkNotNull(expected))
                    println(
                        "ORBITALIS_LUNAR_ROUNDTRIP seatedPilot=true cargo=16 groundContact=$groundContact outward=$outward departureDistance=$departureDistance",
                    )
                    helper.succeed()
                    return
                }
            }
            helper.runAfterDelay(1) { run() }
        }
        helper.runAfterDelay(5) { run() }
    }
}
