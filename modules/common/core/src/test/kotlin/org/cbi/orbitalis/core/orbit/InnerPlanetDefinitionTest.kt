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
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InnerPlanetDefinitionTest {
    @Test
    fun `selected sizes preserve gravity and calendar periods and scale geometry`() {
        for (width in listOf(1000, 10000, 20000)) {
            val p = SolarSystemProfile(11.0, selectedEarthWidth = width)
            assertEquals(3, p.version)
            assertEquals(width * 0.383, p.mercury.width)
            assertEquals(width * 0.949, p.venus.width)
            assertEquals(11.0 * 3.70 / 9.81, p.mercury.surfaceGravity, 1e-14)
            assertEquals(11.0 * 8.87 / 9.81, p.venus.surfaceGravity, 1e-14)
            for ((body, days) in listOf(p.mercury to 87.969, p.venus to 224.701)) {
                assertEquals(days * 1200.0, body.orbit.period, 1e-8)
                assertEquals(p.sunMu, body.orbit.mu)
                assertTrue(body.orbitRadius < p.earthOrbitRadius)
                assertTrue(body.hillRadius > body.halfSize)
                val g = body.gravity.acceleration(Vector3(0.0, 64.0, 0.0))
                assertEquals(-body.surfaceGravity, g.y, 1e-10)
                assertEquals(0.0, g.x, 1e-10)
                assertTrue(body.envelope.ascentDistance > body.envelope.descentDistance)
            }
        }
    }

    @Test
    fun `catalogue places inner planets around Sun and preserves relative Earth subtraction`() {
        val p = SolarSystemProfile(11.0)
        val tick = 567890L
        val snapshot = p.system.sample(tick, 0.02)
        val earth = snapshot.frame(SolarSystemProfile.EARTH, SolarSystemProfile.SUN)
        for (body in p.innerPlanets) {
            val heliocentric = snapshot.frame(body.id, SolarSystemProfile.SUN)
            assertEquals(body.orbitRadius, heliocentric.origin.position.length(), 1e-8)
            assertEquals(sqrt(p.sunMu / body.orbitRadius), heliocentric.origin.velocity.length(), 1e-10)
            val geocentric = snapshot.frame(body.id, SolarSystemProfile.EARTH)
            vector(heliocentric.origin.position - earth.origin.position, geocentric.origin.position, 1e-8)
            vector(heliocentric.origin.velocity - earth.origin.velocity, geocentric.origin.velocity)
            vector(heliocentric.originAcceleration - earth.originAcceleration, geocentric.originAcceleration)
        }
    }

    @Test
    fun `surface frames rotate opposite ways and transport position velocity and acceleration`() {
        val p = SolarSystemProfile(11.0)
        val axis = Vector3(0.0, 0.0, 1.0)
        for (body in p.innerPlanets) {
            val quarter = kotlin.math.abs(body.spinPeriodSeconds) / 4.0
            val frame = p.innerPlanetSurfaceFrame(body.id, 0, quarter)
            val direction = if (body.spinPeriodSeconds > 0) -1.0 else 1.0
            vector(Vector3(direction, 0.0, 0.0), frame.orientation.apply(Vector3(0.0, 1.0, 0.0)))
            vector(axis * (2.0 * PI / body.spinPeriodSeconds), frame.angularVelocity)
            val local = CartesianState(Vector3(100.0, 80.0, -40.0), Vector3(3.0, 2.0, 1.0))
            val parent = frame.toParent(local)
            val restored = frame.fromParent(parent)
            vector(local.position, restored.position, 1e-8)
            vector(local.velocity, restored.velocity)
            val acceleration = Vector3(0.01, -0.2, 0.03)
            vector(acceleration, frame.fromParentAcceleration(parent, frame.toParentAcceleration(local, acceleration)))
            val zero = p.innerPlanetSurfaceFrame(body.id, 0)
            vector(Vector3(0.0, 1.0, 0.0), zero.orientation.apply(Vector3(0.0, 1.0, 0.0)))
        }
    }

    @Test
    fun `retuning profile epoch preserves derived planet spin and orbit phases`() {
        val before = SolarSystemProfile(11.0, epochTick = 100, selectedEarthWidth = 10000)
        val tick = 1000000L
        val after = before.retune(22.0, tick)
        for ((a, b) in before.innerPlanets.zip(after.innerPlanets)) {
            assertEquals(a.orbit.meanAnomaly(tick), b.orbit.meanAnomaly(tick), 1e-14)
            vector(
                before.innerPlanetSurfaceFrame(a.id, tick).orientation.apply(Vector3(0.0, 1.0, 0.0)),
                after.innerPlanetSurfaceFrame(b.id, tick).orientation.apply(Vector3(0.0, 1.0, 0.0)),
            )
            assertEquals(a.orbitPeriodSeconds, b.orbitPeriodSeconds)
        }
    }

    private fun vector(
        expected: Vector3,
        actual: Vector3,
        tolerance: Double = 1e-9,
    ) {
        assertTrue((expected - actual).length() <= tolerance, "Expected $expected, actual $actual")
    }
}
