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
import kotlin.test.assertTrue

class SphericalGravityTest {
    @Test
    fun `exterior falls off with squared radius and points towards centre`() {
        val gravity = SphericalGravity(6000.0, 9.81)
        assertEquals(-9.81, gravity.acceleration(Vector3(0.0, 6000.0, 0.0)).y, 1e-12)
        assertEquals(-9.81 / 4.0, gravity.acceleration(Vector3(12000.0, 0.0, 0.0)).x, 1e-12)
        val direction = Vector3(1.0, -2.0, 3.0).normalized()
        val acceleration = gravity.acceleration(direction * 18000.0)
        assertTrue((acceleration + direction * (9.81 / 9.0)).length() < 1e-12)
    }

    @Test
    fun `interior is continuous and finite through centre`() {
        val gravity = SphericalGravity(6000.0, 9.81)
        assertEquals(0.0, gravity.acceleration(Vector3.ZERO).length())
        assertEquals(-9.81 / 2, gravity.acceleration(Vector3(0.0, 3000.0, 0.0)).y, 1e-12)
        val below = gravity.acceleration(Vector3(0.0, 6000.0 - 1e-6, 0.0)).y
        val above = gravity.acceleration(Vector3(0.0, 6000.0 + 1e-6, 0.0)).y
        assertEquals(below, above, 1e-8)
    }

    @Test
    fun `potential is finite and its gradient matches acceleration across the surface`() {
        val gravity = SphericalGravity(6000.0, 9.81)
        assertEquals(-1.5 * 9.81 * 6000.0, gravity.potential(Vector3.ZERO), 1e-8)
        for (radius in listOf(3000.0, 5999.0, 6000.0, 6001.0, 18000.0)) {
            val epsilon = 0.01
            val derivative =
                (gravity.potential(Vector3(radius + epsilon, 0.0, 0.0)) - gravity.potential(Vector3(radius - epsilon, 0.0, 0.0))) /
                    (2 * epsilon)
            assertEquals(-gravity.acceleration(Vector3(radius, 0.0, 0.0)).x, derivative, 2e-5)
        }
    }

    @Test
    fun `invalid body parameters are rejected`() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { SphericalGravity(value, 9.81) }
            assertFailsWith<IllegalArgumentException> { SphericalGravity(6000.0, value) }
        }
    }
}
