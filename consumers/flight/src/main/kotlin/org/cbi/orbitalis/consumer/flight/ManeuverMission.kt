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

import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.creative_vector_thruster.CreativeVectorThrusterBlockEntity
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerPlayer
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.ManeuverPlan
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Vector3d
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Disposable finite-burn executor. The plan is shared navigation intent, not an orbit/land route. */
internal class ManeuverMission(
    override val body: ServerSubLevel,
    private val plan: ManeuverPlan,
) : FlightMission {
    override var stage = "PLANNING"
        private set
    override val completionReason get() = "flight plan complete"
    private val server = body.level.server
    private val field = SableFlightGravity.flatField(body.level)
    private val rocket =
        body.plot.loadedChunks.any { chunk ->
            chunk.chunk.blockEntities.values
                .any { it is CreativeVectorThrusterBlockEntity }
        }
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Orbitalis consumer maneuver plan").apply { isDaemon = true } }
    private val future: Future<ManeuverPlan.Prediction>
    private var burns = emptyList<ManeuverPlan.Burn>()
    private var index = 0
    private var rocketDrive: RocketDrive? = null
    private var shuttle: AutopilotSession? = null
    private var burn: ManeuverBurn? = null
    private var previous: CartesianState? = null
    private var previousTick = 0L
    private var started = 0.0
    private var settling = 0
    private var closed = false
    private var residual = 0.0
    val completedResiduals = mutableListOf<Double>()

    init {
        require(body.level.dimension() == FlatPlanetSpace.SPACE) { "Maneuver plans currently execute in orbital space; orbit first" }
        require(!body.isRemoved && !SubLevelPhysicsSystem.require(body.level).paused) { "Wait for loaded, active ship physics" }
        val now = server.overworld().gameTime
        val elapsed = (now - plan.epochTick) / 20.0
        require(plan.nodes.isNotEmpty() && plan.nodes.all { it.magnitude >= 0.2 }) { "Save non-zero maneuver nodes first" }
        var lastEnd = elapsed
        for (node in plan.nodes) {
            val halfBurn = node.magnitude / 6.0 + 3.0
            require(node.seconds - halfBurn >= lastEnd + 30.0) { "Allow 30 seconds to turn plus half the burn duration before each node" }
            lastEnd = node.seconds + halfBurn
        }
        val initial = state()
        org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
            .reserve(body.uniqueId, this)
        future =
            try {
                run {
                    val sources =
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight
                            .sample(body.level.server)
                            .dynamics
                    worker.submit<ManeuverPlan.Prediction> { plan.predict(field, initial, now, sources) }
                }
            } catch (
                failure: RuntimeException,
            ) {
                worker.shutdownNow()
                org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                    .release(body.uniqueId, this)
                throw failure
            }
    }

    private fun state() =
        CartesianState(
            FlightSpaceCoordinates.globalPosition(body.level, AutopilotSession.position(body)),
            AutopilotSession.vector(SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body).getLinearVelocity(Vector3d())),
        )

    private fun acquire() {
        if (rocket) {
            if (rocketDrive == null) rocketDrive = RocketDrive(body)
        } else if (shuttle == null) {
            shuttle = AutopilotSession(body, AutopilotSession.position(body), mission = true)
        }
    }

    private fun cut() {
        rocketDrive?.close()
        rocketDrive = null
        shuttle?.close()
        shuttle = null
    }

    override fun requestLanding(): Unit = throw IllegalStateException("Stop the maneuver plan before engaging land")

    override fun tick(pilot: ServerPlayer?): Boolean {
        check(
            !closed && !body.isRemoved && body.level.dimension() == FlatPlanetSpace.SPACE,
        ) { "Maneuver ship unloaded, removed or changed frame" }
        check(!SubLevelPhysicsSystem.require(body.level).paused) { "Maneuver physics paused" }
        val now = server.overworld().gameTime
        val forces =
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .sample(server)
                .dynamics
        if (stage == "PLANNING") {
            if (!future.isDone) return false
            val prediction =
                try {
                    future.get()
                } catch (failure: java.util.concurrent.ExecutionException) {
                    throw IllegalStateException("Maneuver prediction failed: " + failure.cause?.message, failure.cause)
                }
            worker.shutdown()
            require(
                prediction.burns.size == plan.nodes.size,
            ) { "Plan hits the planet or leaves the prediction envelope before its final node" }
            burns = prediction.burns
            // Preserve inertial target vectors calculated at the planned node states.
            acquire()
            cut()
            stage = "COAST"
        }
        val target = burns[index]
        val node = plan.nodes[target.index]
        val clock = (now - plan.epochTick) / 20.0
        val burnStart = node.seconds - (node.magnitude / 6.0 + 3.0)
        val current = state()
        if (burn == null) {
            if (clock < burnStart - 30.0) return false
            acquire()
            stage = "ALIGN"
            if (rocket) rocketDrive!!.turn(target.deltaVelocity)
            val aligned = !rocket || rocketDrive!!.alignment(target.deltaVelocity) > 0.995
            require(clock <= node.seconds + 5.0 || aligned) { "Missed node while turning; edit its time" }
            if (clock < burnStart || !aligned) return false
            burn = ManeuverBurn(target.deltaVelocity)
            previous = current
            previousTick = now
            started = clock
            stage = "BURN"
        }
        val control = checkNotNull(burn)
        val dt = (now - previousTick) / 20.0
        if (dt > 0.0) {
            control.observe(
                checkNotNull(previous).velocity,
                current.velocity,
                forces.acceleration(checkNotNull(previous).position, -dt),
                forces.acceleration(current.position),
                dt,
            )
            previous = current
            previousTick = now
        }
        require(clock - started < node.magnitude / 1.0 + 45.0) { "Maneuver burn timed out; insufficient engine authority" }
        residual = control.remaining.length()
        if (residual <= 0.15 || settling > 0) {
            cut()
            stage = "SETTLING"
            settling++
            if (settling < 30) return false
            require(residual < 0.75) { "Maneuver residual %.2f m/s exceeds tolerance".format(java.util.Locale.ROOT, residual) }
            completedResiduals.add(residual)
            index++
            burn = null
            previous = null
            settling = 0
            if (index == burns.size) {
                stage = "COMPLETE"
                return true
            }
            stage = "COAST"
            return false
        }
        val thrust = control.thrust()
        if (rocket) {
            val axis = target.deltaVelocity.normalized()
            val along = control.remaining.dot(axis)
            require(along >= -0.5 && (control.remaining - axis * along).length() < maxOf(1.0, node.magnitude * 0.1)) {
                "Burn deviated from its planned direction"
            }
            rocketDrive!!.update(forces.acceleration(current.position) + axis * (along * 0.5).coerceIn(0.0, 3.0), axis)
        } else {
            shuttle!!.update(forces.acceleration(current.position) + thrust)
        }
        return false
    }

    override fun status() =
        "Maneuver ${minOf(
            index + 1,
            plan.nodes.size,
        )}/${plan.nodes.size}: $stage; remaining %.2f m/s".format(java.util.Locale.ROOT, residual)

    override fun close() {
        if (closed) return
        closed = true
        future.cancel(true)
        worker.shutdownNow()
        try {
            cut()
        } finally {
            org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                .release(body.uniqueId, this)
        }
    }
}
