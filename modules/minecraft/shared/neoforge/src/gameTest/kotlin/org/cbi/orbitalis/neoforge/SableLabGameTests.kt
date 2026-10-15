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

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.integration.sable.SableOrbitExperiment
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.neoforge.integration.SableLabIntegration
import org.joml.Vector3d
import kotlin.math.abs

/** Registered only when Sable is present. These scenarios require its real native physics backend. */
@PrefixGameTestTemplate(false)
object SableLabGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sable_impulse", timeoutTicks = 160)
    fun sableImpulseAndCoasting(helper: GameTestHelper) {
        val scene = SableLabIntegration.scene(helper.level.server)
        val level = scene.level
        helper.assertTrue(DimensionPhysicsData.getGravity(level).length() == 0.0, "Lab gravity was not zero")
        helper.assertTrue(DimensionPhysicsData.getUniversalDrag(level) == 0.0, "Lab drag was not zero")
        helper.assertTrue(DimensionPhysicsData.getAirPressure(level, Vector3d(0.0, 80.0, 120.0)) == 0.0, "Lab pressure was not zero")
        val config = SubLevelPhysicsSystem.require(level).config
        val originalSubsteps = config.substepsPerTick
        onFinished(helper) {
            config.substepsPerTick = originalSubsteps
            scene.clear()
        }
        config.substepsPerTick = 1
        scene.start()
        helper.runAfterDelay(25) {
            val first = scene.reading(0)
            helper.assertTrue(scene.callbacks > 0 && scene.physicsSeconds >= 0.5, "Physics callbacks did not run")
            helper.assertTrue(first.mass > 0.0 && first.mass.isFinite(), "Mass is invalid")
            helper.assertTrue(abs(first.velocity.x - 1.0) < 0.002, "Force pulse did not produce delta-v = 1 m/s: $first")
            helper.assertTrue(abs(first.velocity.y) < 0.002 && abs(first.velocity.z) < 0.002, "Vacuum probe acquired transverse velocity")
            helper.assertTrue(
                scene.reading(1).velocity.distance(first.velocity) < 0.002,
                "Rotation changed the direction of a world-space impulse",
            )
            val coastingVelocity = Vector3d(first.velocity)
            helper.runAfterDelay(10) {
                helper.assertTrue(scene.reading(0).velocity.distance(coastingVelocity) < 0.002, "Probe lost velocity while coasting")
                config.substepsPerTick = 4
                scene.start()
                helper.runAfterDelay(25) {
                    try {
                        val second = scene.reading(0)
                        helper.assertTrue(abs(second.velocity.x - first.velocity.x) < 0.002, "Force changed with physics timestep")
                        helper.assertTrue(
                            scene.callbacks >= 40 && scene.lastStepSeconds <= 0.012501,
                            "Four-substep configuration was not exercised",
                        )
                        println(
                            "ORBITALIS_SABLE_IMPULSE mass=${second.mass} v1=${first.velocity.x} v4=${second.velocity.x} dt=${scene.lastStepSeconds} callbacks=${scene.callbacks}",
                        )
                        helper.succeed()
                    } finally {
                        config.substepsPerTick = originalSubsteps
                        scene.clear()
                    }
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sable_frame", timeoutTicks = 100)
    fun sableFrameShiftPreservesState(helper: GameTestHelper) {
        val scene = SableLabIntegration.scene(helper.level.server)
        scene.start()
        val player = GameTestPlayers.create(helper)
        onFinished(helper) {
            scene.clear()
            player.discard()
        }
        val level = helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)!!
        player.teleportTo(level, 2.0, 80.0, 120.0, emptySet(), 15f, -20f)
        helper.runAfterDelay(25) {
            val before = List(2) { scene.reading(it) }
            helper.assertTrue(before[1].angularVelocity.length() > 0.1, "Frame test requires nonzero angular velocity")
            val separation = Vector3d(before[1].position).sub(before[0].position)
            val playerPosition = player.position()
            val playerVelocity = Vec3(0.1, 0.0, 0.0)
            player.deltaMovement = playerVelocity
            scene.requestShift(Vec3(32.0, 0.0, 0.0), player)
            helper.runAfterDelay(1) {
                try {
                    helper.assertTrue(
                        scene.completedShifts == 1 && scene.frameOrigin.x == 32.0,
                        "Frame shift was not applied on physics tick",
                    )
                    val after = List(2) { scene.reading(it) }
                    helper.assertTrue(
                        Vector3d(after[1].position).sub(after[0].position).distance(separation) < 0.01,
                        "Relative body positions changed",
                    )
                    for (index in 0..1) {
                        helper.assertTrue(
                            after[index].velocity.distance(before[index].velocity) < 0.002,
                            "Frame shift changed linear velocity",
                        )
                        helper.assertTrue(
                            after[index].angularVelocity.distance(before[index].angularVelocity) < 0.002,
                            "Frame shift changed angular velocity",
                        )
                        // Allow only the normal rotation during the intervening game tick.
                        helper.assertTrue(
                            abs(after[index].orientation.dot(before[index].orientation)) > 0.9999,
                            "Frame shift changed body orientation beyond its normal angular motion",
                        )
                        val global = Vector3d(after[index].position).add(scene.frameOrigin)
                        helper.assertTrue(abs(global.x - before[index].position.x) < 0.2, "Frame shift discontinuity in global position")
                    }
                    helper.assertTrue(abs(player.x - (playerPosition.x - 32.0)) < 0.2, "Observer did not move with the frame")
                    helper.assertTrue(player.yRot == 15f && player.xRot == -20f, "Observer orientation changed")
                    println(
                        "ORBITALIS_SABLE_FRAME origin=${scene.frameOrigin} relativeError=${Vector3d(
                            after[1].position,
                        ).sub(after[0].position).distance(separation)}",
                    )
                    helper.succeed()
                } finally {
                    scene.clear()
                    player.discard()
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sable_orbit", timeoutTicks = 650)
    fun sableCentralGravityOrbit(helper: GameTestHelper) {
        val scene = SableLabIntegration.scene(helper.level.server)
        val config = SubLevelPhysicsSystem.require(scene.level).config
        val originalSubsteps = config.substepsPerTick
        onFinished(helper) {
            config.substepsPerTick = originalSubsteps
            scene.clear()
        }

        fun runOrbit(
            substeps: Int,
            completed: (SableOrbitExperiment.Metrics) -> Unit,
        ) {
            config.substepsPerTick = substeps
            scene.startOrbit()
            var shifted = false

            fun observe() {
                helper.assertTrue(scene.failureReason == null, scene.failureReason ?: "Orbit stopped")
                val metrics = scene.orbitMetrics!!
                if (!shifted && metrics.seconds >= SableOrbitExperiment.PERIOD / 2.0) {
                    scene.requestShift(Vec3(32.0, 0.0, 0.0))
                    shifted = true
                }
                if (metrics.seconds < SableOrbitExperiment.PERIOD) {
                    helper.runAfterDelay(1) { observe() }
                    return
                }
                helper.assertTrue(scene.completedShifts == 1, "Orbit did not exercise a frame shift")
                helper.assertTrue(scene.reading(1).mass > scene.reading(0).mass * 1.5, "Orbit requires different construction masses")
                helper.assertTrue(metrics.maximumPositionError < 1.0, "Orbit position diverged: $metrics")
                helper.assertTrue(metrics.maximumVelocityError < 0.5, "Orbit velocity diverged: $metrics")
                helper.assertTrue(metrics.maximumRadiusError < 0.6, "Circular orbit radial drift: $metrics")
                helper.assertTrue(metrics.maximumRelativeEnergyError < 0.01, "Orbit energy drift: $metrics")
                helper.assertTrue(metrics.maximumRelativeMomentumError < 0.005, "Central force introduced torque: $metrics")
                val first = scene.orbitalState(0)
                val second = scene.orbitalState(1)
                helper.assertTrue((first.position + second.position).length() < 0.02, "Different masses fell differently")
                helper.assertTrue((first.velocity + second.velocity).length() < 0.02, "Different masses acquired different accelerations")
                helper.assertTrue(first.position.x > 15.0 && abs(first.position.z) < 1.0, "Construction did not complete a revolution")
                println("ORBITALIS_SABLE_ORBIT substeps=$substeps mass0=${scene.reading(0).mass} mass1=${scene.reading(1).mass} $metrics")
                completed(metrics)
            }
            helper.runAfterDelay(1) { observe() }
        }
        runOrbit(1) { coarse ->
            runOrbit(4) { fine ->
                helper.assertTrue(
                    fine.maximumPositionError < coarse.maximumPositionError * 0.6,
                    "Smaller steps did not improve orbital accuracy",
                )
                helper.succeed()
            }
        }
    }

    private fun onFinished(
        helper: GameTestHelper,
        cleanup: () -> Unit,
    ) {
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
    }
}
