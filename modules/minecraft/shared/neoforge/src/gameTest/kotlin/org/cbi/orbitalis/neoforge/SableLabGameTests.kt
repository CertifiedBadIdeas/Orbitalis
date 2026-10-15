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
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.SurfaceFlight
import org.cbi.orbitalis.core.planet.TwoBodyGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableOrbitExperiment
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.neoforge.integration.SableLabIntegration
import org.joml.Vector3d
import kotlin.math.PI
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

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sable_burn", timeoutTicks = 1500)
    fun sableOrbitalManoeuvres(helper: GameTestHelper) {
        val server = helper.level.server
        val scene = SableLabIntegration.scene(server)
        val config = SubLevelPhysicsSystem.require(scene.level).config
        val originalSubsteps = config.substepsPerTick
        val gravity = TwoBodyGravity(SableOrbitExperiment.MU)
        onFinished(helper) {
            config.substepsPerTick = originalSubsteps
            scene.clear()
        }

        fun rejected(action: () -> Unit) {
            var rejected = false
            try {
                action()
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Invalid or conflicting burn was accepted")
        }
        scene.start()
        rejected { scene.requestBurn(0, true, 1.0) }
        rejected { scene.startOrbit() }
        helper.assertTrue(scene.size == 2 && scene.orbitMetrics == null, "Rejected restart damaged the active scene")

        fun runBurn(
            substeps: Int,
            prograde: Boolean,
            completed: (Double) -> Unit,
        ) {
            config.substepsPerTick = substeps
            scene.startOrbit()
            rejected { scene.requestBurn(1, prograde, 1.0) }
            helper.runAfterDelay(60) {
                val controlEnergy = gravity.specificEnergy(scene.orbitalState(0))
                helper.assertTrue(scene.reading(1).angularVelocity.length() > 0.1, "Burn requires a spinning construction")
                rejected { scene.requestBurn(2, prograde, 1.0) }
                for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1.01)) {
                    rejected { scene.requestBurn(1, prograde, bad) }
                }
                val direction = if (prograde) "prograde" else "retrograde"
                helper.assertTrue(
                    server.commands.dispatcher.execute(
                        "orbitalis lab sable burn $direction 1 1",
                        server.createCommandSourceStack().withLevel(scene.level),
                    ) == 1,
                    "Burn command failed",
                )
                rejected { scene.requestBurn(0, prograde, 0.5) }
                scene.requestShift(Vec3(32.0, 0.0, 0.0))
                helper.runAfterDelay(1) {
                    helper.assertTrue(scene.completedBurns == 1 && !scene.burnPending, "Burn was not applied exactly once")
                    helper.assertTrue(scene.completedShifts == 1, "Burn did not exercise a frame shift")
                    val receipt = scene.lastBurn!!
                    helper.assertTrue(receipt.probeIndex == 1 && abs(receipt.deltaV.length() - 1.0) < 1e-10, "Wrong burn target or delta-v")
                    val beforeEnergy = gravity.specificEnergy(receipt.before)
                    val afterEnergy = gravity.specificEnergy(receipt.after)
                    helper.assertTrue(if (prograde) afterEnergy > beforeEnergy else afterEnergy < beforeEnergy, "Wrong energy change")
                    helper.assertTrue(
                        abs(gravity.specificEnergy(scene.orbitalState(1)) - afterEnergy) < 0.1,
                        "Native impulse disagrees with burn receipt",
                    )
                    helper.assertTrue(
                        abs(gravity.specificEnergy(scene.orbitalState(0)) - controlEnergy) < 0.05,
                        "Burn changed the control construction",
                    )
                    val expected = gravity.apsides(receipt.after)!!
                    helper.assertTrue(
                        if (prograde) expected.apoapsis > 26.0 else expected.periapsis < 10.5,
                        "Burn did not change the ellipse",
                    )
                    val spin = scene.reading(1).angularVelocity
                    var minRadius = Double.POSITIVE_INFINITY
                    var maxRadius = 0.0

                    fun observe() {
                        helper.assertTrue(
                            scene.failureReason == null && scene.burnRejection == null,
                            "Manoeuvre scene stopped: ${scene.failureReason}",
                        )
                        val radius = scene.orbitalState(1).position.length()
                        minRadius = minOf(minRadius, radius)
                        maxRadius = maxOf(maxRadius, radius)
                        val metrics = scene.orbitMetrics!!
                        if (metrics.seconds < receipt.seconds + 14.0) {
                            helper.runAfterDelay(1) { observe() }
                            return
                        }
                        helper.assertTrue(scene.completedBurns == 1, "Queued burn repeated on another substep")
                        helper.assertTrue(scene.reading(1).angularVelocity.distance(spin) < 0.002, "COM burn introduced spin torque")
                        helper.assertTrue(metrics.maximumPositionError < 1.6, "Post-burn trajectory diverged: $metrics")
                        helper.assertTrue(metrics.maximumRelativeEnergyError < 0.02, "Burn misreported as energy drift: $metrics")
                        helper.assertTrue(metrics.maximumRelativeMomentumError < 0.01, "Burn momentum mismatch: $metrics")
                        val actual = scene.orbitApsides(1)!!
                        helper.assertTrue(abs(actual.periapsis - expected.periapsis) < 0.6, "Periapsis mismatch: $actual vs $expected")
                        helper.assertTrue(abs(actual.apoapsis - expected.apoapsis) < 0.6, "Apoapsis mismatch: $actual vs $expected")
                        val control = scene.orbitApsides(0)!!
                        helper.assertTrue(
                            abs(control.periapsis - 16.0) < 0.6 && abs(control.apoapsis - 16.0) < 0.6,
                            "Control orbit changed",
                        )
                        helper.assertTrue(
                            if (prograde) abs(maxRadius - expected.apoapsis) < 0.8 else abs(minRadius - expected.periapsis) < 0.8,
                            "Construction never reached its new apsis: min=$minRadius max=$maxRadius expected=$expected",
                        )
                        helper.assertTrue(
                            server.commands.dispatcher.execute(
                                "orbitalis lab sable sample",
                                server.createCommandSourceStack().withLevel(scene.level),
                            ) ==
                                1,
                            "Post-burn sample failed",
                        )
                        println(
                            "ORBITALIS_SABLE_BURN substeps=$substeps direction=$direction expected=$expected actual=$actual min=$minRadius max=$maxRadius $metrics",
                        )
                        completed(metrics.maximumPositionError)
                    }
                    observe()
                }
            }
        }
        helper.runAfterDelay(1) {
            runBurn(1, true) { coarsePrograde ->
                runBurn(1, false) { coarseRetrograde ->
                    runBurn(4, true) { finePrograde ->
                        runBurn(4, false) { fineRetrograde ->
                            helper.assertTrue(
                                finePrograde < coarsePrograde * 0.6 && fineRetrograde < coarseRetrograde * 0.6,
                                "Substeps did not improve burn accuracy",
                            )
                            scene.requestBurn(1, true, 0.1)
                            scene.startOrbit()
                            helper.runAfterDelay(3) {
                                helper.assertTrue(
                                    !scene.burnPending && scene.completedBurns == 0 && scene.lastBurn == null,
                                    "Restart retained a queued burn",
                                )
                                // Two boosts stay bounded, the third predicts escape from this diagnostic band.
                                scene.requestBurn(0, true, 1.0)
                                helper.runAfterDelay(1) {
                                    scene.requestBurn(0, true, 1.0)
                                    helper.runAfterDelay(1) {
                                        rejected { scene.requestBurn(0, true, 1.0) }
                                        scene.requestBurn(1, false, 0.1)
                                        scene.clear()
                                        helper.runAfterDelay(2) {
                                            helper.assertTrue(
                                                !scene.burnPending && scene.size == 0 && scene.completedBurns == 0,
                                                "Clear retained a burn",
                                            )
                                            helper.succeed()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "sable_flight", timeoutTicks = 4000)
    fun sableSurfaceFlightAndLanding(helper: GameTestHelper) {
        val server = helper.level.server
        val scene = SableLabIntegration.scene(server)
        val level = scene.level
        val config = SubLevelPhysicsSystem.require(level).config
        val originalSubsteps = config.substepsPerTick
        val corner = BlockPos(2, 104, 122)
        val originalCorner = level.getBlockState(corner)
        val originallyForced = level.forcedChunks.toLongArray().toSet()
        onFinished(helper) {
            config.substepsPerTick = originalSubsteps
            scene.clear()
            if (level.getBlockState(corner).`is`(Blocks.DIAMOND_BLOCK)) level.setBlock(corner, originalCorner, 3)
        }
        level.setBlock(corner, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3)
        var rejected = false
        try {
            scene.startFlight()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        helper.assertTrue(rejected && level.getBlockState(corner).`is`(Blocks.DIAMOND_BLOCK), "Flight overwrote an occupied pad")
        helper.assertTrue(scene.size == 0, "Failed flight start retained a construction")
        level.setBlock(corner, originalCorner, 3)

        fun runFlight(
            substeps: Int,
            completed: () -> Unit,
        ) {
            config.substepsPerTick = substeps
            helper.assertTrue(
                server.commands.dispatcher.execute("orbitalis lab sable flight", server.createCommandSourceStack().withLevel(level)) == 1,
                "Flight command failed",
            )
            val body = SubLevelContainer.getContainer(level)!!.allSubLevels.single()
            val phases = mutableSetOf<SurfaceFlight.Phase>()
            var previousPhase: SurfaceFlight.Phase? = null
            helper.runAfterDelay(3) {
                val mass = scene.reading(0).mass

                fun observe() {
                    helper.assertTrue(scene.failureReason == null, scene.failureReason ?: "Flight stopped")
                    val report = scene.flightReport!!
                    phases.add(report.phase)
                    if (report.phase != previousPhase) {
                        println(
                            "ORBITALIS_SABLE_FLIGHT substeps=$substeps phase=${report.phase} t=${report.seconds} state=${scene.orbitalState(
                                0,
                            )}",
                        )
                        previousPhase = report.phase
                    }
                    helper.assertTrue(
                        scene.size == 1 && !body.isRemoved && SubLevelContainer.getContainer(level)!!.allSubLevels.singleOrNull() === body,
                        "Flight replaced its construction",
                    )
                    helper.assertTrue(scene.reading(0).mass == mass, "Flight changed construction mass")
                    helper.assertTrue(
                        body.plot.embeddedLevelAccessor
                            .getBlockState(BlockPos.ZERO)
                            .`is`(Blocks.GOLD_BLOCK),
                        "Flight lost its block",
                    )
                    helper.assertTrue(report.maximumThrust <= SurfaceFlight.MAX_THRUST + 1e-8, "Unbounded diagnostic thrust")
                    if (report.phase in setOf(SurfaceFlight.Phase.COAST, SurfaceFlight.Phase.TOUCHDOWN, SurfaceFlight.Phase.LANDED)) {
                        helper.assertTrue(report.thrust.length() == 0.0, "Coast or landing used hidden support thrust")
                    }
                    if (report.phase != SurfaceFlight.Phase.LANDED) {
                        helper.runAfterDelay(1) { observe() }
                        return
                    }
                    helper.assertTrue(
                        phases.containsAll(
                            SurfaceFlight.Phase.entries.filter {
                                it != SurfaceFlight.Phase.FAILED
                            },
                        ),
                        "Flight skipped stages: $phases",
                    )
                    helper.assertTrue(report.coastAngle >= 2.0 * PI, "Coast did not complete a measured revolution")
                    helper.assertTrue(report.maximumCoastEnergyError < 0.01, "Unpowered orbit energy drifted")
                    val touchdown = scene.orbitalState(0)
                    helper.assertTrue((touchdown.position - SurfaceFlight.LAUNCH).length() < 0.15, "Flight missed its pad")
                    helper.runAfterDelay(40) {
                        helper.assertTrue(scene.failureReason == null, "Unsupported landing fell through the pad")
                        val settled = scene.orbitalState(0)
                        helper.assertTrue(
                            (settled.position - touchdown.position).length() < 0.05 && settled.velocity.length() < 0.15,
                            "Landing was not supported by actual terrain",
                        )
                        helper.assertTrue(
                            scene.flightReport!!.thrust.length() == 0.0 && !body.isRemoved,
                            "Post-landing state was corrected",
                        )
                        println(
                            "ORBITALIS_SABLE_LANDING substeps=$substeps t=${report.seconds} maxRadius=${report.maximumRadius} energyError=${report.maximumCoastEnergyError} state=$settled",
                        )
                        scene.clear()
                        helper.assertTrue(level.getBlockState(corner) == originalCorner, "Flight pad was not restored")
                        helper.assertTrue(
                            level.forcedChunks.toLongArray().toSet() == originallyForced,
                            "Flight modified persistent forced chunks",
                        )
                        completed()
                    }
                }
                observe()
            }
        }
        helper.runAfterDelay(1) {
            runFlight(1) {
                runFlight(4) {
                    scene.startFlight()
                    helper.runAfterDelay(30) {
                        helper.assertTrue(scene.flightReport!!.phase == SurfaceFlight.Phase.ASCENT, "Cancellation test never took off")
                        level.setBlock(corner, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3)
                        scene.clear()
                        helper.assertTrue(scene.size == 0 && scene.flightReport == null, "Clear retained an active flight")
                        helper.assertTrue(
                            level.getBlockState(corner).`is`(Blocks.DIAMOND_BLOCK),
                            "Cleanup overwrote a subsequent player edit",
                        )
                        level.setBlock(corner, originalCorner, 3)
                        helper.succeed()
                    }
                }
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
