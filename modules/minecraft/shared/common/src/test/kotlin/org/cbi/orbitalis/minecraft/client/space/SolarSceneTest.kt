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
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarSceneTest {
    @Test fun `solar position follows authoritative fractional ephemeris in Earth axes`() {
        val profile = SolarSystemProfile(11.0, earthPhase = 0.7, selectedEarthWidth = 10000)
        val observer = Vector3(4000.0, 9000.0, -3000.0)
        val tick = 12000L
        val seconds = -0.025
        val sphere = SolarScene.sample(profile, tick, seconds, observer)
        val expected =
            profile.system
                .sample(tick, seconds)
                .frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH)
                .origin.position
        assertTrue((sphere.centre * sphere.radius + observer - expected).length() < 1e-7)
        assertTrue((sphere.centre - SolarScene.sample(profile, tick, 0.0, observer).centre).length() > 1e-6)
    }

    @Test fun `solar angular diameter remains consistent across system sizes`() {
        for (width in listOf(1000, 2000, 5000, 10000, 20000)) {
            val profile = SolarSystemProfile(11.0, selectedEarthWidth = width)
            val sphere = SolarScene.sample(profile, 0L, 0.0, Vector3.ZERO)
            val diameter = 2.0 * asin(1.0 / sphere.centre.length()) * 180.0 / PI
            assertTrue(diameter in 0.53..0.54)
            assertEquals(profile.sunRadius, sphere.radius)
        }
    }

    @Test fun `close solar views subtract large positions before float conversion`() {
        val profile = SolarSystemProfile(11.0, selectedEarthWidth = 10000)
        val sun =
            profile.system
                .sample(0L)
                .frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH)
                .origin.position
        val observer = sun + Vector3(0.125, 0.0, 3.0 * profile.sunRadius)
        val sphere = SolarScene.sample(profile, 0L, 0.0, observer)
        assertEquals(-0.125 / sphere.radius, sphere.centre.x, 1e-12)
        assertEquals(-3.0, sphere.centre.z, 1e-12)
        assertTrue(2.0 * asin(1.0 / sphere.centre.length()) > 0.67)
    }

    @Test fun `animated pattern is continuous across clock wrap and long epochs`() {
        val profile = SolarSystemProfile(11.0)
        val before = SolarScene.sample(profile, 23999L, 0.049999, Vector3.ZERO).phase
        val after = SolarScene.sample(profile, 24000L, 0.0, Vector3.ZERO).phase
        assertEquals(sin(before), sin(after), 1e-7)
        assertEquals(cos(before), cos(after), 1e-7)
        assertTrue(SolarScene.sample(profile, Long.MAX_VALUE, 0.025, Vector3.ZERO).phase.isFinite())
    }
}
