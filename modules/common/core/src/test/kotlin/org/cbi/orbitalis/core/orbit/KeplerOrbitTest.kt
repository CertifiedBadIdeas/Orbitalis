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

import org.cbi.orbitalis.core.planet.TwoBodyGravity
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeplerOrbitTest {
    // Period is exactly 400 seconds analytically, giving quarter-turn samples on whole ticks.
    private val radius = 20000.0
    private val mu = radius * radius * radius * (2.0 * PI / 400.0) * (2.0 * PI / 400.0)

    @Test
    fun `circular ephemeris returns position velocity acceleration and closes after one period`() {
        val orbit = KeplerOrbit(mu, radius)
        assertEquals(400.0, orbit.period, 1e-10)
        val start = orbit.sample(0)
        val quarter = orbit.sample(2000)
        val speed = sqrt(mu / radius)
        assertVector(Vector3(radius, 0.0, 0.0), start.origin.position)
        assertVector(Vector3(0.0, speed, 0.0), start.origin.velocity)
        assertVector(Vector3(-mu / radius / radius, 0.0, 0.0), start.originAcceleration)
        assertVector(Vector3(0.0, radius, 0.0), quarter.origin.position)
        assertVector(Vector3(-speed, 0.0, 0.0), quarter.origin.velocity)
        assertVector(start.origin.position, orbit.sample(8000).origin.position)
        assertVector(start.origin.velocity, orbit.sample(8000).origin.velocity)
    }

    @Test
    fun `elliptic apsides and speed agree with vis viva in an inclined plane`() {
        val plane = Rotation3.axisAngle(Vector3(1.0, 2.0, 3.0), 0.9)
        val eccentricity = 0.6
        val orbit = KeplerOrbit(mu, radius, eccentricity, plane)
        val periapsis = orbit.sample(0)
        val apoapsis = orbit.sample(4000)
        assertVector(plane.apply(Vector3(radius * (1.0 - eccentricity), 0.0, 0.0)), periapsis.origin.position)
        assertVector(plane.apply(Vector3(-radius * (1.0 + eccentricity), 0.0, 0.0)), apoapsis.origin.position)
        val gravity = TwoBodyGravity(mu)
        val energy = -mu / (2.0 * radius)
        val momentum = sqrt(mu * radius * (1.0 - eccentricity * eccentricity))
        for (tick in 0L..8000L step 137) {
            val sample = orbit.sample(tick)
            assertEquals(energy, gravity.specificEnergy(sample.origin), 1e-9)
            assertEquals(
                momentum,
                sample.origin.position
                    .cross(sample.origin.velocity)
                    .length(),
                1e-7,
            )
            assertVector(gravity.acceleration(sample.origin.position), sample.originAcceleration)
        }
    }

    @Test
    fun `velocity and acceleration match independent central finite differences`() {
        val orbit = KeplerOrbit(mu, radius, 0.55, Rotation3.axisAngle(Vector3(1.0, 0.0, 0.0), 0.7), 0.4, 1000)
        val tick = 2137L
        val before = orbit.sample(tick - 1)
        val now = orbit.sample(tick)
        val after = orbit.sample(tick + 1)
        assertVector(now.origin.velocity, (after.origin.position - before.origin.position) * 10.0, 0.001)
        assertVector(now.originAcceleration, (after.origin.velocity - before.origin.velocity) * 10.0, 0.0001)
    }

    @Test
    fun `epoch phase works before the epoch and independent of sample order`() {
        val base = KeplerOrbit(mu, radius)
        val shifted = KeplerOrbit(mu, radius, meanAnomalyAtEpoch = PI / 2.0 + 8.0 * PI, epochTick = 2000)
        for (tick in listOf(0L, 7000L, 2000L, 3L, 8000L)) {
            assertVector(base.sample(tick).origin.position, shifted.sample(tick).origin.position)
            assertVector(base.sample(tick).origin.velocity, shifted.sample(tick).origin.velocity)
        }
        val lateEpoch = Long.MAX_VALUE - 8000
        val late = KeplerOrbit(mu, radius, epochTick = lateEpoch)
        assertEquals(base.sample(2000).origin, late.sample(lateEpoch + 2000).origin)
    }

    @Test
    fun `near parabolic bound orbits remain finite and satisfy the Kepler equation`() {
        val eccentricity = 0.999999
        for (mean in listOf(0.0, 1e-8, 0.01, 0.5, PI, -0.01)) {
            val orbit = KeplerOrbit(mu, radius, eccentricity, meanAnomalyAtEpoch = mean)
            val sample = orbit.sample(0)
            val c = sample.origin.position.x / radius + eccentricity
            val s = sample.origin.position.y / (radius * sqrt((1.0 - eccentricity) * (1.0 + eccentricity)))
            val anomaly = kotlin.math.atan2(s, c)
            assertEquals(mean, anomaly - eccentricity * kotlin.math.sin(anomaly), 2e-14)
            assertTrue(
                sample.origin.velocity
                    .length()
                    .isFinite(),
            )
            assertTrue(sample.originAcceleration.length().isFinite())
            val relativeEnergyError = abs(TwoBodyGravity(mu).specificEnergy(sample.origin) + mu / (2.0 * radius)) / (mu / radius)
            assertTrue(relativeEnergyError < 0.001)
        }
    }

    @Test
    fun `invalid and unrepresentable orbit parameters fail before sampling`() {
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { KeplerOrbit(bad, radius) }
            assertFailsWith<IllegalArgumentException> { KeplerOrbit(mu, bad) }
        }
        for (bad in listOf(-0.01, 1.0, 2.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { KeplerOrbit(mu, radius, bad) }
        }
        assertFailsWith<IllegalArgumentException> { KeplerOrbit(mu, radius, epochTick = -1) }
        assertFailsWith<IllegalArgumentException> { KeplerOrbit(mu, radius, meanAnomalyAtEpoch = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { KeplerOrbit(Double.MAX_VALUE, Double.MIN_VALUE) }
        assertFailsWith<IllegalArgumentException> { KeplerOrbit(Double.MIN_VALUE, Double.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { KeplerOrbit(mu, radius).sample(-1) }
    }

    private fun assertVector(
        expected: Vector3,
        actual: Vector3,
        tolerance: Double = 1e-7,
    ) {
        assertTrue((expected - actual).length() < tolerance, "Expected $expected, got $actual")
    }
}
