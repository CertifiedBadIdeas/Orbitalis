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

package org.cbi.orbitalis.minecraft.lighting

import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.OpaqueSolarBox
import org.cbi.orbitalis.core.planet.SolarEmitterCube
import org.cbi.orbitalis.core.planet.SolarShape
import org.cbi.orbitalis.core.planet.SolarVisibility
import org.cbi.orbitalis.core.planet.Vector3

/** Immutable geocentric sample, shared by all external-light queries for one client tick. */
class SolarLightScene(
    profile: SolarSystemProfile,
    tick: Long,
    seconds: Double = 0.0,
) {
    private val earth = profile.earthSurfaceFrame(tick, seconds)
    private val moon = profile.lunarSurfaceFrame(tick, seconds)
    private val field =
        SolarVisibility(
            SolarEmitterCube(SkyLighting.sunPosition(profile, tick, seconds), profile.sunRadius, SolarShape.orientation(tick, seconds)),
            buildList {
                addAll(
                    listOf(
                        OpaqueSolarBox(
                            earth.origin.position,
                            Vector3(profile.geometry.earthHalfSize, FlatPlanetDefinition.SEA_LEVEL, profile.geometry.earthHalfSize),
                            earth.orientation,
                        ),
                        OpaqueSolarBox(
                            moon.origin.position,
                            Vector3(profile.geometry.moonHalfSize, FlatPlanetDefinition.SEA_LEVEL, profile.geometry.moonHalfSize),
                            moon.orientation,
                        ),
                    ),
                )
                val snapshot = profile.system.sample(tick, seconds)
                for (planet in profile.innerPlanets) {
                    val frame = planet.surfaceFrame(snapshot, tick, seconds)
                    add(
                        OpaqueSolarBox(
                            frame.origin.position,
                            Vector3(planet.halfSize, FlatPlanetDefinition.SEA_LEVEL, planet.halfSize),
                            frame.orientation,
                        ),
                    )
                }
            },
        )

    fun visibility(rootPoint: Vector3): Double = field.visibility(rootPoint)
}
