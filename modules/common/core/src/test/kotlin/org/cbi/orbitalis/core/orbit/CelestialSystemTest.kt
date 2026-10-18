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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CelestialSystemTest {
    private val sun = CelestialBody("test:sun", null, 1e12)
    private val earth = CelestialBody("test:earth", sun.id, 1.8e8, KeplerOrbit(sun.mu, 2e6, 0.05))
    private val moon = CelestialBody("test:moon", earth.id, 2e6, KeplerOrbit(earth.mu, 20000.0, 0.03))

    @Test
    fun `three level hierarchy samples origins velocities and acceleration at the same time`() {
        val system = CelestialSystem(listOf(moon, sun, earth))
        val tick = 12345L
        val snapshot = system.sample(tick)
        val a = earth.orbit!!.sample(tick)
        val b = moon.orbit!!.sample(tick)
        val result = snapshot.frame(moon.id, sun.id)
        assertEquals(tick, snapshot.tick)
        assertVector(a.origin.position + b.origin.position, result.origin.position)
        assertVector(a.origin.velocity + b.origin.velocity, result.origin.velocity)
        assertVector(a.originAcceleration + b.originAcceleration, result.originAcceleration)
        val local = snapshot.frame(moon.id, earth.id)
        assertEquals(b.origin, local.origin)
        assertVector(b.originAcceleration, local.originAcceleration)
        assertVector(b.origin.position * -1.0, snapshot.frame(earth.id, moon.id).origin.position)
        assertEquals(CartesianState(Vector3.ZERO, Vector3.ZERO), snapshot.frame(moon.id, moon.id).origin)
    }

    @Test
    fun `distant ancestor cancels before local arithmetic loses surface precision`() {
        val distant = earth.copy(orbit = KeplerOrbit(sun.mu, 1e18))
        val tinyMoon = moon.copy(orbit = KeplerOrbit(earth.mu, 1.0, meanAnomalyAtEpoch = PI / 2))
        val system = CelestialSystem(listOf(sun, distant, tinyMoon))
        val snapshot = system.sample(0)
        val relative = snapshot.frame(moon.id, earth.id)
        assertEquals(tinyMoon.orbit!!.sample(0).origin, relative.origin)
        val vessel = CartesianState(Vector3(0.125, 0.25, -0.5), Vector3(1.0, 2.0, 3.0))
        val parent = relative.toParent(vessel)
        val recovered = relative.fromParent(parent)
        assertVector(vessel.position, recovered.position, 1e-12)
        assertVector(vessel.velocity, recovered.velocity, 1e-11)
    }

    @Test
    fun `relative frame acceleration matches independently differenced origin velocities`() {
        val system = CelestialSystem(listOf(sun, earth, moon))
        val tick = 14000L
        val before = system.sample(tick - 1).frame(moon.id, sun.id)
        val sample = system.sample(tick).frame(moon.id, sun.id)
        val after = system.sample(tick + 1).frame(moon.id, sun.id)
        assertVector(sample.originAcceleration, (after.origin.velocity - before.origin.velocity) * 10.0, 1e-7)
        val point = CartesianState(Vector3(100.0, 0.0, 0.0), Vector3.ZERO)
        // A point stationary in this translating frame inherits its origin's acceleration.
        assertEquals(sample.originAcceleration, sample.toParentAcceleration(point, Vector3.ZERO))
    }

    @Test
    fun `input list mutations and future queries cannot alter an existing sample`() {
        val input = mutableListOf(sun, earth, moon)
        val system = CelestialSystem(input)
        val snapshot = system.sample(200)
        val expected = snapshot.frame(moon.id, sun.id)
        input.clear()
        system.sample(999999)
        assertEquals(expected.origin, snapshot.frame(moon.id, sun.id).origin)
        assertEquals(expected.origin, system.sample(200).frame(moon.id, sun.id).origin)
    }

    @Test
    fun `invalid graphs identifiers masses and mismatched parent gravity are rejected`() {
        val invalid =
            listOf(
                emptyList(),
                listOf(sun, sun),
                listOf(sun, sun.copy(id = "test:second")),
                listOf(sun.copy(id = "bad id")),
                listOf(sun.copy(mu = Double.NaN)),
                listOf(sun.copy(mu = 0.0)),
                listOf(sun.copy(orbit = KeplerOrbit(1.0, 1.0))),
                listOf(sun, earth.copy(orbit = null)),
                listOf(sun, earth.copy(parent = "test:missing")),
                listOf(sun, earth.copy(parent = earth.id)),
                listOf(sun, earth.copy(parent = moon.id), moon),
                listOf(sun, earth.copy(orbit = KeplerOrbit(1.0, 1.0))),
            )
        for (bodies in invalid) assertFailsWith<IllegalArgumentException> { CelestialSystem(bodies) }
        val system = CelestialSystem(listOf(sun, earth, moon))
        assertFailsWith<IllegalArgumentException> { system.sample(-1) }
        val snapshot = system.sample(0)
        assertFailsWith<IllegalArgumentException> { snapshot.frame("test:missing", sun.id) }
        assertFailsWith<IllegalArgumentException> { snapshot.frame(sun.id, "test:missing") }
    }

    private fun assertVector(
        expected: Vector3,
        actual: Vector3,
        tolerance: Double = 1e-8,
    ) {
        assertTrue((expected - actual).length() < tolerance, "Expected $expected, got $actual")
    }
}
