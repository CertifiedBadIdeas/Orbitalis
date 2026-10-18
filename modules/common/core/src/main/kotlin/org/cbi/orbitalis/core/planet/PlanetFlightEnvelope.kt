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
    val earth =
        BodyFlightEnvelope(
            Vector3(FlatPlanetDefinition.HALF_SIZE, FlatPlanetDefinition.SEA_LEVEL, FlatPlanetDefinition.HALF_SIZE),
            FlatPlanetDefinition.ASCENT_HEIGHT,
            FlatPlanetDefinition.DESCENT_HEIGHT,
        )
    val moon =
        BodyFlightEnvelope(
            Vector3(LunarSurface.HALF_SIZE.toDouble(), LunarSurface.SEA_LEVEL.toDouble(), LunarSurface.HALF_SIZE.toDouble()),
            LunarSurface.ASCENT_HEIGHT.toDouble(),
            LunarSurface.DESCENT_HEIGHT.toDouble(),
        )

    fun distance(position: Vector3): Double = earth.distance(position)

    fun arrival(
        position: Vector3,
        velocity: Vector3,
        acceleration: Vector3,
        outward: Boolean,
        horizon: Double,
    ): Double? = earth.arrival(position, velocity, acceleration, outward, horizon)
}

/** A body's rounded entry/exit bands, expressed in its own moving surface frame. */
class BodyFlightEnvelope(
    val halfExtents: Vector3,
    val ascentDistance: Double,
    val descentDistance: Double,
) {
    init {
        require(listOf(halfExtents.x, halfExtents.y, halfExtents.z).all { it.isFinite() && it > 0.0 })
        require(ascentDistance.isFinite() && descentDistance.isFinite() && ascentDistance > descentDistance && descentDistance > 0.0)
    }

    fun distance(position: Vector3): Double =
        Vector3(
            (abs(position.x) - halfExtents.x).coerceAtLeast(0.0),
            (abs(position.y) - halfExtents.y).coerceAtLeast(0.0),
            (abs(position.z) - halfExtents.z).coerceAtLeast(0.0),
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
        val boundary = if (outward) ascentDistance else descentDistance
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
