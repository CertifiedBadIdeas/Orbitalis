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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnerPlanetSurfaceTest {
    @Test
    fun `finite seeded planets have distinct reproducible terrain and no exterior columns`() {
        for (kind in InnerPlanetSurface.Kind.entries) {
            val seed = InnerPlanetSurface.terrainSeed(12345, kind)
            val a = InnerPlanetSurface(kind, seed, 191.5)
            val b = InnerPlanetSurface(kind, InnerPlanetSurface.terrainSeed(12346, kind), 191.5)
            val positions = (-191..191 step 9).flatMap { x -> (-191..191 step 11).map { z -> x to z } }
            val heights = positions.map { (x, z) -> a.height(x, z) }
            assertEquals(heights, positions.reversed().map { (x, z) -> a.height(x, z) }.reversed())
            assertNotEquals(heights, positions.map { (x, z) -> b.height(x, z) })
            assertTrue(heights.all { it != null && it in 24..124 })
            assertTrue(heights.filterNotNull().max() - heights.filterNotNull().min() > 10)
            for ((x, z) in listOf(-192 to 0, 192 to 0, 0 to -192, 0 to 192, Int.MAX_VALUE to Int.MIN_VALUE)) assertNull(a.height(x, z))
            assertTrue(a.height(-191, -191) != null && a.height(191, 191) != null)
            for (x in -190..190) {
                for (z in listOf(-17, -16, -1, 0, 15, 16)) {
                    assertTrue(kotlin.math.abs(a.height(x, z)!! - a.height(x + 1, z)!!) <= 5)
                }
            }
        }
        assertNotEquals(
            InnerPlanetSurface.terrainSeed(12345, InnerPlanetSurface.Kind.MERCURY),
            InnerPlanetSurface.terrainSeed(12345, InnerPlanetSurface.Kind.VENUS),
        )
    }
}
