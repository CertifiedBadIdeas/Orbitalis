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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.Vector3

/** Creative player input in the visible horizon; inertial transport velocity is never rotated. */
class FlightControlAxes private constructor(
    val up: Vector3,
    val forward: Vector3,
) {
    val left = up.cross(forward).normalized()

    fun acceleration(
        input: Vector3,
        speed: Double,
    ): Vector3 {
        val length = input.length()
        if (length < 1e-7) return Vector3.ZERO
        val value = if (length > 1.0) input * (1.0 / length) else input
        return (left * value.x + up * value.y + forward * value.z) * speed
    }

    companion object {
        fun fromView(
            up: Vector3,
            look: Vector3,
            screenUp: Vector3,
        ): FlightControlAxes {
            val vertical = up.normalized()

            fun project(value: Vector3) = value - vertical * value.dot(vertical)
            val projected = project(look)
            val heading =
                if (projected.length() > 1e-6) {
                    projected
                } else {
                    val fallback = project(screenUp) * if (look.dot(vertical) > 0.0) -1.0 else 1.0
                    if (fallback.length() > 1e-6) fallback else project(Vector3(0.0, 0.0, 1.0))
                }
            return FlightControlAxes(vertical, heading.normalized())
        }
    }
}
