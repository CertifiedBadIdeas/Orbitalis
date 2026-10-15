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

import kotlin.math.sqrt

/** Fixed central body, mu = G*M [m^3/s^2]. No atmosphere, rotation, terrain or other gravitating bodies. */
class TwoBodyGravity(
    val mu: Double,
) {
    init {
        require(mu.isFinite() && mu > 0.0) { "Gravitational parameter must be finite and positive" }
    }

    /** Central acceleration [m/s²] at an inertial position relative to the body [m]. */
    fun acceleration(position: Vector3): Vector3 {
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

    /** Distances from the central body [m], without subtracting a surface radius. */
    data class Apsides(
        val periapsis: Double,
        val apoapsis: Double,
    )

    /** Instantaneous Kepler ellipse; null for parabolic/hyperbolic escape. Radial fall has periapsis zero. */
    fun apsides(state: CartesianState): Apsides? {
        val energy = specificEnergy(state)
        if (energy >= 0.0) return null
        val momentum = state.position.cross(state.velocity)
        val semiLatusRectum = momentum.dot(momentum) / mu
        val eccentricity = sqrt((1.0 + 2.0 * energy * semiLatusRectum / mu).coerceIn(0.0, 1.0))
        val semiMajorAxis = -mu / (2.0 * energy)
        val periapsis = semiLatusRectum / (1.0 + eccentricity)
        val apoapsis = semiMajorAxis * (1.0 + eccentricity)
        require(periapsis.isFinite() && apoapsis.isFinite()) { "Apsides are outside the representable range" }
        return Apsides(periapsis, apoapsis)
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
