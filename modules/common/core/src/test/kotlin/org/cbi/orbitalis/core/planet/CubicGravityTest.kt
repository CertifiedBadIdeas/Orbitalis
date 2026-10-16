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

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CubicGravityTest {
    private val field = CubicGravity(6000.0, 9.81, 64.0, 512.0, 6000.0)
    private val axes = listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0))

    @Test
    fun `all six faces have normal gravity even off centre`() {
        for (axis in axes.indices) {
            val normal = axes[axis]
            val tangent = axes[(axis + 1) % 3] * 2500.0 + axes[(axis + 2) % 3] * 1700.0
            for (sign in listOf(-1.0, 1.0)) {
                for (height in listOf(0.0, 100.0, 500.0)) {
                    val position = normal * (sign * (6000.0 + height)) + tangent
                    val acceleration = field.acceleration(position)
                    val expected = normal * (-sign * 9.81 * 6000.0 / (6000.0 + height) * 6000.0 / (6000.0 + height))
                    assertTrue((acceleration - expected).length() < 1e-12)
                    assertEquals(height, field.altitude(position), 1e-12)
                }
            }
        }
    }

    @Test
    fun `vertical launch stays above its off-centre launch point near a face`() {
        var position = Vector3(2500.0, 6000.0, -1700.0)
        var velocity = Vector3(0.0, 80.0, 0.0)
        repeat(400) {
            val dt = 0.01
            val a = field.acceleration(position)
            position += velocity * dt + a * (dt * dt / 2.0)
            velocity += (a + field.acceleration(position)) * (dt / 2.0)
        }
        assertEquals(2500.0, position.x)
        assertEquals(-1700.0, position.z)
        assertEquals(0.0, velocity.x)
        assertEquals(0.0, velocity.z)
        assertTrue(position.y > 6200.0)
    }

    @Test
    fun `far field is exactly central and inverse square`() {
        for (position in listOf(Vector3(13000.0, 9000.0, -5000.0), Vector3(-14000.0, -15000.0, 13000.0))) {
            val radius = position.length()
            val expected = position.normalized() * (-9.81 * (6000.0 / radius) * (6000.0 / radius))
            assertTrue((field.acceleration(position) - expected).length() < 1e-14)
            assertEquals(-9.81 * 6000.0 * (6000.0 / radius), field.sample(position).potential, 1e-10)
        }
    }

    @Test
    fun `edge corner centre and transition forces are continuous and symmetric`() {
        val positions =
            listOf(
                Vector3.ZERO,
                Vector3(6000.0, 6000.0, 0.0),
                Vector3(6000.0, 6000.0, 6000.0),
                Vector3(5936.0, 6000.0, 0.0),
                Vector3(3000.0, 6512.0, 2000.0),
                Vector3(3000.0, 12000.0, 2000.0),
                Vector3(5990.0, 5990.0, 5990.0),
            )
        assertEquals(0.0, field.acceleration(Vector3.ZERO).length())
        for (p in positions) {
            val a = field.acceleration(p)
            assertTrue((field.acceleration(p * -1.0) + a).length() < 1e-12)
            val permuted = field.acceleration(Vector3(p.z, p.x, p.y))
            assertTrue((permuted - Vector3(a.z, a.x, a.y)).length() < 1e-12)
            for (axis in axes) {
                val left = field.acceleration(p - axis * 1e-6)
                val right = field.acceleration(p + axis * 1e-6)
                assertTrue((left - right).length() < 1e-6, "Discontinuous force at $p")
            }
            if (p != Vector3.ZERO) assertTrue(a.dot(p) < 0.0, "Field repels at $p")
        }
        val edge = field.acceleration(Vector3(6000.0, 6000.0, 0.0))
        assertEquals(edge.x, edge.y)
        assertTrue(abs(edge.z) < 1e-15)
        val corner = field.acceleration(Vector3(6000.0, 6000.0, 6000.0))
        assertEquals(corner.x, corner.y)
        assertEquals(corner.y, corner.z)
    }

    @Test
    fun `acceleration is negative potential gradient throughout the field`() {
        val points =
            listOf(
                Vector3.ZERO,
                Vector3(10.0, 8.0, -5.0),
                Vector3(2000.0, 4000.0, -1000.0),
                Vector3(2500.0, 6200.0, -1700.0),
                Vector3(5980.0, 6000.0, 5985.0),
                Vector3(3000.0, 6512.0, 2000.0),
                Vector3(3000.0, 9000.0, 7000.0),
                Vector3(9000.0, 9000.0, 9000.0),
                Vector3(3000.0, 12000.0, 2000.0),
                Vector3(3000.0, 16000.0, 2000.0),
            )
        for (p in points) {
            val a = field.acceleration(p)
            for (axis in axes) {
                val step = 0.001
                val derivative = (field.sample(p + axis * step).potential - field.sample(p - axis * step).potential) / (2.0 * step)
                assertEquals(-a.dot(axis), derivative, 1e-7, "Potential gradient mismatch at $p")
            }
        }
    }

    @Test
    fun `transition trajectories conserve energy with timestep convergence`() {
        val initial = Vector3(2000.0, 6400.0, 1000.0)
        val initialVelocity = Vector3(40.0, 240.0, 10.0)
        val initialEnergy = initialVelocity.dot(initialVelocity) / 2.0 + field.sample(initial).potential

        fun error(dt: Double): Double {
            var p = initial
            var v = initialVelocity
            var largestError = 0.0
            var highest = 0.0
            repeat((160.0 / dt).toInt()) {
                val a = field.acceleration(p)
                p += v * dt + a * (dt * dt / 2.0)
                v += (a + field.acceleration(p)) * (dt / 2.0)
                highest = maxOf(highest, field.altitude(p))
                val energy = v.dot(v) / 2.0 + field.sample(p).potential
                largestError = maxOf(largestError, abs(energy - initialEnergy))
            }
            assertTrue(highest > 6000.0, "Trajectory did not traverse the full height blend")
            return largestError
        }
        val coarse = error(0.04)
        val fine = error(0.02)
        assertTrue(fine / abs(initialEnergy) < 1e-6, "Relative energy error ${fine / abs(initialEnergy)}")
        println("CUBIC_GRAVITY_ENERGY coarse=$coarse fine=$fine initial=$initialEnergy")
        assertTrue(fine < coarse * 0.4, "No timestep convergence: $coarse -> $fine")
    }

    @Test
    fun `spherical fraction grows along an off-axis ascent`() {
        var previous = -1.0
        for (i in 0..100) {
            val p = Vector3(3000.0, 6500.0 + i * 60.0, 1000.0)
            val a = field.acceleration(p)
            val fraction = (a.x / a.y) / (p.x / p.y)
            assertTrue(fraction >= previous - 1e-12 && fraction <= 1.0 + 1e-12)
            previous = fraction
        }
        assertEquals(1.0, previous, 1e-12)
    }

    @Test
    fun `small cube surface band keeps jump gravity constant on all faces`() {
        val plateau = CubicGravity(16.0, 32.0, 0.0, 8.0, 20.0, constantGravityAltitude = 8.0)
        for (axis in axes) {
            for (sign in listOf(-1.0, 1.0)) {
                for (height in listOf(0.0, 0.9, 3.9, 5.5, 8.0)) {
                    val p = axis * (sign * (16.0 + height))
                    assertTrue((plateau.acceleration(p) - axis * (-sign * 32.0)).length() < 1e-12)
                }
            }
        }
        val far = Vector3(50.0, 30.0, 10.0)
        val expected = far.normalized() * (-32.0 * 24.0 / far.length() * 24.0 / far.length())
        assertTrue((plateau.acceleration(far) - expected).length() < 1e-12)
        for (p in listOf(
            Vector3(0.0, 5.0, 0.0),
            Vector3(2.0, 16.0, 1.0),
            Vector3(2.0, 20.0, 1.0),
            Vector3(2.0, 24.0, 1.0),
            Vector3(10.0, 29.0, 8.0),
            far,
        )) {
            for (axis in axes) {
                val h = 1e-5
                val gradient = (plateau.sample(p + axis * h).potential - plateau.sample(p - axis * h).potential) / (2.0 * h)
                assertEquals(-plateau.acceleration(p).dot(axis), gradient, 1e-5, "Nonconservative surface band at $p")
                assertTrue((plateau.acceleration(p + axis * h) - plateau.acceleration(p - axis * h)).length() < 0.0001)
            }
        }
        for (bad in listOf(-1.0, 9.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { CubicGravity(16.0, 32.0, 0.0, 8.0, 20.0, bad) }
        }
    }

    @Test
    fun `invalid field parameters are rejected`() {
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { CubicGravity(bad, 9.81, 64.0, 512.0, 6000.0) }
            assertFailsWith<IllegalArgumentException> { CubicGravity(6000.0, bad, 64.0, 512.0, 6000.0) }
            if (bad != 0.0) assertFailsWith<IllegalArgumentException> { CubicGravity(6000.0, 9.81, bad, 512.0, 6000.0) }
        }
        assertFailsWith<IllegalArgumentException> { CubicGravity(6000.0, 9.81, 6000.0, 512.0, 6000.0) }
        assertFailsWith<IllegalArgumentException> { CubicGravity(6000.0, 9.81, 64.0, -1.0, 6000.0) }
        assertFailsWith<IllegalArgumentException> { CubicGravity(6000.0, 9.81, 64.0, 512.0, 512.0) }
    }
}
