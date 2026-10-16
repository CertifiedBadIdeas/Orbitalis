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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CubicPlanetProofTest {
    private val field = CubicGravity(16.0, 9.81, 0.0, 4.0, 20.0)

    @Test
    fun sharpEdgeChangesForceWithoutPotentialJump() {
        val a = Vector3(16.1, 16.0, 0.0)
        val b = Vector3(16.0, 16.1, 0.0)
        assertTrue(field.acceleration(a).x < 0 && field.acceleration(a).y == 0.0)
        assertTrue(field.acceleration(b).y < 0 && field.acceleration(b).x == 0.0)
        assertTrue(abs(field.sample(a).potential - field.sample(b).potential) < 1e-10)
        val tie = field.acceleration(Vector3(16.0, 16.0, 0.0))
        assertTrue(tie.x == tie.y && tie.z == 0.0)
        assertTrue(field.acceleration(Vector3.ZERO).length() == 0.0)
        assertTrue(field.acceleration(Vector3(0.0, -8.0, 0.0)).y > 0)
    }

    @Test
    fun sharpPotentialGradientAndFarFieldAgree() {
        for (p in listOf(Vector3(17.0, 10.0, 1.0), Vector3(25.0, 19.0, 2.0), Vector3(40.0, 25.0, 8.0))) {
            val step = Vector3(1e-4, 0.0, 0.0)
            val gradient = (field.sample(p + step).potential - field.sample(p - step).potential) / 2e-4
            assertTrue(abs(field.acceleration(p).x + gradient) < 1e-6)
        }
        val p = Vector3(40.0, 25.0, 8.0)
        val expected = p * (-9.81 * 16.0 * 16.0 / p.length() / p.length() / p.length())
        assertTrue((field.acceleration(p) - expected).length() < 1e-10)
    }

    @Test
    fun fixtureHasConnectedInteriorUnequalReliefAndConvexEdges() {
        for (x in -15..14) assertTrue(CubicPlanetFixture.solid(x, 0, 0))
        assertTrue(CubicPlanetFixture.solid(0, 18, 0))
        assertFalse(CubicPlanetFixture.solid(18, 0, 0))
        assertTrue(CubicPlanetFixture.solid(17, 0, 0))
        for (i in -16..15) {
            assertTrue(CubicPlanetFixture.solid(i, i, 0))
            assertTrue(CubicPlanetFixture.solid(i, i, i))
        }
        assertFalse(CubicPlanetFixture.solid(21, 0, 0))
    }
}
