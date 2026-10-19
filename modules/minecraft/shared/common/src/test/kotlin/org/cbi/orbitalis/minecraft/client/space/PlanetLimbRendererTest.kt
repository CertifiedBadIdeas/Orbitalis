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

package org.cbi.orbitalis.minecraft.client.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanetLimbRendererTest {
    @Test fun atmosphericLayerContainsFeatherAcrossSelectedPlanetSizes() {
        for (halfSize in listOf(500.0, 5000.0, 50000.0)) {
            val (minimum, maximum) = PlanetLimbRenderer.atmosphereBounds(halfSize)
            for (vertex in PlanetLimbRenderer.vertices(halfSize)) {
                val p = vertex.position
                assertTrue(p.x in minimum.x..maximum.x)
                assertTrue(p.y in minimum.y..maximum.y)
                assertTrue(p.z in minimum.z..maximum.z)
            }
        }
    }

    @Test fun haloTracksSelectedBodyAfterDefaultGeometryWasAlreadyPrepared() {
        val legacy = PlanetLimbRenderer.vertices(5000.0)
        val small = PlanetLimbRenderer.vertices(500.0)
        assertEquals(legacy.size, small.size)
        assertEquals(5280.0, legacy.maxOf { kotlin.math.abs(it.position.x) }, 1e-8)
        assertEquals(780.0, small.maxOf { kotlin.math.abs(it.position.x) }, 1e-8)
        assertTrue(small.all { kotlin.math.abs(it.position.x) <= 780.0 && kotlin.math.abs(it.position.z) <= 780.0 })
        assertEquals(legacy.map { it.alpha }, small.map { it.alpha })
        assertEquals(legacy.map { it.position.y }, small.map { it.position.y })
        assertEquals(legacy, PlanetLimbRenderer.vertices(5000.0))
    }
}
