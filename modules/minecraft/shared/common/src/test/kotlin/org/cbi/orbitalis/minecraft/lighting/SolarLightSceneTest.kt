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
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals

class SolarLightSceneTest {
    private fun exitDistance(
        direction: Vector3,
        half: Double,
    ): Double =
        minOf(
            half / kotlin.math.abs(direction.x),
            64.0 / kotlin.math.abs(direction.y),
            half / kotlin.math.abs(direction.z),
        )

    @Test fun `Earth and Moon each block sunlight behind their actual rotating slab`() {
        for (width in listOf(1000, 10000, 20000)) {
            val profile = SolarSystemProfile(11.0, selectedEarthWidth = width, earthSpinEpochTick = 0L)
            for (tick in listOf(0L, 6000L, 12000L, 250000L)) {
                val scene = SolarLightScene(profile, tick)
                val sun = SkyLighting.sunPosition(profile, tick)
                val toward = sun.normalized()
                assertEquals(1.0, scene.visibility(toward * (width * 3.0)))
                val earthDirection = profile.earthSurfaceFrame(tick).orientation.inverse(toward)
                val earthExit = exitDistance(earthDirection, width / 2.0)
                assertEquals(0.0, scene.visibility(toward * -(earthExit + 5.0)))
                val moon = profile.lunarSurfaceFrame(tick).origin.position
                val fromMoon = (sun - moon).normalized()
                val moonDirection = profile.lunarSurfaceFrame(tick).orientation.inverse(fromMoon)
                val moonExit = exitDistance(moonDirection, profile.geometry.moonHalfSize)
                assertEquals(0.0, scene.visibility(moon - fromMoon * (moonExit + 5.0)))
            }
        }
    }

    @Test fun `Mercury and Venus participate in the same visibility field`() {
        val profile = SolarSystemProfile(11.0, selectedEarthWidth = 10000)
        for (tick in listOf(0L, 6000L, 1000000L)) {
            val scene = SolarLightScene(profile, tick)
            val snapshot = profile.system.sample(tick)
            val sun = SkyLighting.sunPosition(profile, tick)
            for (planet in profile.innerPlanets) {
                val centre = planet.surfaceFrame(snapshot, tick).origin.position
                val toward = (sun - centre).normalized()
                // Just outside the containing box, on opposite sides of the emitter direction.
                val radius = Vector3(planet.halfSize, 64.0, planet.halfSize).length() + 10.0
                assertEquals(1.0, scene.visibility(centre + toward * radius))
                val local = planet.surfaceFrame(snapshot, tick).orientation.inverse(toward)
                assertEquals(0.0, scene.visibility(centre - toward * (exitDistance(local, planet.halfSize) + 5.0)))
            }
        }
    }
}
