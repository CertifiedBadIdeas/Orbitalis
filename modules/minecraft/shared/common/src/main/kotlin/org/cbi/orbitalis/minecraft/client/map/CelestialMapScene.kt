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

package org.cbi.orbitalis.minecraft.client.map

import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.Vector3

/** Pure presentation sampled from the server profile/epoch; all coordinates are Earth-relative. */
object CelestialMapScene {
    data class Body(
        val id: String,
        val position: Vector3,
        val radius: Double,
        val orbit: List<Vector3> = emptyList(),
    )

    fun follow(
        camera: MapCamera,
        id: String?,
        bodies: List<Body>,
    ) {
        bodies.firstOrNull { it.id == id }?.let { camera.focus = it.position }
    }

    fun sample(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double = 0.0,
    ): List<Body> {
        val snapshot = profile.system.sample(tick, seconds)
        val sun = snapshot.frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH).origin.position
        val moon = snapshot.frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH).origin.position
        return listOf(
            Body(SolarSystemProfile.SUN, sun, SolarSystemProfile.SUN_VISUAL_RADIUS),
            Body(
                SolarSystemProfile.EARTH,
                Vector3.ZERO,
                FlatPlanetDefinition.HALF_SIZE,
                profile.earthOrbit.curve(tick = tick).map {
                    it +
                        sun
                },
            ),
            Body(SolarSystemProfile.MOON, moon, SolarSystemProfile.MOON_HALF_SIZE, profile.moonOrbit.curve(tick = tick)),
        )
    }
}
