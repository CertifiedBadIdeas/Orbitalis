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

import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarCalendarTest {
    @Test fun `calendar periods determine distances without changing sizes or masses`() {
        for (g in listOf(1.0, 9.81, 11.0, 22.0, 1000.0)) {
            val profile = SolarSystemProfile(g)
            val legacy = profile.copy(preset = SolarSystemProfile.Preset.COMPACT)
            assertEquals(FlatPlanetDefinition.gravity(g).body.mu, profile.earthGravity.body.mu)
            assertEquals(legacy.moonGravity.mu, profile.moonGravity.mu)
            assertEquals(legacy.sunMu, profile.sunMu)
            assertEquals(legacy.earthGravity.body.halfWidth, profile.earthGravity.body.halfWidth)
            assertEquals(legacy.moonGravity.halfWidth, profile.moonGravity.halfWidth)
            assertEquals(438288.0, profile.earthOrbit.period, 1e-8)
            assertEquals(32784.0, profile.moonOrbit.period, 1e-8)
            assertEquals(profile.earthGravity.body.mu, profile.moonOrbit.mu)
            assertEquals(profile.sunMu, profile.earthOrbit.mu)
        }
        val profile = SolarSystemProfile(11.0)
        assertEquals(168941.6459689473, profile.moonOrbitRadius, 1e-6)
        assertEquals(20502263.640696827, profile.earthOrbitRadius, 1e-5)
    }

    @Test fun `solar spin beat and lunar phases follow one physical calendar`() {
        val day = SolarSystemProfile.SOLAR_DAY_SECONDS
        val spin = 2 * PI / SolarSystemProfile.SIDEREAL_DAY_SECONDS
        val year = 2 * PI / SolarSystemProfile.YEAR_SECONDS
        assertEquals(2 * PI, (spin - year) * day, 1e-12)
        assertEquals(1196.7234600262123, SolarSystemProfile.SIDEREAL_DAY_SECONDS, 1e-10)
        val profile = SolarSystemProfile(11.0)
        val month = SolarSystemProfile.SYNODIC_MONTH_SECONDS
        assertEquals(29.52875473484848, month / day, 1e-12)
        assertEquals(2 * PI, (2 * PI / profile.moonOrbit.period - year) * month, 1e-12)
        for (seconds in listOf(0.0, 32784.0 / 4, month, 438288.0)) {
            val frame = profile.lunarSurfaceFrame(0, seconds)
            val up = frame.orientation.yAxis
            assertTrue(up.dot(frame.origin.position.normalized()) < -1.0 + 1e-12)
        }
    }

    @Test fun `old preset migration preserves phases and clock while moving to the new radii`() {
        val old = SolarSystemProfile(11.0, 100, 0.7, -1.3, SolarSystemProfile.Preset.COMPACT)
        val tick = 12345L
        val new = old.retune(11.0, tick, SolarSystemProfile.Preset.CALENDAR)
        assertEquals(tick, new.epochTick)
        assertEquals(old.earthOrbit.meanAnomaly(tick), new.earthOrbit.meanAnomaly(tick), 1e-12)
        assertEquals(old.moonOrbit.meanAnomaly(tick), new.moonOrbit.meanAnomaly(tick), 1e-12)
        assertEquals(20000.0, old.moonOrbitRadius)
        assertTrue(new.moonOrbitRadius > 160000)
        val retuned = new.retune(22.0, tick + 200)
        assertEquals(new.earthOrbit.period, retuned.earthOrbit.period, 1e-8)
        assertEquals(new.moonOrbit.period, retuned.moonOrbit.period, 1e-8)
        assertEquals(cbrt(2.0), retuned.moonOrbitRadius / new.moonOrbitRadius, 1e-12)
    }
}
