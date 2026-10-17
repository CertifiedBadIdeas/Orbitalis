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

/** Uniform spherical body: inverse-square exterior and a continuous, nonsingular interior. */
class SphericalGravity(
    val radius: Double,
    val surfaceAcceleration: Double,
) {
    init {
        require(radius.isFinite() && radius > 0.0) { "Radius must be finite and positive" }
        require(surfaceAcceleration.isFinite() && surfaceAcceleration > 0.0) { "Surface gravity must be finite and positive" }
    }

    fun altitude(position: Vector3): Double = position.length() - radius

    /** Specific potential, zero at infinity; continuous through the surface and centre. */
    fun potential(position: Vector3): Double {
        val distance = position.length()
        return if (distance < radius) {
            surfaceAcceleration * radius * ((distance / radius) * (distance / radius) - 3.0) / 2.0
        } else {
            -surfaceAcceleration * radius * (radius / distance)
        }
    }

    private val exterior = TwoBodyGravity(surfaceAcceleration * radius * radius)

    /** Position relative to the centre [m]; acceleration in the same inertial axes [m/s²]. */
    fun acceleration(position: Vector3): Vector3 =
        if (position.length() < radius) {
            position * (-surfaceAcceleration / radius)
        } else {
            exterior.acceleration(position)
        }
}
