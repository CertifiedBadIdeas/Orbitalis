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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectangularGravityTest {
    private val field = RectangularGravity(1000.0, 64.0, 1000.0, 9.81)

    @Test
    fun `top gravity is calibrated and interior faces edges corners are nonsingular`() {
        assertEquals(-9.81, field.acceleration(Vector3(0.0, 64.0, 0.0)).y, 1e-12)
        assertEquals(Vector3.ZERO, field.acceleration(Vector3.ZERO))
        for (p in listOf(
            Vector3.ZERO,
            Vector3(0.0, 64.0, 0.0),
            Vector3(1000.0, 0.0, 0.0),
            Vector3(1000.0, 64.0, 0.0),
            Vector3(1000.0, 64.0, 1000.0),
            Vector3(1000.0, 0.0, 1000.0),
            Vector3(0.0, 0.0, 1000.0),
        )) {
            val sample = field.sample(p)
            assertTrue(sample.potential.isFinite() && sample.potential < 0.0)
            for (offset in listOf(Vector3(1e-5, 0.0, 0.0), Vector3(0.0, 1e-5, 0.0), Vector3(0.0, 0.0, 1e-5))) {
                assertTrue((field.acceleration(p + offset) - field.acceleration(p - offset)).length() < 1e-4)
            }
        }
    }

    @Test
    fun `reflection and square symmetry hold and off axis gravity is not central`() {
        val p = Vector3(400.0, 800.0, 200.0)
        val a = field.acceleration(p)
        assertTrue(a.x < 0.0 && a.y < 0.0 && a.z < 0.0)
        assertTrue(p.cross(a).length() > 100.0)
        assertTrue((a + field.acceleration(p * -1.0)).length() < 1e-10)
        val rotated = field.acceleration(Vector3(p.z, p.y, p.x))
        assertTrue((rotated - Vector3(a.z, a.y, a.x)).length() < 1e-10)
        assertEquals(field.potential(p), field.potential(p * -1.0), 1e-8)
    }

    @Test
    fun `acceleration is negative gradient of potential including far field blend`() {
        for (p in listOf(
            Vector3(100.0, 20.0, 50.0),
            Vector3(1000.0, 64.0, 1000.0),
            Vector3(400.0, 800.0, 200.0),
            Vector3(7000.0, 1000.0, 2000.0),
            Vector3(9000.0, 500.0, 1500.0),
            Vector3(13000.0, 1000.0, 4000.0),
        )) {
            val a = field.acceleration(p)
            val delta = 0.001
            for ((offset, component) in listOf(
                Vector3(delta, 0.0, 0.0) to a.x,
                Vector3(0.0, delta, 0.0) to a.y,
                Vector3(0.0, 0.0, delta) to a.z,
            )) {
                assertEquals(component, -(field.potential(p + offset) - field.potential(p - offset)) / (2 * delta), 2e-5)
            }
        }
        for (radius in listOf(8000.0, 12000.0)) {
            val p = Vector3(0.0, radius, 0.0)
            assertTrue(
                (field.acceleration(p + Vector3(0.0, 0.001, 0.0)) - field.acceleration(p - Vector3(0.0, 0.001, 0.0))).length() < 1e-6,
            )
        }
    }

    @Test
    fun `analytic near field agrees with independent Simpson volume integration`() {
        // Numerical Newton integral uses no corner primitives or potential finite differences.
        val p = Vector3(234.0, 1800.0, 321.0)
        var potential = 0.0
        var acceleration = Vector3.ZERO
        val count = 32
        val steps = Vector3(2000.0 / count, 128.0 / count, 2000.0 / count)

        fun weight(i: Int) =
            if (i == 0 || i == count) {
                1.0
            } else if (i % 2 == 0) {
                2.0
            } else {
                4.0
            }
        for (i in 0..count) {
            for (j in 0..count) {
                for (k in 0..count) {
                    val d = p - Vector3(-1000.0 + i * steps.x, -64.0 + j * steps.y, -1000.0 + k * steps.z)
                    val r = d.length()
                    val mass = field.mu / (2000.0 * 128.0 * 2000.0) * steps.x * steps.y * steps.z * weight(i) * weight(j) * weight(k) / 27.0
                    potential -= mass / r
                    acceleration = acceleration - d.normalized() * (mass / r / r)
                }
            }
        }
        assertEquals(potential, field.potential(p), 0.002)
        assertTrue((acceleration - field.acceleration(p)).length() < 1e-5)
    }

    @Test
    fun `distant field approaches total mass inverse square without cancellation`() {
        val p = Vector3(1.0, 2.0, -3.0).normalized() * 1e8
        val expected = p.normalized() * (-field.mu / p.length() / p.length())
        assertTrue((field.acceleration(p) - expected).length() / expected.length() < 1e-9)
        assertEquals(-field.mu / p.length(), field.potential(p), 1e-10)
    }

    @Test
    fun `units scale geometrically while surface acceleration stays fixed`() {
        val larger = RectangularGravity(2000.0, 128.0, 2000.0, 9.81)
        val p = Vector3(450.0, 920.0, 200.0)
        assertTrue((field.acceleration(p) - larger.acceleration(p * 2.0)).length() < 1e-10)
        assertEquals(field.potential(p) * 2.0, larger.potential(p * 2.0), 1e-8)
        assertTrue(field.contains(Vector3(1000.0, 64.0, -1000.0)))
        assertFalse(field.contains(Vector3(1000.1, 64.0, -1000.0)))
    }

    @Test
    fun `invalid parameters are rejected`() {
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { RectangularGravity(bad, 64.0, 1000.0, 9.81) }
            assertFailsWith<IllegalArgumentException> { RectangularGravity(1000.0, bad, 1000.0, 9.81) }
            assertFailsWith<IllegalArgumentException> { RectangularGravity(1000.0, 64.0, bad, 9.81) }
            assertFailsWith<IllegalArgumentException> { RectangularGravity(1000.0, 64.0, 1000.0, bad) }
        }
    }
}
