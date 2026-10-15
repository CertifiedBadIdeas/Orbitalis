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

/** Fixed central body, mu = G*M [m^3/s^2]. No atmosphere, rotation, terrain or other gravitating bodies. */
class TwoBodyGravity(
    val mu: Double,
) {
    init {
        require(mu.isFinite() && mu > 0.0) { "Gravitational parameter must be finite and positive" }
    }

    private fun acceleration(position: Vector3): Vector3 {
        val radius = position.length()
        require(radius.isFinite() && radius > 0.0) { "Gravity is singular at the origin" }
        return position.normalized() * (-mu / radius / radius)
    }

    /** Fixed-step velocity Verlet. Caller chooses timestep and handles collisions and frame handoffs. */
    fun step(
        state: CartesianState,
        seconds: Double,
    ): CartesianState {
        require(seconds.isFinite() && seconds > 0.0) { "Timestep must be finite and positive" }
        val halfVelocity = state.velocity + acceleration(state.position) * (seconds * 0.5)
        val position = state.position + halfVelocity * seconds
        val velocity = halfVelocity + acceleration(position) * (seconds * 0.5)
        return CartesianState(position, velocity)
    }

    /** Specific mechanical energy [m^2/s^2]. */
    fun specificEnergy(state: CartesianState): Double {
        val radius = state.position.length()
        require(radius.isFinite() && radius > 0.0) { "Energy is singular at the origin" }
        val energy = state.velocity.dot(state.velocity) * 0.5 - mu / radius
        require(energy.isFinite()) { "Energy is outside the representable range" }
        return energy
    }
}
