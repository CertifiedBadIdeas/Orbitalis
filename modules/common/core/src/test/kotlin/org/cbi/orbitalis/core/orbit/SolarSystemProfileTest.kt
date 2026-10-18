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
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SolarSystemProfileTest {
    @Test
    fun `compact lunar period follows native Earth mass and the accepted distance`() {
        val profile = SolarSystemProfile(11.0)
        assertEquals(FlatPlanetDefinition.gravity(11.0).body.mu, profile.moonOrbit.mu)
        val sample = profile.system.sample(0).frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
        assertEquals(20000.0, sample.origin.position.length(), 1e-8)
        assertEquals(sqrt(profile.moonOrbit.mu / 20000.0), sample.origin.velocity.length(), 1e-10)
        assertTrue(profile.moonOrbit.period in 1300.0..1400.0)
    }

    @Test
    fun `retuning gravity preserves current phase and positions while changing velocity consistently`() {
        val before = SolarSystemProfile(11.0, 100, 0.7, -1.3)
        val tick = 10000L
        val after = before.retune(22.0, tick)
        assertEquals(tick, after.epochTick)
        for ((body, reference) in listOf(
            SolarSystemProfile.EARTH to SolarSystemProfile.SUN,
            SolarSystemProfile.MOON to SolarSystemProfile.EARTH,
        )) {
            val a = before.system.sample(tick).frame(body, reference)
            val b = after.system.sample(tick).frame(body, reference)
            assertTrue((a.origin.position - b.origin.position).length() < 1e-8)
            assertTrue((a.origin.velocity * sqrt(2.0) - b.origin.velocity).length() < 1e-9)
        }
    }

    @Test
    fun `ellipse curve uses the same focus orientation and apsides as the ephemeris`() {
        val rotation = Rotation3.axisAngle(Vector3(1.0, 2.0, 3.0), 0.8)
        val orbit = KeplerOrbit(1e8, 20000.0, 0.4, rotation, epochTick = 100)
        val curve = orbit.curve(128)
        assertEquals(128, curve.size)
        assertEquals(orbit.sample(100).origin.position, curve.first())
        for (point in curve) {
            val local = rotation.inverse(point)
            val x = local.x / orbit.semiMajorAxis + orbit.eccentricity
            val y = local.y / (orbit.semiMajorAxis * sqrt(1.0 - orbit.eccentricity * orbit.eccentricity))
            assertEquals(1.0, x * x + y * y, 1e-12)
            assertEquals(0.0, local.z, 1e-10)
        }
        for (bad in listOf(0, 15, 257, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> { orbit.curve(bad) }
    }

    @Test
    fun `profile rejects unbounded gravity phases and invalid epochs`() {
        for (bad in listOf(0.0, -1.0, 1e7, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { SolarSystemProfile(bad) }
        }
        for (bad in listOf(PI + 1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { SolarSystemProfile(11.0, earthPhase = bad) }
            assertFailsWith<IllegalArgumentException> { SolarSystemProfile(11.0, moonPhase = bad) }
        }
        assertFailsWith<IllegalArgumentException> { SolarSystemProfile(11.0, -1) }
        assertFailsWith<IllegalArgumentException> { SolarSystemProfile(11.0, 100).retune(12.0, 99) }
    }
}
