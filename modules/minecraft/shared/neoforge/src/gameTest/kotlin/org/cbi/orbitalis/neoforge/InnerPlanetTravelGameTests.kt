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
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.orbit.FlightFrame
import org.cbi.orbitalis.core.orbit.OrbitalSector
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.OrbitalSectors
import org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.cbi.orbitalis.minecraft.innerplanets.NativeInnerPlanets
import org.cbi.orbitalis.minecraft.integration.sable.AutomaticShipTravel
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipFrameTransform
import org.joml.Vector3d

/** Actual native-sector ingress, native colliders and automatic planet egress; no command teleports. */
@PrefixGameTestTemplate(false)
object InnerPlanetTravelGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "mercury_ship", timeoutTicks = 2400)
    fun mercuryShipLandingRoundTrip(helper: GameTestHelper) = ship(helper, NativeInnerPlanets.MERCURY)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "venus_ship", timeoutTicks = 2400)
    fun venusShipLandingRoundTrip(helper: GameTestHelper) = ship(helper, NativeInnerPlanets.VENUS)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "mercury_player", timeoutTicks = 1200)
    fun mercuryIndependentPlayerRoundTrip(helper: GameTestHelper) = player(helper, NativeInnerPlanets.MERCURY)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "venus_player", timeoutTicks = 1200)
    fun venusIndependentPlayerRoundTrip(helper: GameTestHelper) = player(helper, NativeInnerPlanets.VENUS)

    private fun sector(
        helper: GameTestHelper,
        planet: ResourceKey<Level>,
        local: Vector3,
    ): net.minecraft.server.level.ServerLevel {
        val sample = CelestialFlight.sample(helper.level.server)
        val heliocentric =
            sample.rootPoint(local, planet) +
                sample.frames.profile.earthOrbit
                    .sample(sample.frames.tick)
                    .origin.position
        val key = CelestialFlight.dimension(FlightFrame.Sector(OrbitalSector.containing(heliocentric)))
        return checkNotNull(OrbitalSectors.ensure(helper.level.server, key))
    }

    private fun cleanup(
        helper: GameTestHelper,
        action: () -> Unit,
    ) {
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testPassed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) = action()

                override fun testFailed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) = action()

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}
            },
        )
    }

    private fun player(
        helper: GameTestHelper,
        planet: ResourceKey<Level>,
    ) {
        val server = helper.level.server
        val surface = checkNotNull(server.getLevel(planet))
        val orbit = sector(helper, planet, Vector3(0.0, 1560.0, 0.0))
        val player = GameTestPlayers.create(helper)
        player.setGameMode(GameType.SPECTATOR)
        cleanup(helper) {
            PlayerSpaceTravel.close(player)
            player.discard()
        }
        var returning = false
        helper.onEachTick {
            Thread.sleep(15)
            repeat(16) { for (level in server.allLevels) level.chunkSource.pollTask() }
            val sample = CelestialFlight.sample(server)
            val definition =
                sample.frames.profile.innerPlanets
                    .single { it.id == planet.location().toString() }
            val local =
                CartesianState(
                    Vector3(
                        0.0,
                        if (returning) definition.envelope.ascentDistance + 74.0 else definition.envelope.descentDistance + 60.0,
                        0.0,
                    ),
                    Vector3(0.0, if (returning) 400.0 else -400.0, 0.0),
                )
            val from = if (returning) surface else orbit
            val state = if (returning) local else sample.state(local, planet, orbit.dimension())
            if (player.level() !==
                from
            ) {
                check(player.teleportTo(from, state.position.x, state.position.y, state.position.z, emptySet(), 31f, -12f))
            }
            player.setPos(state.position.x, state.position.y, state.position.z)
            player.deltaMovement =
                net.minecraft.world.phys
                    .Vec3(state.velocity.x / 20, state.velocity.y / 20, state.velocity.z / 20)
            runCatching { GameTestPlayers.acknowledgeTeleport(player) }
            val target = sample.destination(from, state)
            helper.assertTrue(
                target != null && if (returning) OrbitalSectors.sector(target) != null else target == planet,
                "Wrong planet/sector selected: planet=$planet returning=$returning target=$target",
            )
            val expected = sample.state(state, from.dimension(), checkNotNull(target))
            if (!PlayerSpaceTravel.transfer(player)) return@onEachTick
            helper.assertTrue(player.level().dimension() == target, "Independent player went to the wrong world")
            helper.assertTrue(
                (Vector3(player.x, player.y, player.z) - expected.position).length() < 1e-6,
                "Player position changed at planetary handoff",
            )
            helper.assertTrue(
                (
                    Vector3(
                        player.deltaMovement.x,
                        player.deltaMovement.y,
                        player.deltaMovement.z,
                    ) * 20.0 - expected.velocity
                ).length() < 1e-6,
                "Player velocity changed at planetary handoff",
            )
            GameTestPlayers.acknowledgeTeleport(player)
            helper.assertTrue(!PlayerSpaceTravel.transfer(player), "Independent player bounced after planetary handoff")
            println("INNER_PLANET_PLAYER planet=$planet returning=$returning target=$target")
            if (returning) helper.succeed() else returning = true
        }
    }

    private fun ship(
        helper: GameTestHelper,
        planet: ResourceKey<Level>,
    ) {
        val server = helper.level.server
        val surface = checkNotNull(server.getLevel(planet))
        val start = Vector3(42.0, 220.0, -60.0)
        val orbit = sector(helper, planet, start)
        val initial = CelestialFlight.sample(server)
        val position = initial.point(start, planet, orbit.dimension())
        val frame = initial.frames.profile.innerPlanetSurfaceFrame(planet.location().toString(), initial.frames.tick)
        val container = checkNotNull(SubLevelContainer.getContainer(orbit))
        val body =
            container.allocateNewSubLevel(
                Pose3d().also {
                    it.position().set(position.x, position.y, position.z)
                    it.orientation().rotateZ(kotlin.math.atan2(frame.orientation.xAxis.y, frame.orientation.xAxis.x))
                },
            ) as ServerSubLevel
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos(1, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos(0, 1, 0), Blocks.CHEST.defaultBlockState(), 3)
        val id = body.uniqueId
        val owner = Any()
        SpaceTravelReservations.reserve(id, owner)
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
        body.updateLastPose()

        fun cargo(b: ServerSubLevel) =
            b.plot.loadedChunks
                .flatMap { it.chunk.blockEntities.values }
                .filterIsInstance<ChestBlockEntity>()
                .single()
        cargo(body).setItem(0, ItemStack(Items.COBBLESTONE, 16))
        val transfer =
            SableShipTransfer(
                server,
                java.nio.file.Files
                    .createTempDirectory("orbitalis-inner-planet-travel-"),
            )
        val automatic = AutomaticShipTravel(server, transfer, owner)
        cleanup(helper) {
            automatic.close()
            transfer.close()
            SpaceTravelReservations.release(id, owner)
            CelestialFlight.levels(server).forEach { world ->
                SubLevelPhysicsSystem.require(world).paused = false
                val c = SubLevelContainer.getContainer(world)
                (c?.getSubLevel(id) as? ServerSubLevel)?.let { moved ->
                    c.removeForceLoadTicket(moved, SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE)
                    c.removeSubLevel(moved, SubLevelRemovalReason.REMOVED)
                }
            }
        }
        var phase = 0
        var expected: CompoundTag? = null
        var target: ResourceKey<Level>? = null
        var contactTicks = 0

        fun expectedAt(
            b: ServerSubLevel,
            to: ResourceKey<Level>,
        ): CompoundTag {
            val sample = CelestialFlight.sample(server)
            return ShipFrameTransform(
                sample,
                b.level.dimension(),
                to,
            ).ship(SableShipSnapshot.capture(b), SableFlightGravity.reading(b).position)
        }

        fun assertArrival(b: ServerSubLevel) {
            val saved = checkNotNull(expected)
            val pose = SableNBTUtils.readPose3d(saved.getCompound("pose"))
            helper.assertTrue(b.logicalPose().position().distance(pose.position()) < 1e-6, "Planet handoff displaced ship")
            helper.assertTrue(
                kotlin.math.abs(b.logicalPose().orientation().dot(pose.orientation())) > 0.999999,
                "Planet handoff changed attitude",
            )
            helper.assertTrue(
                SableShipSnapshot.matchesNativeVelocity(
                    SubLevelPhysicsSystem.require(b.level).getPhysicsHandle(b).getLinearVelocity(Vector3d()),
                    SableShipSnapshot.vector(saved, "exact_linear"),
                ),
                "Planet handoff changed exact velocity",
            )
            helper.assertTrue(cargo(b).getItem(0).count == 16, "Planet handoff lost cargo")
        }
        helper.onEachTick {
            Thread.sleep(10)
            repeat(16) { for (level in server.allLevels) level.chunkSource.pollTask() }
            CelestialFlight.levels(server).forEach { it.resetEmptyTime() }
            transfer.tick()
            helper.assertTrue(transfer.error == null, "Inner planet transfer failed: ${transfer.error}")
            when {
                phase == 0 && !transfer.busy && body.massTracker.centerOfMass != null -> {
                    val sample = CelestialFlight.sample(server)
                    val reading = SableFlightGravity.reading(body)
                    val local = sample.point(reading.position, orbit.dimension(), planet)
                    val desired = sample.state(CartesianState(local, Vector3(0.0, -12.0, 0.0)), planet, orbit.dimension())
                    val spin = sample.angular(Vector3.ZERO, planet, orbit.dimension())
                    SubLevelPhysicsSystem
                        .require(
                            orbit,
                        ).getPhysicsHandle(
                            body,
                        ).addLinearAndAngularVelocity(
                            Vector3d(desired.velocity.x, desired.velocity.y, desired.velocity.z).sub(reading.velocity),
                            Vector3d(spin.x, spin.y, spin.z),
                        )
                    phase = 1
                }

                phase == 1 -> {
                    val reading = SableFlightGravity.reading(body)
                    val destination =
                        CelestialFlight
                            .sample(
                                server,
                            ).destination(
                                orbit,
                                CartesianState(reading.position, Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z)),
                            )
                    helper.assertTrue(destination == planet, "Ship selected Earth or another body instead of $planet: $destination")
                    expected = expectedAt(body, planet)
                    automatic.tick()
                    if (transfer.busy) phase = 2
                }

                phase == 2 && !transfer.busy -> {
                    val arrived = checkNotNull(SubLevelContainer.getContainer(surface)?.getSubLevel(id) as? ServerSubLevel)
                    assertArrival(arrived)
                    SubLevelContainer.getContainer(surface)!!.addForceLoadTicket(
                        arrived,
                        SubLevelLoadingTicketType.COMMAND_FORCED,
                        net.minecraft.util.Unit.INSTANCE,
                    )
                    helper.assertTrue(container.getSubLevel(id) == null, "Planet ingress duplicated source ship")
                    phase = 3
                }

                phase == 3 -> {
                    val arrived = checkNotNull(SubLevelContainer.getContainer(surface)?.getSubLevel(id) as? ServerSubLevel)
                    val reading = SableFlightGravity.reading(arrived)
                    val ground = surface.getHeight(Heightmap.Types.WORLD_SURFACE, reading.position.x.toInt(), reading.position.z.toInt())
                    helper.assertTrue(reading.position.y >= ground - 4.0, "Ship passed through native planetary terrain")
                    helper.assertTrue(reading.acceleration.y < 0.0, "Native planetary gravity does not point down")
                    if (arrived.boundingBox().minY() in (ground - 2.0)..(ground + 0.5) && kotlin.math.abs(reading.velocity.y) < 0.5) {
                        if (++contactTicks >= 5) {
                            println("INNER_PLANET_CONTACT planet=$planet ground=$ground position=${reading.position}")
                            SubLevelPhysicsSystem
                                .require(
                                    surface,
                                ).getPhysicsHandle(
                                    arrived,
                                ).addLinearAndAngularVelocity(Vector3d(0.0, 300.0, 0.0).sub(reading.velocity), Vector3d())
                            phase = 4
                        }
                    } else {
                        contactTicks = 0
                    }
                }

                phase == 4 -> {
                    val arrived = checkNotNull(SubLevelContainer.getContainer(surface)?.getSubLevel(id) as? ServerSubLevel)
                    val reading = SableFlightGravity.reading(arrived)
                    val destination =
                        CelestialFlight
                            .sample(
                                server,
                            ).destination(
                                surface,
                                CartesianState(reading.position, Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z)),
                            )
                    if (destination != null) {
                        helper.assertTrue(
                            OrbitalSectors.sector(destination) != null,
                            "Planet ascent did not select safe heliocentric sector",
                        )
                        target = destination
                        expected = expectedAt(arrived, destination)
                        automatic.tick()
                        if (transfer.busy) phase = 5
                    }
                }

                phase == 5 && !transfer.busy -> {
                    val destination = checkNotNull(server.getLevel(checkNotNull(target)))
                    val returned = checkNotNull(SubLevelContainer.getContainer(destination)?.getSubLevel(id) as? ServerSubLevel)
                    assertArrival(returned)
                    helper.assertTrue(
                        SubLevelContainer.getContainer(surface)?.getSubLevel(id) == null,
                        "Planet egress duplicated surface ship",
                    )
                    println("INNER_PLANET_SHIP_ROUNDTRIP planet=$planet target=$target cargo=16 contactTicks=$contactTicks")
                    helper.succeed()
                }
            }
        }
    }
}
