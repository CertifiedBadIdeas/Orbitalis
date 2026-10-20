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
    @Test fun `body close-ups see the top face at different spin phases`() {
        val profile = SolarSystemProfile(11.0, earthSpinEpochTick = 0L)
        for (tick in listOf(0L, 6000L, 12000L, 18000L, 260783L)) {
            val frames =
                listOf(
                    SolarSystemProfile.EARTH to profile.earthSurfaceFrame(tick),
                    SolarSystemProfile.MOON to profile.lunarSurfaceFrame(tick),
                ) + profile.innerPlanets.map { it.id to profile.innerPlanetSurfaceFrame(it.id, tick) }
            for ((id, frame) in frames) {
                val camera = MapCamera()
                CelestialMapScene.orientSurface(camera, profile, id, tick)
                assertEquals(kotlin.math.sin(0.85), camera.direction.dot(frame.orientation.yAxis), 1e-10)
                val focus = frame.origin.position
                camera.frame(focus, 15000.0, 600, 400)
                assertEquals(MapCamera.Point(300.0, 200.0), camera.project(focus, 600, 400))
                camera.rotate(21.0, -13.0)
                assertTrue(camera.direction.x.isFinite() && camera.direction.y.isFinite() && camera.direction.z.isFinite())
            }
        }
    }

    @Test fun `rendered Moon and focused map share the fractional surface frame epoch`() {
        val profile = SolarSystemProfile(11.0)
        val tick = 12000L
        val seconds = -0.025
        val frames =
            org.cbi.orbitalis.core.orbit
                .SurfaceFlightFrames(profile, tick, seconds)
        val scene = CelestialMapScene.sample(profile, tick, seconds)
        val moon = scene.single { it.id == SolarSystemProfile.MOON }
        assertEquals(profile.lunarSurfaceFrame(tick, seconds).origin.position, moon.position)
        val world = org.cbi.orbitalis.core.orbit.SurfaceFlightFrames.World.MOON
        val origin = frames.frame(world).origin.position
        assertEquals(moon.position, origin)
        val camera = MapCamera()
        CelestialMapScene.follow(camera, SolarSystemProfile.MOON, scene)
        assertEquals(moon.position, camera.focus)
        val native = Vector3(70.0, 800.0, -50.0)
        val root =
            frames
                .frame(world)
                .toParent(
                    org.cbi.orbitalis.core.planet
                        .CartesianState(native, Vector3.ZERO),
                ).position
        assertTrue((frames.frame(world).orientation.inverse(root - origin) - native).length() < 1e-9)
        assertTrue((moon.position - profile.lunarSurfaceFrame(tick).origin.position).length() > 0.1)
    }

    @Test
    fun `map body positions and orbit origins use the authoritative snapshot epoch`() {
        val profile = SolarSystemProfile(11.0, 1000, 0.7, -1.1)
        val tick = 12000L
        val scene = CelestialMapScene.sample(profile, tick).associateBy { it.id }
        val snapshot = profile.system.sample(tick)
        assertEquals(
            setOf(
                SolarSystemProfile.SUN,
                SolarSystemProfile.EARTH,
                SolarSystemProfile.MOON,
                SolarSystemProfile.MERCURY,
                SolarSystemProfile.VENUS,
            ),
            scene.keys,
        )
        val sun = scene.getValue(SolarSystemProfile.SUN)
        val earth = scene.getValue(SolarSystemProfile.EARTH)
        val moon = scene.getValue(SolarSystemProfile.MOON)
        assertEquals(Vector3.ZERO, earth.position)
        assertEquals(snapshot.frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH).origin.position, sun.position)
        assertEquals(snapshot.frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH).origin.position, moon.position)
        for (planet in profile.innerPlanets) {
            val body = scene.getValue(planet.id)
            assertEquals(snapshot.frame(planet.id, SolarSystemProfile.EARTH).origin.position, body.position)
            assertTrue((body.orbit.first() - body.position).length() < 1e-7)
            assertTrue(body.orbit.all { kotlin.math.abs((it - sun.position).length() - planet.orbitRadius) < 1e-7 })
        }
        assertTrue(sun.orbit.isEmpty())
        assertTrue(earth.orbit.first().length() < 1e-8)
        assertTrue((moon.orbit.first() - moon.position).length() < 1e-8)
        assertTrue(earth.orbit.all { kotlin.math.abs((it - sun.position).length() - profile.earthOrbitRadius) < 1e-7 })
        assertTrue(moon.orbit.all { kotlin.math.abs(it.length() - profile.moonOrbitRadius) < 1e-7 })
    }

    @Test
    fun `framing follows a moving body and fits the Earth Moon scene at several aspects`() {
        val profile = SolarSystemProfile(11.0)
        val camera = MapCamera()
        for ((width, height) in listOf(144 to 321, 144 to 148, 600 to 400, 1000 to 300)) {
            camera.frame(Vector3.ZERO, profile.moonOrbitRadius * 1.3, width, height)
            val bodies = CelestialMapScene.sample(profile, 1200)
            for (body in bodies.filter { it.id == SolarSystemProfile.EARTH || it.id == SolarSystemProfile.MOON }) {
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

    @Test fun `Moon tracking keeps it fixed while Earth revolves and manual pan detaches`() {
        val profile = SolarSystemProfile(11.0)
        val camera = MapCamera()
        camera.frame(Vector3.ZERO, 30000.0)
        var previousEarth: MapCamera.Point? = null
        for (tick in listOf(0L, 2000L, 4000L, 6000L)) {
            val bodies = CelestialMapScene.sample(profile, tick)
            CelestialMapScene.follow(camera, SolarSystemProfile.MOON, bodies)
            val moon = camera.projectRaw(bodies.single { it.id == SolarSystemProfile.MOON }.position, 600, 400)
            assertEquals(MapCamera.Point(300.0, 200.0), moon)
            val earth = camera.projectRaw(Vector3.ZERO, 600, 400)
            previousEarth?.let { assertTrue(kotlin.math.hypot(earth.x - it.x, earth.y - it.y) > 1.0) }
            previousEarth = earth
        }
        camera.pan(10.0, 20.0, 400)
        val focus = camera.focus
        CelestialMapScene.follow(camera, null, CelestialMapScene.sample(profile, 8000L))
        assertEquals(focus, camera.focus)
    }
}
