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

package org.cbi.orbitalis.core.planet

import org.cbi.orbitalis.core.orbit.CelestialDynamics
import kotlin.math.max

/** Time is seconds after the plan's game-time epoch. Components are metres per second. */
data class ManeuverNode(
    val seconds: Double,
    val prograde: Double,
    val normal: Double,
    val radial: Double,
) {
    init {
        require(seconds.isFinite() && seconds in 0.05..3600.0) { "Node time must be in 0.05..3600 seconds" }
        require(listOf(prograde, normal, radial).all { it.isFinite() && kotlin.math.abs(it) <= 500.0 }) { "Invalid node delta-v" }
        require(magnitude <= 500.0) { "Node delta-v exceeds 500 m/s" }
    }

    val magnitude: Double get() = Vector3(prograde, normal, radial).length()

    /** Right-handed velocity / orbit-normal / outward radial basis, evaluated at this node. */
    fun deltaVelocity(state: CartesianState): Vector3 {
        if (magnitude == 0.0) return Vector3.ZERO
        require(state.velocity.length() > 1e-6) { "A maneuver needs a non-zero velocity" }
        val tangent = state.velocity.normalized()
        val momentum = state.position.cross(state.velocity)
        require(momentum.length() > 1e-6) { "Orbital axes are undefined for purely radial motion" }
        val up = momentum.normalized()
        return tangent * prograde + up * normal + tangent.cross(up) * radial
    }
}

/** Shared navigation data. Executing engines is an optional integration, never part of the predictor. */
data class ManeuverPlan(
    val epochTick: Long,
    val nodes: List<ManeuverNode>,
) {
    init {
        require(epochTick >= 0 && nodes.size <= 8) { "A plan supports at most eight nodes" }
        require(nodes.zipWithNext().all { (a, b) -> b.seconds - a.seconds >= 0.05 }) { "Node times must increase" }
    }

    data class Segment(
        val offset: Double,
        val coast: CoastPrediction.Result,
    )

    data class Burn(
        val index: Int,
        val seconds: Double,
        val state: CartesianState,
        val deltaVelocity: Vector3,
    )

    data class Prediction(
        val segments: List<Segment>,
        val burns: List<Burn>,
    )

    /** Actual field, instantaneous preview impulses. Stops at the first collision or range limit. */
    fun predict(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        gameTick: Long,
    ): Prediction = predict(field, initial, gameTick, null)

    fun predict(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        gameTick: Long,
        sources: CelestialDynamics?,
    ): Prediction {
        require(gameTick >= 0)
        require(gameTick >= epochTick) { "Plan epoch is in the future" }
        val range = sources?.profile?.flightRange ?: 100000.0
        val start = CoastPrediction.predict(field, initial, CoastPrediction.Limits(horizon = 0.05, range = range), sources)
        if (start.terminal.seconds == 0.0) return Prediction(listOf(Segment(0.0, start)), emptyList())
        val elapsed = (gameTick - epochTick) / 20.0
        val segments = mutableListOf<Segment>()
        val burns = mutableListOf<Burn>()
        var time = 0.0
        var state = initial
        for ((index, node) in nodes.withIndex()) {
            val due = node.seconds - elapsed
            if (due < 0.0) continue // Past impulses are already part of the observed physical state.
            val duration = due - time
            if (duration > 0.0) {
                val coast = CoastPrediction.predict(field, state, limits(duration, range), sources, time)
                segments.add(Segment(time, coast))
                if (coast.end != CoastPrediction.End.HORIZON) return Prediction(segments, burns)
                state = coast.terminal.state
            }
            time = due
            val delta = node.deltaVelocity(state)
            burns.add(Burn(index, time, state, delta))
            state = CartesianState(state.position, state.velocity + delta)
        }
        segments.add(Segment(time, CoastPrediction.orbit(field, state, CoastPrediction.Limits(range = range), sources, time)))
        return Prediction(segments, burns)
    }

    private fun limits(
        duration: Double,
        range: Double,
    ) = CoastPrediction.Limits(
        horizon = duration,
        step = max(0.05, duration / 11999.0),
        sampleInterval = max(0.5, duration / 599.0),
        range = range,
    )
}
