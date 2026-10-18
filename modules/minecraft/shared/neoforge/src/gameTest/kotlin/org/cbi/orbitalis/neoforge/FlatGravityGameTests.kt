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
import net.minecraft.util.Unit
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Vector3d
import java.util.concurrent.CompletableFuture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

@PrefixGameTestTemplate(false)
object FlatGravityGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "flat_gravity", timeoutTicks = 6000)
    fun nativeFiniteBodyFreeOrbit(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val field = SableFlightGravity.flatField(level)
        val planningModel =
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .sample(level.server)
                .dynamics
        val plan = CompletableFuture.supplyAsync { BallisticOrbit.firstRevolution(field, planningModel, clearance = 64.0) }
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val physics = SubLevelPhysicsSystem.require(level)
        helper.assertTrue(
            DimensionPhysicsData.getGravity(level).length() == 0.0 && DimensionPhysicsData.getUniversalDrag(level) == 0.0,
            "Space has native gravity/drag",
        )

        // Planning runs off-thread. Do not let an already spawned ship fall through the return
        // boundary during thousands of accelerated ticks while that CPU result is pending.
        fun createWhenPlanned() {
            level.resetEmptyTime()
            if (!plan.isDone) {
                java.util.concurrent.locks.LockSupport
                    .parkNanos(2_000_000L)
                helper.runAfterDelay(1) { createWhenPlanned() }
                return
            }
            val pose = Pose3d()
            pose.position().set(0.0, 4096.0 + FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ORBIT_HEIGHT, 0.0)
            val body = container.allocateNewSubLevel(pose) as ServerSubLevel
            val owner = Any()
            org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                .reserve(body.uniqueId, owner)
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
                    ) {
                        test.error?.printStackTrace()
                        cleanup()
                    }

                    override fun testAddedForRerun(
                        test: GameTestInfo,
                        retry: GameTestInfo,
                        runner: GameTestRunner,
                    ) {}

                    fun cleanup() {
                        org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                            .release(body.uniqueId, owner)
                        plan.cancel(false)
                        if (!body.isRemoved) {
                            container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                        }
                    }
                },
            )
            body.plot.newEmptyChunk(body.plot.centerChunk)
            body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
            body.updateLastPose()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)

            fun start() {
                level.resetEmptyTime()
                if (body.massTracker.centerOfMass == null) {
                    helper.runAfterDelay(1) { start() }
                    return
                }
                val nativeInitial = plan.join().initial
                val model =
                    org.cbi.orbitalis.minecraft.flight.CelestialFlight
                        .sample(level.server)
                val earthFrame = model.frames.profile.earthSurfaceFrame(planningModel.epochTick)
                val initial =
                    org.cbi.orbitalis.core.planet.CartesianState(
                        earthFrame.orientation.apply(nativeInitial.position),
                        earthFrame.orientation.apply(nativeInitial.velocity),
                    )
                val before = SableFlightGravity.reading(body)
                val desired = FlightSpaceCoordinates.localPosition(FlatPlanetSpace.SPACE, initial.position)
                val delta = desired - before.position
                val handle = physics.getPhysicsHandle(body)
                handle.teleport(Vector3d(body.logicalPose().position()).add(delta.x, delta.y, delta.z), body.logicalPose().orientation())
                handle.addLinearAndAngularVelocity(
                    Vector3d(initial.velocity.x, initial.velocity.y, initial.velocity.z).sub(before.velocity),
                    Vector3d(),
                )
                var predicted = initial
                var predictedTick = model.frames.tick
                val angleBasis = earthFrame.orientation
                var ticks = 0
                var previous = 0.0
                var angle = 0.0
                var error = 0.0

                fun measure() {
                    level.resetEmptyTime()
                    val reading = SableFlightGravity.reading(body)
                    val p = FlightSpaceCoordinates.globalPosition(level, reading.position)
                    val sample =
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight
                            .sample(level.server)
                    if (ticks == 0) {
                        // Pending native teleport/velocity commands are applied at the next physics boundary.
                        // Start comparison from that observed boundary, not from the pre-command tick.
                        predicted =
                            org.cbi.orbitalis.core.planet.CartesianState(
                                p,
                                Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z),
                            )
                        predictedTick = sample.frames.tick
                    }
                    val elapsed = (sample.frames.tick - predictedTick) * 0.05
                    var advanced = 0.0
                    while (advanced < elapsed - 1e-10) {
                        val dt = minOf(0.005, elapsed - advanced)
                        val seconds = (predictedTick - model.frames.tick) * 0.05 + advanced
                        val half = predicted.velocity + model.dynamics.acceleration(predicted.position, seconds) * (dt * 0.5)
                        val nextP = predicted.position + half * dt
                        predicted =
                            org.cbi.orbitalis.core.planet.CartesianState(
                                nextP,
                                half + model.dynamics.acceleration(nextP, seconds + dt) * (dt * 0.5),
                            )
                        advanced += dt
                    }
                    predictedTick = sample.frames.tick
                    val localP = sample.nativePoint(p, net.minecraft.world.level.Level.OVERWORLD)
                    val relative = angleBasis.inverse(p)
                    val next = atan2(relative.x, relative.y)
                    var step = next - previous
                    if (step < -PI) step += 2 * PI
                    if (step > PI) step -= 2 * PI
                    angle += step
                    previous = next
                    error = maxOf(error, (p - predicted.position).length())
                    if (ticks % 200 ==
                        0
                    ) {
                        println(
                            "ORBITALIS_FLAT_INERT substeps=${physics.config.substepsPerTick} ticks=$ticks predictionErrorMetres=$error p=$p v=${reading.velocity}",
                        )
                    }
                    helper.assertTrue(!field.body.contains(localP), "Native orbit hit the body")
                    helper.assertTrue(
                        // Rapier uses float state and two native substeps; budget 0.2% of the 5 km orbit.
                        error < initial.position.length() * 0.002,
                        "Native unpowered prediction position error=$error m; substeps=${physics.config.substepsPerTick}",
                    )
                    if (angle >= 2 * PI) {
                        println("ORBITALIS_FLAT_INERT_COMPLETE ticks=$ticks predictionErrorMetres=$error")
                        helper.succeed()
                        return
                    }
                    ticks++
                    helper.runAfterDelay(1) { measure() }
                }
                helper.runAfterDelay(1) { measure() }
            }
            helper.runAfterDelay(5) { start() }
        }
        helper.runAfterDelay(1) { createWhenPlanned() }
    }
}
