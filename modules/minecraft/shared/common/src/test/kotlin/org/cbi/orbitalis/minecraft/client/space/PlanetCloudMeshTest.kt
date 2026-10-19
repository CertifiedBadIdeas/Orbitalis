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

class PlanetCloudMeshTest {
    @Test fun `fancy clouds have native four metre volume face shades and fixed centre sampling`() {
        val vertices = PlanetCloudMesh.vertices(true)
        assertEquals((2 + 256 * 4) * 4, vertices.size)
        assertEquals(setOf(0.7f, 0.8f, 0.9f, 1f), vertices.map { it.shade }.toSet())
        assertEquals(0f, vertices.minOf { it.y })
        assertEquals(4f, vertices.maxOf { it.y })
        assertTrue(vertices.take(4).all { it.y == 0f && it.shade == 0.7f })
        assertTrue(vertices.drop(4).take(4).all { it.y == 4f - 1f / 1024f })
        val firstNegativeX = vertices.drop(8).take(4)
        assertTrue(firstNegativeX.all { it.x == 0f && it.u == 0.5f / 256f })
        assertEquals(setOf(0f, 1f), firstNegativeX.map { it.v }.toSet())
        assertTrue(PlanetCloudMesh.vertices(false).all { it.y == 0f && it.shade == 1f })
        assertEquals(4, PlanetCloudMesh.vertices(false).size)
    }

    @Test fun `tiles retain native drift and offset while covering every selected finite footprint`() {
        for (half in listOf(500.0, 5000.0, 10000.0)) {
            for (time in listOf(0f, 1f, 20000f, 1000000f)) {
                val tiles = PlanetCloudMesh.tiles(half, time)
                assertTrue(tiles.size <= 64)
                assertTrue(tiles.minOf { it.x } <= -half && tiles.maxOf { it.x } + 3072f >= half)
                assertTrue(tiles.minOf { it.z } <= -half && tiles.maxOf { it.z } + 3072f >= half)
                for (tile in tiles) {
                    val u = (tile.x + (time * 0.03f).toDouble()) / 3072.0
                    val v = (tile.z / 12.0 + 0.33000001311302185) / 256.0
                    assertTrue(kotlin.math.abs(u - kotlin.math.round(u)) < 1e-6)
                    assertTrue(kotlin.math.abs(v - kotlin.math.round(v)) < 1e-6)
                }
            }
        }
    }
}
