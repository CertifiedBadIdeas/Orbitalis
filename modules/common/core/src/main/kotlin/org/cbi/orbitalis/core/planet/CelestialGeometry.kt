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

/** Immutable world geometry. Null selects the established pre-settings dimensions and lunar gravity. */
data class CelestialGeometry(
    val selectedEarthWidth: Int? = null,
) {
    init {
        require(selectedEarthWidth == null || selectedEarthWidth in 1000..20000)
    }

    val earthWidth = selectedEarthWidth?.toDouble() ?: FlatPlanetDefinition.SIZE
    val earthHalfSize = earthWidth / 2.0
    val moonHalfSize = if (selectedEarthWidth == null) LunarSurface.HALF_SIZE.toDouble() else earthWidth * 0.273 / 2.0
    val moonGravityRatio = if (selectedEarthWidth == null) 1.0 / 6.0 else 0.165
    val earthEnvelope =
        BodyFlightEnvelope(
            Vector3(earthHalfSize, FlatPlanetDefinition.SEA_LEVEL, earthHalfSize),
            maxOf(1800.0, earthWidth * 0.3),
            maxOf(1500.0, earthWidth * 0.2),
        )
    val moonEnvelope = BodyFlightEnvelope(Vector3(moonHalfSize, FlatPlanetDefinition.SEA_LEVEL, moonHalfSize), 1800.0, 1500.0)
}
