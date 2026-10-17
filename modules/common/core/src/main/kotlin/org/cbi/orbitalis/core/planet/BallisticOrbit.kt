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

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** A numerically fitted periodic meridian, not a Kepler ellipse or an altitude-hold path. */
class BallisticOrbit private constructor(
    private val states: List<CartesianState>,
    val step: Double,
    val period: Double,
    val minimumClearance: Double,
) {
    val initial: CartesianState get() = states.first()

    fun stateAt(seconds: Double): CartesianState {
        require(seconds.isFinite())
        val time = ((seconds % period) + period) % period
        val index = floor(time / step).toInt().coerceAtMost(states.lastIndex - 1)
        val fraction = (time - index * step) / minOf(step, period - index * step)
        val a = states[index]
        val b = states[index + 1]
        return CartesianState(a.position + (b.position - a.position) * fraction, a.velocity + (b.velocity - a.velocity) * fraction)
    }

    companion object {
        /** Bounded pure computation. Call off the game thread and cache for an unchanged field. */
        fun plan(
            field: SurfaceGravityLayer,
            altitude: Double = field.body.halfWidth,
            clearance: Double = 32.0,
            diagonal: Boolean = false,
        ): BallisticOrbit {
            require(
                altitude.isFinite() && altitude in (field.body.halfWidth * 0.8)..(field.body.halfWidth * 1.2) && clearance.isFinite() &&
                    clearance >= 0.0,
            )
            require(!diagonal || field.body.halfWidth == field.body.halfDepth) { "Diagonal meridian requires a square body" }
            val start = Vector3(0.0, field.body.halfHeight + altitude, 0.0)
            val direction = if (diagonal) Vector3(1.0, 0.0, 1.0).normalized() else Vector3(1.0, 0.0, 0.0)
            val dt = 0.02
            val escape = sqrt(-2.0 * field.potential(start))

            data class Shot(
                val speed: Double,
                val error: Double,
                val time: Double,
            )

            fun shoot(speed: Double): Shot? {
                var state = CartesianState(start, direction * speed)
                repeat(
                    kotlin.math
                        .ceil(15000.0 * sqrt(field.body.halfWidth / 1000.0))
                        .toInt()
                        .coerceIn(15000, 75000),
                ) { index ->
                    val previous = state
                    state = advance(field, state, dt)
                    if (clearance(field.body, state.position) < clearance ||
                        state.position.length() > maxOf(40000.0, field.body.halfWidth * 40.0)
                    ) {
                        return null
                    }
                    val before = previous.position.dot(direction)
                    val after = state.position.dot(direction)
                    if (after <= 0.0) {
                        if (state.position.y >= 0.0) return null
                        val fraction = before / (before - after)
                        return Shot(
                            speed,
                            previous.velocity.y + (state.velocity.y - previous.velocity.y) * fraction,
                            (index + fraction) * dt,
                        )
                    }
                }
                return null
            }
            var previous: Shot? = null
            var bracket: Pair<Shot, Shot>? = null
            for (index in 0..64) {
                val shot = shoot(escape * (0.4 + 0.5 * index / 64.0))
                val old = previous
                if (shot != null && old != null && shot.error * old.error <= 0.0) {
                    bracket = old to shot
                    break
                }
                previous = shot
            }
            val found = requireNotNull(bracket) { "No safe periodic low orbit found for this field and clearance" }
            var low = found.first
            var high = found.second
            repeat(24) {
                val middle = requireNotNull(shoot((low.speed + high.speed) * 0.5)) { "Orbit bracket crosses an unsafe trajectory" }
                if (low.error * middle.error <= 0.0) high = middle else low = middle
            }
            val best = if (abs(low.error) < abs(high.error)) low else high
            val period = best.time * 2.0
            val states = mutableListOf(CartesianState(start, direction * best.speed))
            var time = 0.0
            var minimum = Double.POSITIVE_INFINITY
            val energy = field.specificEnergy(states.first())
            while (time < period) {
                val next = advance(field, states.last(), minOf(dt, period - time))
                minimum = minOf(minimum, clearance(field.body, next.position))
                require(minimum >= clearance && abs((field.specificEnergy(next) - energy) / energy) < 0.001) {
                    "Predicted orbit violates clearance or energy bounds"
                }
                states.add(next)
                time = minOf(period, time + dt)
            }
            require((states.last().position - start).length() < 0.5 && (states.last().velocity - states.first().velocity).length() < 0.1) {
                "Predicted free trajectory does not close"
            }
            return BallisticOrbit(states.toList(), dt, period, minimum)
        }

        private fun clearance(
            body: RectangularGravity,
            p: Vector3,
        ): Double =
            Vector3(max(0.0, abs(p.x) - body.halfWidth), max(0.0, abs(p.y) - body.halfHeight), max(0.0, abs(p.z) - body.halfDepth)).length()

        private fun advance(
            field: SurfaceGravityLayer,
            state: CartesianState,
            dt: Double,
        ): CartesianState {
            val half = state.velocity + field.acceleration(state.position) * (dt * 0.5)
            val p = state.position + half * dt
            return CartesianState(p, half + field.acceleration(p) * (dt * 0.5))
        }
    }
}
