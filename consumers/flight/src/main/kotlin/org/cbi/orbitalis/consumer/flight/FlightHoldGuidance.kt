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
import org.cbi.orbitalis.core.planet.Vector3

/** Disposable stand guidance: requests accelerations; never integrates or applies forces. */
internal object FlightHoldGuidance {
    data class Demand(
        val thrustAcceleration: Vector3,
        val angularAcceleration: Vector3,
    )

    fun command(
        state: CartesianState,
        target: Vector3,
        gravity: Vector3,
        attitudeError: Vector3,
        angularVelocity: Vector3,
    ): Demand {
        val velocity = limited((target - state.position) * 0.3, 12.0)
        return Demand(
            limited((velocity - state.velocity) * 0.8, 4.0) - gravity,
            limited(attitudeError - angularVelocity * 1.6, 0.8),
        )
    }

    private fun limited(
        value: Vector3,
        maximum: Double,
    ): Vector3 {
        val length = value.length()
        return if (length > maximum) value * (maximum / length) else value
    }
}
