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
import kotlin.test.assertTrue

class SurfaceGravityLayerTest {
    private val body = RectangularGravity(1000.0, 64.0, 1000.0, 9.81)
    private val field = SurfaceGravityLayer(body, 11.0)

    @Test
    fun `native region includes surface edges corners and underground positions`() {
        for (x in listOf(-1000.0, 0.0, 1000.0)) {
            for (z in listOf(-1000.0, 0.0, 1000.0)) {
                for (y in listOf(-64.0, 0.0, 64.0, 264.0, 320.0)) {
                    assertEquals(Vector3(0.0, -11.0, 0.0), field.acceleration(Vector3(x, y, z)))
                }
            }
        }
    }

    @Test
    fun `finite field is restored above below and beside compact layer`() {
        for (p in listOf(Vector3(0.0, 664.0, 0.0), Vector3(0.0, -320.0, 0.0), Vector3(1256.0, 64.0, 0.0), Vector3(0.0, 64.0, -1256.0))) {
            assertEquals(body.sample(p), field.sample(p))
        }
        val distant = Vector3(1e6, 200.0, 0.0)
        assertEquals(body.sample(distant), field.sample(distant))
    }

    @Test
    fun `layer is conservative through vertical side and lower blends`() {
        val points =
            listOf(
                Vector3(0.0, 400.0, 0.0),
                Vector3(900.0, 500.0, 900.0),
                Vector3(1120.0, 200.0, 0.0),
                Vector3(1120.0, 430.0, 1120.0),
                Vector3(200.0, -200.0, 100.0),
                Vector3(1000.0, 320.0, 1000.0),
                Vector3(1256.0, 664.0, 1256.0),
            )
        for (p in points) {
            val a = field.acceleration(p)
            val delta = 0.001
            for ((offset, component) in listOf(
                Vector3(delta, 0.0, 0.0) to a.x,
                Vector3(0.0, delta, 0.0) to a.y,
                Vector3(0.0, 0.0, delta) to a.z,
            )) {
                assertEquals(component, -(field.potential(p + offset) - field.potential(p - offset)) / (2 * delta), 1e-5)
            }
        }
    }

    @Test
    fun `upper layer attracts downward and all boundaries have continuous forces`() {
        for (x in 0..10) {
            for (z in 0..10) {
                for (y in 264..664 step 10) {
                    assertTrue(field.acceleration(Vector3(x * 100.0, y.toDouble(), z * 100.0)).y < 0.0)
                }
            }
        }
        for (p in listOf(
            Vector3(1000.0, 320.0, 0.0),
            Vector3(1256.0, 430.0, 0.0),
            Vector3(500.0, 664.0, 500.0),
            Vector3(800.0, -320.0, 0.0),
        )) {
            for (offset in listOf(Vector3(1e-5, 0.0, 0.0), Vector3(0.0, 1e-5, 0.0), Vector3(0.0, 0.0, 1e-5))) {
                assertTrue((field.acceleration(p + offset) - field.acceleration(p - offset)).length() < 1e-4)
            }
        }
    }
}
