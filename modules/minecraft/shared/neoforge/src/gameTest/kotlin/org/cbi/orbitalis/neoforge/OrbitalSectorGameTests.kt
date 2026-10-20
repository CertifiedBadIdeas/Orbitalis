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
        tag.putInt("Version", 999)
        helper.assertTrue(!OrbitalSectors.Catalogue.load(tag).compatible, "Unknown catalogue accepted")
        helper.succeed()
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
    fun nativeSectorShip(helper: GameTestHelper) {
        val server = helper.level.server
        val sourceKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(40, 5, 2)))
        val targetKey = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector(41, 5, 2)))
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
        val transfer = SableShipTransfer(server, folder)
        var started = false
        var elapsed = 0
        var expected: CartesianState? = null
        helper.onEachTick {
            source.resetEmptyTime()
            target.resetEmptyTime()
            transfer.tick()
            helper.assertTrue(transfer.error == null, "Sector transfer failed: ${transfer.error}")
            if (!started && !transfer.busy && ++elapsed > 5) {
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
                transfer.begin(null, body, targetKey)
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
                SpaceTravelReservations.release(id, owner)
                SubLevelContainer.getContainer(target)!!.removeSubLevel(moved, SubLevelRemovalReason.REMOVED)
                transfer.close()
                helper.succeed()
            }
        }
    }
}
