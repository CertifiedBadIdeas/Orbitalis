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

package org.cbi.orbitalis.core.flight

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Flight guidance: inertial acceleration requests, with no world or engine ownership. */
object OrbitalGuidance {
    const val MAX_ACCELERATION = 3.0

    fun limited(
        value: Vector3,
        maximum: Double,
    ): Vector3 {
        val length = value.length()
        return if (length > maximum) value * (maximum / length) else value
    }

    fun point(
        state: CartesianState,
        target: Vector3,
        maximumSpeed: Double,
    ): Vector3 {
        val error = target - state.position
        // Conservative stopping distance leaves margin for engine fade and discrete sampling.
        val speed = min(maximumSpeed, sqrt(2 * 1.5 * error.length()))
        val desired = limited(error * 0.2, speed)
        return limited(desired - state.velocity, MAX_ACCELERATION)
    }

    /** Shared physical landing profile on both sides of a dimension handoff. */
    fun descent(
        state: CartesianState,
        launch: Vector3,
    ): Vector3 {
        val clearance = state.position.y - launch.y
        val maximumSpeed =
            when {
                clearance < 5 -> 0.4
                clearance < 40 -> 3.0
                else -> 80.0
            }
        return point(state, launch + Vector3(0.0, -0.08, 0.0), maximumSpeed)
    }

    fun angleStep(
        previous: Double,
        current: Double,
    ): Double = atan2(sin(current - previous), cos(current - previous))
}
