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
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.OrbitalTestFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.cbi.orbitalis.minecraft.integration.sable.FlightTestTravel
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Vector3d
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object SableFlightGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "flight_gravity", timeoutTicks = 150)
    fun ordinaryShipsReceiveCubicGravity(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(SableFlightGravity.DIMENSION))
        helper.assertTrue(!level.hasChunk(0, 0), "Entry regression requires an initially unloaded destination chunk")
        val player = GameTestPlayers.create(helper)
        val originalPosition = player.position()
        val originalDimension = player.level().dimension()
        val travel = FlightTestTravel(helper.level.server)
        try {
            helper.assertTrue(travel.enter(player), "Flight entry failed")
            helper.assertTrue(player.y > level.seaLevel, "Flight entry placed the player underground")
            helper.assertTrue(level.canSeeSky(player.blockPosition()), "Flight entry is below the surface")
            helper.assertTrue(travel.leave(player), "Flight return failed")
            helper.assertTrue(
                player.level().dimension() == originalDimension && player.position().distanceTo(originalPosition) < 1e-8,
                "Flight return lost the entry position",
            )
        } finally {
            player.discard()
        }
        val physics = SubLevelPhysicsSystem.require(level)
        val config = physics.config
        val originalSubsteps = config.substepsPerTick
        val bodies = mutableListOf<ServerSubLevel>()

        fun removeBodies() {
            bodies.forEach { body ->
                val container = SubLevelContainer.getContainer(body.level)!!
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
            bodies.clear()
        }
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testPassed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    removeBodies()
                    config.substepsPerTick = originalSubsteps
                }

                override fun testFailed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    removeBodies()
                    config.substepsPerTick = originalSubsteps
                }

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}
            },
        )

        fun ship(
            world: ServerLevel,
            x: Double,
            y: Double,
            heavy: Boolean = false,
        ): ServerSubLevel {
            val container = SubLevelContainer.getContainer(world)!!
            val pose = Pose3d()
            pose.position().set(x, y, 0.0)
            if (heavy) pose.orientation().rotateZ(0.8).rotateX(0.3)
            val body = container.allocateNewSubLevel(pose) as ServerSubLevel
            bodies.add(body)
            body.plot.newEmptyChunk(body.plot.centerChunk)
            val material = if (heavy) Blocks.IRON_BLOCK else Blocks.GOLD_BLOCK
            check(body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, material.defaultBlockState(), 3))
            if (heavy) check(body.plot.embeddedLevelAccessor.setBlock(BlockPos(0, 1, 0), material.defaultBlockState(), 3))
            body.updateLastPose()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            // No Orbitalis owner tag, registration or opt-in: these are ordinary native constructions.
            return body
        }

        helper.assertTrue(DimensionPhysicsData.getGravity(level).length() == 0.0, "Native gravity would be counted twice")
        helper.assertTrue(DimensionPhysicsData.getUniversalDrag(level) == 0.0, "Unexpected flight-space drag")
        helper.assertTrue(
            DimensionPhysicsData.getAirPressure(level, Vector3d(0.0, 200.0, 0.0)) == 0.0,
            "Unexpected flight-space atmosphere",
        )
        helper.assertTrue(abs(DimensionPhysicsData.getGravity(helper.level).y + 11.0) < 1e-6, "Overworld gravity was changed")
        val results = mutableListOf<Double>()

        fun run(substeps: Int) {
            config.substepsPerTick = substeps
            val light = ship(level, 0.0, 200.0)
            val heavy = ship(level, 1600.0, 200.0, true)
            val high = ship(level, 0.0, 6064.0)
            val outside = ship(helper.level, 0.0, 1000.0)
            helper.runAfterDelay(5) {
                val before = listOf(light, heavy, high).map(SableFlightGravity::reading)
                helper.assertTrue(
                    abs(before[1].acceleration.x) < 1e-12 && abs(before[1].acceleration.z) < 1e-12,
                    "Off-centre face gravity has a lateral component",
                )
                val relative = Vector3(1600.0, 600.0, 1000.0)
                val orbit = checkNotNull(level.server.getLevel(SableFlightGravity.ORBIT))
                val surfaceField = SableFlightGravity.field.acceleration(relative - SableFlightGravity.CENTRE)
                val orbitField =
                    SableFlightGravity.field.acceleration(
                        SableFlightGravity.globalPosition(orbit, OrbitalTestFrame.toOrbit(relative)) -
                            SableFlightGravity.CENTRE,
                    )
                helper.assertTrue((surfaceField - orbitField).length() < 1e-12, "Frame handoff changes gravity")
                val outsideHandle = SubLevelPhysicsSystem.require(helper.level).getPhysicsHandle(outside)
                val outsideBefore = outsideHandle.getLinearVelocity(Vector3d())
                // Represents another force producer, such as Propulsion: gravity must not erase it.
                physics.getPhysicsHandle(light).applyLinearImpulse(Vector3d(before[0].mass * 3.0, 0.0, 0.0))
                helper.runAfterDelay(20) {
                    val after = listOf(light, heavy, high).map(SableFlightGravity::reading)
                    val lowDelta = after[0].velocity.y - before[0].velocity.y
                    val heavyDelta = after[1].velocity.y - before[1].velocity.y
                    val highDelta = after[2].velocity.y - before[2].velocity.y
                    helper.assertTrue(
                        abs(lowDelta - before[0].acceleration.y) < 0.04,
                        "Cubic acceleration or timestep is wrong: $lowDelta",
                    )
                    helper.assertTrue(abs(lowDelta - heavyDelta) < 0.015, "Mass or body rotation changed gravitational acceleration")
                    helper.assertTrue(
                        abs(highDelta - before[2].acceleration.y) < 0.015 && abs(highDelta) < abs(lowDelta) * 0.3,
                        "Gravity did not weaken with altitude",
                    )
                    helper.assertTrue(abs(after[0].velocity.x - before[0].velocity.x - 3.0) < 0.01, "Another producer's impulse was erased")
                    helper.assertTrue(
                        abs(after[1].velocity.x - before[1].velocity.x) < 1e-6 &&
                            abs(after[1].velocity.z - before[1].velocity.z) < 1e-6,
                        "Off-centre ship acquired lateral velocity under face gravity",
                    )
                    helper.assertTrue(
                        physics.getPhysicsHandle(heavy).getAngularVelocity(Vector3d()).length() < 1e-5,
                        "Gravity introduced torque",
                    )
                    val outsideDelta = outsideHandle.getLinearVelocity(Vector3d()).y - outsideBefore.y
                    helper.assertTrue(
                        outsideDelta < -8.0 && outsideDelta > -12.0,
                        "Orbitalis affected ordinary Overworld ships: $outsideDelta",
                    )
                    helper.assertTrue(bodies.all { it.userDataTag?.isEmpty != false }, "Gravity required ship ownership metadata")
                    println(
                        "ORBITALIS_FLIGHT_GRAVITY substeps=$substeps lowDelta=$lowDelta heavyDelta=$heavyDelta highDelta=$highDelta outsideDelta=$outsideDelta",
                    )
                    results.add(lowDelta)
                    removeBodies()
                    if (substeps == 1) {
                        helper.runAfterDelay(1) { run(4) }
                    } else {
                        helper.assertTrue(abs(results[0] - results[1]) < 0.015, "Gravity changed with native substeps")
                        helper.succeed()
                    }
                }
            }
        }
        run(1)
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "space_orbit", timeoutTicks = 18000)
    fun fullSpaceOrbitPreservesNativeEnergy(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(FlightTestSpace.SPACE))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val physics = SubLevelPhysicsSystem.require(level)
        val centre = FlightTestSpace.localPosition(FlightTestSpace.SPACE, FlightTestSpace.CENTRE)
        val radius = 18000.0
        val mu = FlightTestSpace.gravity.surfaceAcceleration * FlightTestSpace.gravity.halfExtent * FlightTestSpace.gravity.halfExtent
        val speed = kotlin.math.sqrt(mu / radius)
        val pose = Pose3d()
        pose.position().set(0.0, centre.y + radius, 0.0)
        val body = container.allocateNewSubLevel(pose) as ServerSubLevel
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

                fun cleanup() {
                    if (!body.isRemoved) {
                        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                    }
                }
            },
        )
        body.plot.newEmptyChunk(body.plot.centerChunk)
        check(body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3))
        body.updateLastPose()
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        helper.runAfterDelay(5) {
            val handle = physics.getPhysicsHandle(body)
            val before = SableFlightGravity.reading(body)
            val desired = Vector3(0.0, centre.y + radius, 0.0)
            val delta = desired - before.position
            handle.teleport(Vector3d(body.logicalPose().position()).add(delta.x, delta.y, delta.z), body.logicalPose().orientation())
            handle.addLinearAndAngularVelocity(Vector3d(speed, 0.0, 0.0).sub(before.velocity), Vector3d())
            val referenceEnergy = -mu / (2 * radius)
            var maximumRadiusError = 0.0
            var maximumEnergyError = 0.0
            var previousAngle = 0.0
            var sweptAngle = 0.0
            var ticks = 0

            fun measure() {
                level.resetEmptyTime()
                val reading = SableFlightGravity.reading(body)
                val relative = reading.position - centre
                val angle = kotlin.math.atan2(relative.x, relative.y)
                var advance = angle - previousAngle
                if (advance < -kotlin.math.PI) advance += 2 * kotlin.math.PI
                if (advance > kotlin.math.PI) advance -= 2 * kotlin.math.PI
                sweptAngle += advance
                previousAngle = angle
                val energy = reading.velocity.lengthSquared() / 2 + FlightTestSpace.gravity.sample(relative).potential
                maximumRadiusError = maxOf(maximumRadiusError, abs(relative.length() - radius))
                maximumEnergyError = maxOf(maximumEnergyError, abs((energy - referenceEnergy) / referenceEnergy))
                helper.assertTrue(reading.position.y >= 4000, "Full orbit crossed the safe Minecraft height")
                helper.assertTrue(maximumRadiusError < 30.0, "Native orbit radius drift: $maximumRadiusError")
                helper.assertTrue(maximumEnergyError < 0.003, "Native orbit energy drift: $maximumEnergyError")
                if (sweptAngle >= 2 * kotlin.math.PI) {
                    helper.assertTrue(abs(relative.x) < 15 && abs(relative.y - radius) < 30, "Full revolution did not close")
                    helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.001, "Gravity spun the free body")
                    println(
                        "ORBITALIS_FULL_SPACE_ORBIT ticks=$ticks angle=$sweptAngle radiusError=$maximumRadiusError energyError=$maximumEnergyError",
                    )
                    helper.succeed()
                } else {
                    ticks++
                    helper.runAfterDelay(1) { measure() }
                }
            }
            helper.runAfterDelay(1) { measure() }
        }
    }
}
