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

package org.cbi.orbitalis.minecraft.client.space

import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.client.map.CelestialMapScene
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.joml.Matrix4f

/** Shared depth domain and physical ordering, including ties in finite sky-buffer precision. */
object CelestialBodiesRenderer {
    fun render(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double,
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        includeEarth: Boolean = true,
        includeSun: Boolean = true,
        excludeBody: String? = null,
        earthEffects: Boolean = false,
    ) {
        var updatedInner = false
        val zAxis = Vector3(view.m02().toDouble(), view.m12().toDouble(), view.m22().toDouble())
        val bodies =
            CelestialMapScene
                .sample(
                    profile,
                    tick,
                    seconds,
                    includeOrbits = false,
                ).sortedBy { (it.position - observer).dot(zAxis) }
        for (body in bodies) {
            if (body.id == excludeBody) continue
            when (body.id) {
                SolarSystemProfile.SUN -> {
                    if (includeSun) SolarRenderer.render(profile, tick, seconds, observer, view, projection, scale)
                }

                SolarSystemProfile.EARTH -> {
                    if (includeEarth) {
                        SurfacePlanetRenderer.renderScene(
                            observer,
                            view,
                            projection,
                            scale,
                            clouds = earthEffects,
                            limb = earthEffects,
                        )
                    }
                }

                SolarSystemProfile.MOON -> {
                    LunarPlanetRenderer.render(profile, tick, CelestialFlight.lunarSeed, observer, view, projection, scale, seconds)
                }

                else -> {
                    InnerPlanetRenderer.render(
                        profile,
                        tick,
                        CelestialFlight.innerPlanetSeeds,
                        observer,
                        view,
                        projection,
                        scale,
                        seconds,
                        includeBody = body.id,
                        updateMeshes = !updatedInner,
                    )
                    updatedInner = true
                }
            }
        }
    }
}
