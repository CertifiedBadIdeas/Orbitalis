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
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CelestialMapSceneTest {
    @Test
    fun `map body positions and orbit origins use the authoritative snapshot epoch`() {
        val profile = SolarSystemProfile(11.0, 1000, 0.7, -1.1)
        val tick = 12000L
        val scene = CelestialMapScene.sample(profile, tick).associateBy { it.id }
        val snapshot = profile.system.sample(tick)
        assertEquals(setOf(SolarSystemProfile.SUN, SolarSystemProfile.EARTH, SolarSystemProfile.MOON), scene.keys)
        val sun = scene.getValue(SolarSystemProfile.SUN)
        val earth = scene.getValue(SolarSystemProfile.EARTH)
        val moon = scene.getValue(SolarSystemProfile.MOON)
        assertEquals(Vector3.ZERO, earth.position)
        assertEquals(snapshot.frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH).origin.position, sun.position)
        assertEquals(snapshot.frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH).origin.position, moon.position)
        assertTrue(sun.orbit.isEmpty())
        assertTrue(earth.orbit.first().length() < 1e-8)
        assertTrue((moon.orbit.first() - moon.position).length() < 1e-8)
        assertTrue(earth.orbit.all { kotlin.math.abs((it - sun.position).length() - SolarSystemProfile.EARTH_ORBIT_RADIUS) < 1e-7 })
        assertTrue(moon.orbit.all { kotlin.math.abs(it.length() - SolarSystemProfile.MOON_ORBIT_RADIUS) < 1e-7 })
    }

    @Test
    fun `framing follows a moving body and fits the Earth Moon scene at several aspects`() {
        val profile = SolarSystemProfile(11.0)
        val camera = MapCamera()
        for ((width, height) in listOf(144 to 321, 144 to 148, 600 to 400, 1000 to 300)) {
            camera.frame(Vector3.ZERO, SolarSystemProfile.MOON_ORBIT_RADIUS * 1.3, width, height)
            val bodies = CelestialMapScene.sample(profile, 1200)
            for (body in bodies.filter { it.id != SolarSystemProfile.SUN }) {
                assertTrue(camera.project(body.position, width, height) != null)
            }
            repeat(12) {
                camera.rotate(37.0, -15.0)
                for (point in bodies.single { it.id == SolarSystemProfile.MOON }.orbit) {
                    assertTrue(camera.project(point, width, height) != null)
                }
            }
            val moon = bodies.single { it.id == SolarSystemProfile.MOON }
            camera.frame(moon.position, moon.radius * 3.0, width, height)
            val point = checkNotNull(camera.project(moon.position, width, height))
            assertEquals(width / 2.0, point.x)
            assertEquals(height / 2.0, point.y)
        }
    }
}
