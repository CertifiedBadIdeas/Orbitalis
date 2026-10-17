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

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs

/** Bounded search over burn epochs and continuous return curves; no fixed longitude trigger. */
internal object RocketReturn {
    const val TURN_LEAD = 12.0

    class Path(
        val segments: List<RocketConnection>,
        val deltaV: Double,
    ) {
        val duration get() = segments.sumOf { it.duration }

        fun at(time: Double): RocketConnection.Sample {
            var remaining = time
            for (segment in segments) {
                if (remaining <= segment.duration) return segment.at(remaining)
                remaining -= segment.duration
            }
            return segments.last().at(segments.last().duration)
        }
    }

    data class Departure(
        val delay: Double,
        val path: Path,
    )

    /** Immutable free coast, then a burn. Turn lead is included before comparing candidate costs. */
    fun departure(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        home: Vector3,
        authority: Double,
        direction: Vector3,
        horizon: Double,
    ): Departure {
        require(horizon.isFinite() && horizon in TURN_LEAD..180.0)
        var state = initial
        var time = 0.0
        var next = TURN_LEAD
        var best: Departure? = null
        while (time <= horizon) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Return planning cancelled")
            if (time + 1e-7 >= next) {
                if (state.position.y > field.body.halfHeight + 32.0) {
                    candidate(field, state, home, authority, direction)?.let { path ->
                        if (best == null || path.deltaV < best.path.deltaV) best = Departure(time, path)
                    }
                }
                next += 2.0
            }
            val dt = 0.05
            val half = state.velocity + field.acceleration(state.position) * (dt * 0.5)
            val position = state.position + half * dt
            state = CartesianState(position, half + field.acceleration(position) * (dt * 0.5))
            time += dt
        }
        return checkNotNull(best) { "No efficient return window to $home in the next $horizon seconds" }
    }

    private fun candidate(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        home: Vector3,
        authority: Double,
        direction: Vector3,
    ): Path? {
        require(authority.isFinite() && authority >= 25.0)
        val remaining = (home - initial.position).dot(direction)
        // The approach axis is fixed by the orbit, not by instantaneous velocity at a corner.
        // Reject a pad that has already been passed, while allowing natural orbital curvature.
        if (remaining < 20.0) return null
        var best: Path? = null
        for (height in listOf(80.0, 160.0, 300.0)) {
            val approach = CartesianState(home + Vector3(0.0, height, 0.0), Vector3(0.0, -10.0, 0.0))
            for (landingDuration in listOf(maxOf(8.0, height / 25.0), maxOf(12.0, height / 18.0))) {
                val landing =
                    RocketConnection(
                        approach,
                        CartesianState(home + Vector3(0.0, 0.6, 0.0), Vector3(0.0, -0.2, 0.0)),
                        Vector3.ZERO,
                        Vector3.ZERO,
                        landingDuration,
                    )
                for (duration in 8..80 step 2) {
                    for (initialThrust in listOf(2.0, 6.0, 10.0)) {
                        val braking =
                            RocketConnection(
                                initial,
                                approach,
                                field.acceleration(initial.position) - initial.velocity.normalized() * initialThrust,
                                Vector3.ZERO,
                                duration.toDouble(),
                            )
                        val path = Path(listOf(braking, landing), 0.0)
                        val cost = cost(field, path, initial, home, direction, remaining, authority) ?: continue
                        if (best == null || cost < best.deltaV) best = Path(listOf(braking, landing), cost)
                    }
                }
            }
        }
        // Separate along-track braking from an off-plane approach. A single shared-duration
        // polynomial cannot stop above a near-edge pad while also translating hundreds of metres
        // sideways without overshoot. Every join retains velocity and acceleration continuity.
        val cross = Vector3(home.x - initial.position.x, 0.0, home.z - initial.position.z) - direction * remaining
        if (cross.length() > 50.0) {
            for (height in listOf(300.0, 500.0)) {
                for (fraction in listOf(0.0, 0.25, 0.5)) {
                    val joinPosition = initial.position + direction * remaining + cross * fraction
                    val join =
                        CartesianState(
                            Vector3(joinPosition.x, maxOf(home.y + height, initial.position.y), joinPosition.z),
                            cross.normalized() * minOf(30.0, cross.length() / 12.0) + Vector3(0.0, -10.0, 0.0),
                        )
                    val approach = CartesianState(home + Vector3(0.0, 80.0, 0.0), Vector3(0.0, -10.0, 0.0))
                    val landing =
                        RocketConnection(
                            approach,
                            CartesianState(home + Vector3(0.0, 0.6, 0.0), Vector3(0.0, -0.2, 0.0)),
                            Vector3.ZERO,
                            Vector3.ZERO,
                            8.0,
                        )
                    for (crossDuration in listOf(16.0, 24.0, 32.0)) {
                        val crossing = RocketConnection(join, approach, Vector3.ZERO, Vector3.ZERO, crossDuration)
                        for (duration in 8..40 step 2) {
                            for (initialThrust in listOf(2.0, 6.0, 10.0)) {
                                val braking =
                                    RocketConnection(
                                        initial,
                                        join,
                                        field.acceleration(initial.position) - initial.velocity.normalized() * initialThrust,
                                        Vector3.ZERO,
                                        duration.toDouble(),
                                    )
                                val path = Path(listOf(braking, crossing, landing), 0.0)
                                val cost = cost(field, path, initial, home, direction, remaining, authority) ?: continue
                                if (best == null || cost < best.deltaV) best = Path(path.segments, cost)
                            }
                        }
                    }
                }
            }
        }
        return best
    }

    /** Integral of required thrust acceleration, not kinetic energy or creative engine power. */
    private fun cost(
        field: SurfaceGravityLayer,
        path: Path,
        initial: CartesianState,
        home: Vector3,
        direction: Vector3,
        remaining: Double,
        authority: Double,
    ): Double? {
        val attitude = RocketAttitudeControl.Envelope(initial.velocity * -1.0)
        var cost = 0.0
        var surface = false
        var t = 0.0
        while (true) {
            val sample = path.at(t)
            val p = sample.state.position
            val thrust = sample.acceleration - field.acceleration(p)
            val horizontal = Vector3(p.x - home.x, 0.0, p.z - home.z).length()
            val progress = (p - initial.position).dot(direction)
            val clearance =
                Vector3(
                    maxOf(0.0, abs(p.x) - field.body.halfWidth),
                    maxOf(0.0, abs(p.y) - field.body.halfHeight),
                    maxOf(0.0, abs(p.z) - field.body.halfDepth),
                ).length()
            if (p.y <= 464.0 && sample.state.velocity.y < 0.0 && abs(p.x) < 960.0 && abs(p.z) < 960.0) surface = true
            val inside = abs(p.x) < field.body.halfWidth && abs(p.z) < field.body.halfDepth
            if ((horizontal >= 30.0 && clearance < 32.0) || (inside && p.y < home.y) || p.y >= maxOf(1064.0, initial.position.y) + 200.0 ||
                sample.state.velocity.length() >= 180.0 || thrust.length() > authority * 0.8 ||
                (surface && (abs(p.x) >= 980.0 || abs(p.z) >= 980.0)) ||
                (inside && p.y < maxOf(field.body.halfHeight + 32.0, home.y + 120.0) && horizontal >= 30.0) ||
                progress > remaining + 0.5
            ) {
                return null
            }
            val dt = minOf(0.25, path.duration - t)
            if (dt > 0.0 && !attitude.step(thrust, dt)) return null
            cost += thrust.length() * dt
            if (dt <= 0.0) break
            t += dt
        }
        if ((path.at(2.0).acceleration - field.acceleration(path.at(2.0).state.position)).dot(initial.velocity) >= 0.0) return null
        return cost
    }
}
