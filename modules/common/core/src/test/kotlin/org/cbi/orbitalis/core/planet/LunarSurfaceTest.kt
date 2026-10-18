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

class LunarSurfaceTest {
    @Test
    fun `finite footprint has no exterior floor and bounded repeatable seeded relief`() {
        val a = LunarSurface(42)
        val b = LunarSurface(43)
        val samples = (-1400 until 1400 step 7).flatMap { x -> (-1400 until 1400 step 13).map { z -> x to z } }
        val heights = samples.map { (x, z) -> a.height(x, z)!! }
        assertTrue(heights.all { it in 24..88 })
        assertTrue(heights.max() - heights.min() > 15)
        assertEquals(heights, samples.reversed().map { (x, z) -> a.height(x, z)!! }.reversed())
        assertNotEquals(heights, samples.map { (x, z) -> b.height(x, z)!! })
        for ((x, z) in listOf(-1401 to 0, 1400 to 0, 0 to -1401, 0 to 1400)) assertNull(a.height(x, z))
        assertTrue(a.height(-1400, -1400) != null && a.height(1399, 1399) != null)
    }

    @Test
    fun `neighbouring columns remain continuous across chunk and negative cell seams`() {
        val terrain = LunarSurface(-12345)
        for (x in -1399..1398) {
            for (z in listOf(-193, -192, -17, -16, -1, 0, 15, 16, 191, 192)) {
                assertTrue(kotlin.math.abs(terrain.height(x, z)!! - terrain.height(x + 1, z)!!) <= 3)
                assertTrue(kotlin.math.abs(terrain.height(z, x)!! - terrain.height(z, x + 1)!!) <= 3)
            }
        }
    }
}
