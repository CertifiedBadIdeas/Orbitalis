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

/** Rounded distance bands around the reference body's volume, shared by travel and preloading. */
object PlanetFlightEnvelope {
    fun distance(position: Vector3): Double =
        Vector3(
            (abs(position.x) - FlatPlanetDefinition.HALF_SIZE).coerceAtLeast(0.0),
            (abs(position.y) - FlatPlanetDefinition.SEA_LEVEL).coerceAtLeast(0.0),
            (abs(position.z) - FlatPlanetDefinition.HALF_SIZE).coerceAtLeast(0.0),
        ).length()

    /** Earliest crossing under constant net acceleration; bounded search, not a collision solver. */
    fun arrival(
        position: Vector3,
        velocity: Vector3,
        acceleration: Vector3,
        outward: Boolean,
        horizon: Double,
    ): Double? {
        require(horizon.isFinite() && horizon >= 0.0)
        val boundary = if (outward) FlatPlanetDefinition.ASCENT_HEIGHT else FlatPlanetDefinition.DESCENT_HEIGHT
        val sign = if (outward) -1.0 else 1.0

        fun point(t: Double) = position + velocity * t + acceleration * (t * t * 0.5)

        fun gap(t: Double) = (distance(point(t)) - boundary) * sign
        var visits = 0

        fun search(
            left: Double,
            right: Double,
            leftGap: Double,
        ): Double? {
            if (++visits > 1024) return null
            if (leftGap <= 1e-8) return left
            val dt = right - left
            // Distance to a closed volume is 1-Lipschitz. No possible crossing can be pruned here.
            val reach = (velocity + acceleration * left).length() * dt + acceleration.length() * dt * dt * 0.5
            if (leftGap > reach + 1e-8) return null
            if (dt <= 1e-5) return if (gap(right) <= 1e-8) right else null
            val middle = (left + right) * 0.5
            return search(left, middle, leftGap) ?: search(middle, right, gap(middle))
        }
        return search(0.0, horizon, gap(0.0))
    }
}
