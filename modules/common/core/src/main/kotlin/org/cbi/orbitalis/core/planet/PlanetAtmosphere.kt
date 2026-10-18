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

/** Compact atmosphere over the playable top; its drag reaches zero at the ascent boundary. */
object PlanetAtmosphere {
    fun vacuum(
        position: Vector3,
        geometry: CelestialGeometry = CelestialGeometry(),
    ): Double {
        fun fade(t: Double): Double {
            val s = t.coerceIn(0.0, 1.0)
            return 1.0 - s * s * s * (10.0 + s * (-15.0 + 6.0 * s))
        }
        val upper = fade((position.y - FlatPlanetDefinition.SEA_LEVEL - 256.0) / (geometry.earthEnvelope.ascentDistance - 256.0))
        val lower = fade((-position.y - FlatPlanetDefinition.SEA_LEVEL) / 256.0)
        val sides =
            fade((abs(position.x) - geometry.earthHalfSize) / 256.0) *
                fade((abs(position.z) - geometry.earthHalfSize) / 256.0)
        return 1.0 - upper * lower * sides
    }
}
