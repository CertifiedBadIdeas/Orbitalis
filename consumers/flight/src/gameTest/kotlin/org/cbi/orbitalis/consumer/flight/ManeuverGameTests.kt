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

package org.cbi.orbitalis.consumer.flight

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.util.Unit
import net.minecraft.world.level.Level
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.ManeuverNode
import org.cbi.orbitalis.core.planet.ManeuverPlan
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Vector3d

@PrefixGameTestTemplate(false)
object ManeuverGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 6000, batch = "maneuver")
    fun nativeRocketManeuverPlan(helper: GameTestHelper) = scenario(helper, true)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 6000, batch = "maneuver_shuttle")
    fun nativeShuttleManeuverPlan(helper: GameTestHelper) = scenario(helper, false)

    private fun scenario(
        helper: GameTestHelper,
        rocket: Boolean,
    ) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val field = SableFlightGravity.flatField(level)
        val sample = CelestialFlight.sample(level)
        val geometry = sample.dynamics.profile.geometry
        val orbit =
            BallisticOrbit.firstRevolution(
                field,
                sample.dynamics,
                maxOf(geometry.earthHalfSize, geometry.earthEnvelope.ascentDistance + 200.0),
                32.0,
                direction = -1.0,
            )
        // The reference stores inertial states in frozen launch axes, so rotate its velocity
        // as a direction rather than adding the surface chart's transport velocity again.
        val rootInitial =
            CartesianState(
                sample.rootPoint(orbit.initial.position, Level.OVERWORLD),
                sample.rootDirection(orbit.initial.velocity, Level.OVERWORLD),
            )
        val nativeInitial = sample.nativeState(rootInitial, FlatPlanetSpace.SPACE)
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val body =
            if (rocket) {
                VacuumTestBlueprint.create(
                    level,
                    BlockPos.containing(nativeInitial.position.x, nativeInitial.position.y, nativeInitial.position.z),
                    "rocket",
                )
            } else {
                PropulsionGameTests
                    .Rig(
                        helper,
                        nativeInitial.position.x,
                        dimension = FlatPlanetSpace.SPACE,
                        y = nativeInitial.position.y,
                    ).body
            }
        var mission: ManeuverMission? = null
        var retention: AutopilotFlightLoading? = null
        onFinished(helper) {
            mission?.close()
            retention?.close()
            if (!body.isRemoved) {
                VacuumTestBlueprint.discardGlue(body)
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
        }
        helper.runAfterDelay(20) {
            if (!rocket) {
                body.plot.loadedChunks
                    .flatMap {
                        it.chunk.blockEntities.values
                    }.filterIsInstance<CreativeThrusterBlockEntity>()
                    .forEach {
                        it.setThrustConfig(9)
                    }
            }
            val physics = SubLevelPhysicsSystem.require(level)
            physics.pipeline.prePhysicsTicks()
            physics.pipeline.resetVelocity(body)
            physics
                .getPhysicsHandle(
                    body,
                ).addLinearAndAngularVelocity(
                    Vector3d(nativeInitial.velocity.x, nativeInitial.velocity.y, nativeInitial.velocity.z),
                    Vector3d(),
                )
            physics.pipeline.prePhysicsTicks()
            val initial = FlightGuidanceFrames.rootState(body)
            val initialModel = CelestialFlight.sample(level).dynamics
            val plan =
                ManeuverPlan(
                    level.server.overworld().gameTime,
                    listOf(ManeuverNode(50.0, 8.0, 3.0, 2.0), ManeuverNode(115.0, -5.0, 0.0, 0.0)),
                )
            mission = ManeuverMission(body, plan)
            retention = AutopilotFlightLoading(body)
            container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            var ticks = 0
            var powered = false
            var turned = false
            var fading = -1

            fun run() {
                level.resetEmptyTime()
                retention!!.follow(body)
                val engines =
                    body.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<AbstractThrusterBlockEntity>()
                val current = checkNotNull(mission)
                if (fading < 0 && current.tick(null)) {
                    current.close()
                    fading = 0
                }
                if (current.stage == "ALIGN") turned = true
                powered = powered || engines.any { it.power > 0.001f }
                if (++ticks % 200 == 0) println("ORBITALIS_MANEUVER rocket=$rocket tick=$ticks ${current.status()}")
                if (fading >= 0 && ++fading > 50) {
                    helper.assertTrue(
                        powered && turned && current.completedResiduals.size == 2,
                        "Maneuvers did not actuate native engines through both nodes",
                    )
                    helper.assertTrue(current.completedResiduals.all { it < 0.75 }, "Finite burns missed delta-v tolerance")
                    helper.assertTrue(
                        engines.all { it.power == 0.0f && it.currentThrust == 0.0f },
                        "Engine inputs/output retained after plan completion",
                    )
                    val elapsed = (CelestialFlight.sample(level).dynamics.epochTick - initialModel.epochTick) / 20.0
                    val coast =
                        CoastPrediction
                            .predict(
                                field,
                                initial,
                                CoastPrediction.Limits(horizon = elapsed, range = initialModel.profile.flightRange),
                                initialModel,
                            ).terminal.state
                    val actual = FlightGuidanceFrames.rootState(body).position
                    helper.assertTrue((actual - coast.position).length() > 100.0, "Native trajectory did not change after burns")
                    helper.assertTrue(
                        physics.getPhysicsHandle(body).getAngularVelocity(Vector3d()).length() < 0.1,
                        "Maneuver left the ship spinning",
                    )
                    println(
                        "ORBITALIS_MANEUVER_COMPLETE rocket=$rocket residuals=${current.completedResiduals} coastDeviation=${(actual - coast.position).length()}",
                    )
                    helper.succeed()
                    return
                }
                helper.runAfterDelay(1) { run() }
            }
            run()
        }
    }
}
