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

package org.cbi.orbitalis.minecraft.flight

import org.cbi.orbitalis.core.planet.PlanetAtmosphere
import org.cbi.orbitalis.core.planet.Vector3

/** Presentation blends over the upper surface and around the existing lateral/lower atmosphere. */
object FlightAtmosphere {
    const val START = FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT * 0.1
    const val END = FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT * 0.75

    fun vacuum(
        position: Vector3,
        geometry: org.cbi.orbitalis.core.planet.CelestialGeometry =
            org.cbi.orbitalis.core.planet
                .CelestialGeometry(),
    ): Float {
        val lateral = PlanetAtmosphere.vacuum(Vector3(position.x, minOf(position.y, FlatPlanetSpace.SEA_LEVEL), position.z), geometry)
        return (1.0 - (1.0 - vacuum(position.y, geometry)) * (1.0 - lateral)).toFloat()
    }

    fun vacuum(
        height: Double,
        geometry: org.cbi.orbitalis.core.planet.CelestialGeometry =
            org.cbi.orbitalis.core.planet
                .CelestialGeometry(),
    ): Float {
        if (!height.isFinite()) return 0f
        val start = FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.ascentDistance * 0.1
        val end = FlatPlanetSpace.SEA_LEVEL + geometry.earthEnvelope.descentDistance * 0.75
        val t = ((height - start) / (end - start)).coerceIn(0.0, 1.0)
        return (t * t * (3.0 - 2.0 * t)).toFloat()
    }
}
