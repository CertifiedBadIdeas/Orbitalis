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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CelestialGeometry
import org.cbi.orbitalis.core.planet.LunarSurface
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CelestialGeometryTest {
    @Test fun `legacy dimensions and gravity remain independent of new creation defaults`() {
        val legacy = SolarSystemProfile(11.0)
        val selected = SolarSystemProfile(11.0, selectedEarthWidth = 10000)
        assertEquals(5000.0, legacy.earthGravity.body.halfWidth)
        assertEquals(1400.0, legacy.moonGravity.halfWidth)
        assertEquals(11.0 / 6.0, legacy.moonGravity.surfaceAcceleration)
        assertEquals(SolarSystemProfile.VERSION, selected.version)
        assertEquals(2, legacy.version)
        assertEquals(1365.0, selected.moonGravity.halfWidth)
        assertEquals(11.0 * 0.165, selected.moonGravity.surfaceAcceleration)
        assertEquals(legacy.earthGravity.body.mu, selected.earthGravity.body.mu)
    }

    @Test fun `physical periods stay fixed as mass and system distances follow selected body dimensions`() {
        val full = SolarSystemProfile(11.0, selectedEarthWidth = 10000)
        val preview = SolarSystemProfile(11.0, selectedEarthWidth = 1000, earthSpinEpochTick = 0L)
        assertEquals(500.0, preview.earthGravity.body.halfWidth)
        assertEquals(136.5, preview.moonGravity.halfWidth)
        assertTrue(preview.earthGravity.body.mu < full.earthGravity.body.mu)
        assertTrue(preview.moonOrbitRadius < full.moonOrbitRadius)
        assertTrue(preview.moonOrbitRadius > full.moonOrbitRadius * 0.1)
        assertEquals(SolarSystemProfile.LUNAR_PERIOD_SECONDS, preview.moonOrbit.period, 1e-8)
        assertEquals(SolarSystemProfile.YEAR_SECONDS, preview.earthOrbit.period, 1e-7)
        assertEquals(1000, preview.retune(12.0, 1000).selectedEarthWidth)
        assertEquals(0L, preview.retune(12.0, 1000).earthSpinEpochTick)
        assertEquals(5000.0, full.earthGravity.body.halfWidth)
    }

    @Test fun `small body all face transfers retain minimum native approach clearance`() {
        val profile = SolarSystemProfile(11.0, selectedEarthWidth = 1000)
        val frames = SurfaceFlightFrames(profile, 0)
        for (world in listOf(SurfaceFlightFrames.World.EARTH, SurfaceFlightFrames.World.MOON)) {
            val e = if (world == SurfaceFlightFrames.World.EARTH) profile.geometry.earthEnvelope else profile.geometry.moonEnvelope
            assertTrue(e.descentDistance >= 1500.0 && e.ascentDistance >= e.descentDistance + 300.0)
            for (p in listOf(
                Vector3(e.halfExtents.x + e.ascentDistance + 1.0, 0.0, 0.0),
                Vector3(
                    0.0,
                    -e.halfExtents.y - e.ascentDistance - 1.0,
                    0.0,
                ),
            )) {
                assertEquals(SurfaceFlightFrames.World.SPACE, frames.destination(CartesianState(p, Vector3.ZERO), world))
            }
        }
    }

    @Test fun `native lunar footprint samples selected width without changing seeded geology`() {
        val small = LunarSurface(1234, 136.5)
        val old = LunarSurface(1234)
        assertEquals(273, (-200..200).count { small.contains(it, 0) })
        assertEquals(old.height(0, 0), small.height(0, 0))
        assertTrue(small.height(137, 0) == null && small.height(-137, 0) == null)
        assertTrue(old.height(137, 0) != null)
        assertFailsWith<IllegalArgumentException> { CelestialGeometry(999) }
        assertFailsWith<IllegalArgumentException> { CelestialGeometry(20001) }
    }
}
