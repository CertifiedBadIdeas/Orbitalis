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
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.orbit.FlightFrame
import org.cbi.orbitalis.core.orbit.OrbitalSector
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.OrbitalSectors
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.joml.Vector3d
import java.nio.file.Files

@PrefixGameTestTemplate(false)
object OrbitalSectorGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sectors", timeoutTicks = 200)
    fun nativeSectorLifecycle(helper: GameTestHelper) {
        val server = helper.level.server
        val key = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(35, -4, 8)))
        val level = checkNotNull(OrbitalSectors.ensure(server, key))
        helper.assertTrue(server.getLevel(key) === level, "Native sector lookup changed identity")
        helper.assertTrue(level.dimension() == key, "Native sector has wrong dimension")
        helper.assertTrue(
            org.cbi.orbitalis.minecraft.space.SpaceWorlds
                .objects(level),
            "Sector not sparse vacuum",
        )
        helper.assertTrue(SubLevelContainer.getContainer(level) != null, "Native Sable container was not installed")
        val tag = OrbitalSectors.Catalogue.open(server).save(net.minecraft.nbt.CompoundTag(), server.registryAccess())
        val restored = OrbitalSectors.Catalogue.load(tag)
        helper.assertTrue(restored.compatible && key.location().toString() in restored.keys, "Sector catalogue did not roundtrip")
        val coldKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(36, -4, 8)))
        val catalogue = OrbitalSectors.Catalogue.open(server)
        catalogue.keys.add(coldKey.location().toString())
        catalogue.setDirty()
        server.overworld().dataStorage.save()
        OrbitalSectors.restore(server)
        helper.assertTrue(server.allLevels.none { it.dimension() == coldKey }, "Startup eagerly materialized a cold sector")
        val cold = checkNotNull(server.getLevel(coldKey))
        helper.assertTrue(cold.dimension() == coldKey && cold !== level, "Persisted native lookup did not materialize independent sector")
        val file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/orbitalis_orbital_sectors.dat")
        tag.putInt("Version", 999)
        val future = OrbitalSectors.Catalogue.load(tag)
        helper.assertTrue(
            !future.compatible && future.save(net.minecraft.nbt.CompoundTag(), server.registryAccess()) == tag,
            "Unknown catalogue was not preserved",
        )
        // The native writer runs off-thread. Poll the owned file across ticks instead of
        // waiting for Minecraft's global IO worker on the server thread.
        helper.onEachTick {
            if (!Files.exists(file)) return@onEachTick
            val persisted =
                net.minecraft.nbt.NbtIo
                    .readCompressed(
                        file,
                        net.minecraft.nbt.NbtAccounter
                            .create(1000000),
                    ).getCompound("data")
            if (coldKey.location().toString() !in OrbitalSectors.Catalogue.load(persisted).keys) return@onEachTick
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sectors_player", timeoutTicks = 400)
    fun nativeSectorPlayer(helper: GameTestHelper) {
        val server = helper.level.server
        val sourceKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(32, 6, -1)))
        val source = checkNotNull(OrbitalSectors.ensure(server, sourceKey))
        val player = GameTestPlayers.create(helper)
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
        val position = Vector3(OrbitalSector.WIDTH * 0.5 + 33000.0, OrbitalSector.WIDTH, 0.0)
        check(player.teleportTo(source, position.x, position.y, position.z, emptySet(), 31f, -12f))
        GameTestPlayers.acknowledgeTeleport(player)
        player.deltaMovement =
            net.minecraft.world.phys
                .Vec3(1.25, -0.5, 0.125)
        helper.onEachTick {
            val sample = CelestialFlight.sample(server)
            val current =
                CartesianState(
                    Vector3(player.x, player.y, player.z),
                    Vector3(player.deltaMovement.x, player.deltaMovement.y, player.deltaMovement.z) * 20.0,
                )
            val targetKey = sample.destination(source, current)
            helper.assertTrue(targetKey != null, "Player did not select next sector")
            val expected = sample.state(current, sourceKey, checkNotNull(targetKey))
            if (org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
                    .transfer(player)
            ) {
                helper.assertTrue(player.level().dimension() == targetKey, "Player went to incorrect sector")
                helper.assertTrue(
                    (Vector3(player.x, player.y, player.z) - expected.position).length() < 1e-6,
                    "Player position changed at sector handoff",
                )
                helper.assertTrue(
                    (Vector3(player.deltaMovement.x, player.deltaMovement.y, player.deltaMovement.z) * 20.0 - expected.velocity).length() <
                        1e-6,
                    "Player velocity changed at sector handoff",
                )
                GameTestPlayers.acknowledgeTeleport(player)
                helper.assertTrue(
                    !org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
                        .transfer(player),
                    "Player bounced across sector seam",
                )
                org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
                    .close(player)
                player.discard()
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sectors_ship", timeoutTicks = 1200)
    fun nativeSectorShip(helper: GameTestHelper) = sectorShip(helper, false)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sectors_mounted", timeoutTicks = 1200)
    fun nativeSectorMounted(helper: GameTestHelper) = sectorShip(helper, true)

    private fun sectorShip(
        helper: GameTestHelper,
        mounted: Boolean,
    ) {
        val server = helper.level.server
        val sourceKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(if (mounted) 44 else 40, 5, 2)))
        val targetKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(if (mounted) 45 else 41, 5, 2)))
        val source = checkNotNull(OrbitalSectors.ensure(server, sourceKey))
        val target = checkNotNull(OrbitalSectors.ensure(server, targetKey))
        val container = checkNotNull(SubLevelContainer.getContainer(source))
        val body =
            container.allocateNewSubLevel(
                Pose3d().also {
                    it.position().set(OrbitalSector.WIDTH * 0.5 + 33000.0, OrbitalSector.WIDTH, 0.0)
                },
            ) as ServerSubLevel
        val id = body.uniqueId
        val owner = Any()
        SpaceTravelReservations.reserve(id, owner)
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState(), 3)
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
        body.updateLastPose()
        val folder = Files.createTempDirectory("orbitalis-sector-transfer-")
        val player =
            if (mounted) {
                GameTestPlayers
                    .create(
                        helper,
                    ).also { it.setGameMode(net.minecraft.world.level.GameType.SURVIVAL) }
            } else {
                null
            }
        var cart: net.minecraft.world.entity.vehicle.Minecart? = null
        val sync =
            object : org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync {
                override fun hasShip(
                    player: net.minecraft.server.level.ServerPlayer,
                    body: ServerSubLevel,
                ) = true

                override fun isReady(
                    player: net.minecraft.server.level.ServerPlayer,
                    body: ServerSubLevel,
                ): Boolean {
                    runCatching { GameTestPlayers.acknowledgeTeleport(player) }
                    return true
                }
            }
        val transfer = SableShipTransfer(server, folder, sync)
        var started = false
        var elapsed = 0
        var expected: CartesianState? = null
        helper.onEachTick {
            source.resetEmptyTime()
            target.resetEmptyTime()
            if (player != null) runCatching { GameTestPlayers.acknowledgeTeleport(player) }
            Thread.sleep(5)
            repeat(8) {
                source.chunkSource.pollTask()
                target.chunkSource.pollTask()
            }
            transfer.tick()
            helper.assertTrue(transfer.error == null, "Sector transfer failed: ${transfer.error}")
            if (!started && !transfer.busy && ++elapsed > 5) {
                if (player != null && cart == null) {
                    val plot = body.plot.centerBlock
                    cart =
                        net.minecraft.world.entity.vehicle.Minecart(source, plot.x + 0.5, plot.y + 1.1, plot.z + 0.5).also { c ->
                            c.setNoGravity(true)
                            check(source.addFreshEntity(c))
                            val p = SableFlightGravity.reading(body).position
                            check(player.teleportTo(source, p.x, p.y, p.z, emptySet(), 31f, -12f))
                            check(player.startRiding(c, true))
                        }
                    return@onEachTick
                }
                val physics = SubLevelPhysicsSystem.require(source)
                physics.getPhysicsHandle(body).addLinearAndAngularVelocity(Vector3d(72.0, -13.0, 8.0), Vector3d(0.0, 0.0, 0.1))
                val reading = SableFlightGravity.reading(body)
                expected =
                    CelestialFlight
                        .sample(
                            server,
                        ).state(
                            CartesianState(reading.position, Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z)),
                            sourceKey,
                            targetKey,
                        )
                val unsafe = runCatching { transfer.begin(player, body, org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE) }
                helper.assertTrue(
                    unsafe.exceptionOrNull()?.message == "Destination exceeds safe native chart coordinates",
                    "Unsafe astronomical native destination was accepted",
                )
                helper.assertTrue(container.getSubLevel(id) === body && !body.isRemoved, "Rejected chart transfer changed source authority")
                transfer.begin(player, body, targetKey)
                started = true
            } else if (started && !transfer.busy) {
                val moved = checkNotNull(SubLevelContainer.getContainer(target)?.getSubLevel(id) as? ServerSubLevel)
                helper.assertTrue(container.getSubLevel(id) == null, "Source ship was duplicated")
                val reading = SableFlightGravity.reading(moved)
                helper.assertTrue(
                    (Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z) - checkNotNull(expected).velocity).length() < 0.1,
                    "Native sector transfer lost velocity",
                )
                helper.assertTrue(reading.position.length() < 2e6, "Native body reconstructed outside safe coordinates")
                helper.assertTrue(
                    moved.plot.loadedChunks.any { chunk ->
                        chunk.chunk.sections.any { section -> section.maybeHas { state -> state.`is`(Blocks.GOLD_BLOCK) } }
                    },
                    "Cargo block lost",
                )
                if (player != null) {
                    helper.assertTrue(
                        player.level() === target && player.vehicle?.uuid == cart?.uuid && !player.isCreative,
                        "Mounted survival player lost seat at sector handoff",
                    )
                    helper.assertTrue(source.getEntity(checkNotNull(cart).uuid) == null, "Source minecart was duplicated")
                    player.stopRiding()
                    target.getEntity(checkNotNull(cart).uuid)?.discard()
                    player.discard()
                }
                SpaceTravelReservations.release(id, owner)
                SubLevelContainer.getContainer(target)!!.removeSubLevel(moved, SubLevelRemovalReason.REMOVED)
                transfer.close()
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sectors_recovery", timeoutTicks = 1200)
    fun nativeSectorJournalRecovery(helper: GameTestHelper) {
        val server = helper.level.server
        val sourceKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(50, 5, 2)))
        val targetKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(51, 5, 2)))
        val source = checkNotNull(OrbitalSectors.ensure(server, sourceKey))
        val target = checkNotNull(OrbitalSectors.ensure(server, targetKey))
        val container = checkNotNull(SubLevelContainer.getContainer(source))
        val targetContainer = checkNotNull(SubLevelContainer.getContainer(target))
        val body = container.allocateNewSubLevel(Pose3d().also { it.position().set(500000.0, OrbitalSector.WIDTH, 0.0) }) as ServerSubLevel
        val id = body.uniqueId
        val owner = Any()
        SpaceTravelReservations.reserve(id, owner)
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState(), 3)
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
        body.updateLastPose()
        val folder = Files.createTempDirectory("orbitalis-sector-recovery-")
        val writer =
            org.cbi.orbitalis.minecraft.integration.sable
                .ShipTransferJournal(folder)
        var transaction: org.cbi.orbitalis.minecraft.integration.sable.ShipTransferRecord? = null
        var pending: java.util.concurrent.CompletableFuture<kotlin.Unit>? = null
        var manager: SableShipTransfer? = null
        var stage = 0
        var elapsed = 0
        helper.onEachTick {
            source.resetEmptyTime()
            target.resetEmptyTime()
            Thread.sleep(5)
            val current = manager
            if (current != null) {
                current.tick()
                helper.assertTrue(current.error == null, "Sector recovery failed: ${current.error}")
            }
            if (stage == 0 && ++elapsed > 5) {
                val sample = CelestialFlight.sample(server)
                val snapshot =
                    org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
                        .capture(body)
                val transformed =
                    org.cbi.orbitalis.minecraft.integration.sable
                        .ShipFrameTransform(sample, sourceKey, targetKey)
                        .ship(snapshot, SableFlightGravity.reading(body).position)
                val destination =
                    org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot.forDestination(
                        transformed,
                        source,
                        target,
                    )
                transaction =
                    org.cbi.orbitalis.minecraft.integration.sable.ShipTransferRecord(
                        java.util.UUID.randomUUID(),
                        id,
                        null,
                        false,
                        snapshot,
                        destination,
                        net.minecraft.nbt.CompoundTag(),
                        net.minecraft.nbt.CompoundTag(),
                        net.minecraft.nbt.CompoundTag(),
                        org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPhase.PREPARED,
                        frozenFrame = sample,
                        explicitSource = sourceKey,
                        explicitDestination = targetKey,
                    )
                val encoded = checkNotNull(transaction).encode()
                helper.assertTrue(
                    encoded.getInt("version") == 8 && !encoded.contains("source_is_orbit"),
                    "Sector journal inferred route from legacy boolean",
                )
                val decoded =
                    org.cbi.orbitalis.minecraft.integration.sable.ShipTransferRecord
                        .decode(encoded)
                helper.assertTrue(
                    decoded.sourceDimension == sourceKey && decoded.destinationDimension == targetKey,
                    "Explicit sector route did not roundtrip",
                )
                helper.assertTrue(
                    org.cbi.orbitalis.minecraft.integration.sable.ShipTransferRecord
                        .decode(decoded.encode())
                        .encode() == encoded,
                    "Explicit sector journal canonical encoding changed",
                )
                pending = writer.write(checkNotNull(transaction))
                stage = 1
            } else if ((stage == 1 || stage == 3) && pending?.isDone == true) {
                pending!!.join()
                manager = SableShipTransfer(server, folder)
                stage++
            } else if (stage == 2 && current != null && !current.busy) {
                helper.assertTrue(
                    container.getSubLevel(id) != null && targetContainer.getSubLevel(id) == null,
                    "PREPARED recovery did not retain source authority",
                )
                current.close()
                manager = null
                pending =
                    writer.write(
                        checkNotNull(transaction).copy(phase = org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPhase.COMMITTED),
                    )
                stage = 3
            } else if (stage == 4 && current != null && !current.busy) {
                helper.assertTrue(
                    container.getSubLevel(id) == null && targetContainer.getSubLevel(id) != null,
                    "COMMITTED recovery did not retain destination authority",
                )
                val winner = targetContainer.getSubLevel(id) as ServerSubLevel
                helper.assertTrue(
                    winner.plot.loadedChunks.any {
                        it.chunk.sections.any { section ->
                            section.maybeHas { state -> state.`is`(Blocks.GOLD_BLOCK) }
                        }
                    },
                    "Recovery lost native cargo",
                )
                current.close()
                writer.close()
                SpaceTravelReservations.release(id, owner)
                targetContainer.removeSubLevel(winner, SubLevelRemovalReason.REMOVED)
                helper.succeed()
            }
        }
    }
}
