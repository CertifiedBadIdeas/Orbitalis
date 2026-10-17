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

/** Shared physical footprint and size-proportional flight bands, in metres. */
object FlatPlanetDefinition {
    const val SIZE = 10000.0
    const val SCALE = SIZE / 2000.0
    const val HALF_SIZE = SIZE / 2.0
    const val SEA_LEVEL = 64.0
    const val ASCENT_HEIGHT = 600.0 * SCALE
    const val DESCENT_HEIGHT = 400.0 * SCALE
    const val ORBIT_HEIGHT = 1000.0 * SCALE

    fun gravity(surfaceAcceleration: Double): SurfaceGravityLayer =
        SurfaceGravityLayer(
            RectangularGravity(HALF_SIZE, SEA_LEVEL, HALF_SIZE, surfaceAcceleration),
            surfaceAcceleration,
            fullHeight = ASCENT_HEIGHT,
        )
}
