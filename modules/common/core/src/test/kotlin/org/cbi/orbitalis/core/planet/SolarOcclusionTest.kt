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

import org.cbi.orbitalis.core.orbit.Rotation3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarOcclusionTest {
    private val half = Vector3(1.0, 0.1, 1.0)
    private val sun = Vector3(0.0, 100.0, 0.0)

    @Test
    fun `finite cube gives umbra penumbra and full illumination`() {
        val shadow = SolarOcclusion(sun, 1.0, Rotation3.IDENTITY, half)
        assertEquals(0.0, shadow.visibility(Vector3(0.0, -10.0, 0.0)))
        assertTrue(shadow.visibility(Vector3(1.1, -10.0, 0.0)) in 0.01..0.99)
        assertEquals(1.0, shadow.visibility(Vector3(1.3, -10.0, 0.0)))
        assertEquals(1.0, shadow.visibility(Vector3(0.0, 10.0, 0.0)))
    }

    @Test
    fun `slab seen edge on casts a narrower shadow than its broad face`() {
        val broad = SolarOcclusion(sun, 2.0, Rotation3.IDENTITY, half)
        val edge = SolarOcclusion(Vector3(100.0, 0.0, 0.0), 2.0, Rotation3.IDENTITY, half)
        assertEquals(0.0, broad.visibility(Vector3(0.0, -10.0, 0.0)))
        assertTrue(edge.visibility(Vector3(-10.0, 0.0, 0.0)) in 0.01..0.99)
    }

    @Test
    fun `occluder behind the source or observer cannot eclipse it`() {
        val shadow = SolarOcclusion(sun, 1.0, Rotation3.IDENTITY, half)
        assertEquals(1.0, shadow.visibility(Vector3(0.0, 110.0, 0.0)))
        val farSun = Vector3(0.0, -100.0, 0.0)
        assertEquals(1.0, SolarOcclusion(farSun, 1.0, Rotation3.IDENTITY, half).visibility(Vector3(0.0, -10.0, 0.0)))
    }

    @Test
    fun `shadow is invariant under common scaling`() {
        val point = Vector3(1.1, -10.0, 0.0)
        val first = SolarOcclusion(sun, 1.0, Rotation3.IDENTITY, half).visibility(point)
        for (scale in listOf(0.001, 1000.0)) {
            assertEquals(first, SolarOcclusion(sun * scale, scale, Rotation3.IDENTITY, half * scale).visibility(point * scale))
        }
    }

    @Test
    fun `catalogue alignment eclipses the moon and quarter orbit does not`() {
        for (width in listOf(1000, 10000, 20000)) {
            for (phase in listOf(0.0, kotlin.math.PI / 2.0)) {
                val profile =
                    org.cbi.orbitalis.core.orbit.SolarSystemProfile(
                        11.0,
                        18000L,
                        0.0,
                        phase,
                        earthSpinEpochTick = 0L,
                        selectedEarthWidth = width,
                    )
                val earth = profile.earthSurfaceFrame(18000L).orientation
                val moon = profile.lunarSurfaceFrame(18000L).origin.position
                val sun =
                    profile.system
                        .sample(18000L)
                        .frame(
                            org.cbi.orbitalis.core.orbit.SolarSystemProfile.SUN,
                            org.cbi.orbitalis.core.orbit.SolarSystemProfile.EARTH,
                        ).origin.position
                val local = earth.inverse(moon)
                val shadow =
                    SolarOcclusion(
                        earth.inverse(sun),
                        profile.sunRadius,
                        Rotation3.fromAxes(
                            earth.inverse(Vector3(1.0, 0.0, 0.0)),
                            earth.inverse(Vector3(0.0, 1.0, 0.0)),
                            earth.inverse(Vector3(0.0, 0.0, 1.0)),
                        ),
                        Vector3(width / 2.0, FlatPlanetDefinition.SEA_LEVEL, width / 2.0),
                    )
                assertEquals(if (phase == 0.0) 0.0 else 1.0, shadow.visibility(local), "width=$width phase=$phase")
            }
        }
    }

    @Test
    fun `samples stay on the visible emitting faces after rotation`() {
        val rotation = Rotation3.axisAngle(Vector3(0.0, 0.0, 1.0), 0.7)
        val source = SolarOcclusion(sun, 2.0, rotation, half)
        val observer = Vector3(30.0, -10.0, 20.0)
        val facing = rotation.inverse(observer - sun)
        for (i in 0 until SolarOcclusion.SAMPLE_COUNT) {
            val p = rotation.inverse(source.sample(observer, i) - sun) * 0.5
            val extent = maxOf(kotlin.math.abs(p.x), kotlin.math.abs(p.y), kotlin.math.abs(p.z))
            assertEquals(1.0, extent, 1e-12)
            val front =
                (kotlin.math.abs(p.x) > 0.999 && p.x * facing.x > 0.0) ||
                    (kotlin.math.abs(p.y) > 0.999 && p.y * facing.y > 0.0) ||
                    (kotlin.math.abs(p.z) > 0.999 && p.z * facing.z > 0.0)
            assertTrue(front)
        }
    }

    @Test
    fun `source orientation remains continuous at the animation wrap`() {
        val a = SolarShape.orientation(23999L, 0.049999).apply(Vector3(1.0, 0.0, 0.0))
        val b = SolarShape.orientation(24000L, 0.0).apply(Vector3(1.0, 0.0, 0.0))
        assertTrue((a - b).length() < 1e-7)
    }

    @Test
    fun `emitter orientation is the inverse of the shader stellar coordinate chart`() {
        // solarCoordinates maps root to local by -2*phase; emitting axes map local to root.
        val axis = SolarShape.orientation(3000L, 0.0).apply(Vector3(1.0, 0.0, 0.0))
        assertEquals(0.0, axis.x, 1e-12)
        assertEquals(1.0, axis.y, 1e-12)
    }
}
