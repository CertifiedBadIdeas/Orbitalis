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
    @Test fun atmosphereIsAnchoredToSurfaceAndFadesNearCloudHeightForEveryBodySize() {
        for (halfSize in listOf(500.0, 5000.0, 50000.0)) {
            for (cloudHeight in listOf(128.0, 192.0, 256.0)) {
                val (minimum, maximum) = PlanetLimbRenderer.atmosphereBounds(halfSize, cloudHeight)
                assertEquals(SpaceScene.TOP, minimum.y)
                assertEquals(cloudHeight + 24.0, maximum.y)
                for (vertex in PlanetLimbRenderer.vertices(halfSize, cloudHeight)) {
                    val p = vertex.position
                    assertTrue(p.x in minimum.x..maximum.x)
                    assertTrue(p.y in minimum.y..maximum.y)
                    assertTrue(p.z in minimum.z..maximum.z)
                }
                assertTrue(maximum.x - halfSize <= 160.0)
            }
        }
    }

    @Test fun changingCloudHeightLeavesTheBaseAttachedToTheSurface() {
        val low = PlanetLimbRenderer.atmosphereBounds(500.0, 128.0)
        val high = PlanetLimbRenderer.atmosphereBounds(500.0, 256.0)
        assertEquals(low.first, high.first)
        assertEquals(128.0, high.second.y - low.second.y)
        assertEquals(low.second.x, high.second.x)
    }
}
