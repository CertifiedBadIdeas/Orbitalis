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

/** Fixed, nonrotating diagnostic frame. Translation changes position, never physical velocity. */
object OrbitalTestFrame {
    val origin = Vector3(0.0, 256.0, 0.0)

    fun toOrbit(position: Vector3): Vector3 = position - origin

    fun toSurface(position: Vector3): Vector3 = position + origin

    fun requirePatch(surfacePosition: Vector3) {
        require(abs(surfacePosition.x) <= 2048.0 && abs(surfacePosition.z) <= 2048.0) {
            "Outside the diagnostic surface patch (x/z +/-2048 m)"
        }
    }
}
