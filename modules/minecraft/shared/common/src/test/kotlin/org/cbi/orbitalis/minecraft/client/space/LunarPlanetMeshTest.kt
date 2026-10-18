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

import org.cbi.orbitalis.core.planet.LunarSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LunarPlanetMeshTest {
    @Test fun sectorsCoverNativeFootprintAndUseSeededGrayRelief() {
        val seed = 719L
        val terrain = LunarSurface(seed)
        val faces = LunarPlanetMesh.sectors.flatMap { LunarPlanetMesh.faces(seed, it).toList() }
        val tops = faces.filter { it.normal.y > 0.0 }
        assertEquals(176 * 176, tops.size)
        assertEquals(
            2800.0 * 2800.0,
            tops.sumOf { f ->
                val a = f.vertices[0]
                val b = f.vertices[2]
                (b.x - a.x) * (b.z - a.z)
            },
        )
        for (f in tops) {
            val a = f.vertices[0]
            val x = (kotlin.math.floor(a.x / 16.0).toInt() * 16 + 8).coerceIn(-1400, 1399)
            val z = (kotlin.math.floor(a.z / 16.0).toInt() * 16 + 8).coerceIn(-1400, 1399)
            assertEquals(terrain.height(x, z)!!.toDouble() + 1.0, a.y)
            assertEquals(f.color shr 16 and 255, f.color shr 8 and 255)
        }
        assertTrue(tops.map { it.vertices[0].y }.distinct().size > 20)
        assertTrue(faces.all { f -> f.vertices.all { it.x in -1400.0..1400.0 && it.z in -1400.0..1400.0 && it.y in -64.0..89.0 } })
        assertTrue(faces.size < 150000)
        for (axis in listOf(-1400.0, 1400.0)) {
            val walls = faces.filter { f -> f.normal.y == 0.0 && f.vertices.all { it.x == axis } }
            assertTrue(walls.isNotEmpty())
            assertEquals(-64.0, walls.flatMap { it.vertices }.minOf { it.y })
        }
        val bottom = LunarPlanetMesh.underside().toList()
        assertTrue(bottom.all { it.normal.y < 0.0 && it.vertices.all { v -> v.y == -64.0 } })
    }

    @Test fun clippedCoarseCoverageHasNoEarthSizedOrColoredCells() {
        val faces = LunarPlanetMesh.sectors.flatMap { LunarPlanetMesh.coarse(it).toList() }
        val tops = faces.filter { it.normal.y > 0.0 }
        assertEquals(
            2800.0 * 2800.0,
            tops.sumOf { f ->
                val a = f.vertices[0]
                val b = f.vertices[2]
                (b.x - a.x) * (b.z - a.z)
            },
        )
        assertTrue(tops.all { it.color == 0x969698 })
        assertTrue(faces.all { f -> f.vertices.all { it.x in -1400.0..1400.0 && it.z in -1400.0..1400.0 } })
    }

    @Test fun nativeMoonSkyRetainsDistantReliefAboveBesideAndBelowTheSurface() {
        fun visible(
            x: Double,
            y: Double,
            z: Double,
        ) = LunarPlanetMesh.visibleFromSurface(
            org.cbi.orbitalis.core.planet
                .Vector3(x, y, z),
        )
        assertTrue(!visible(0.0, 90.0, 0.0))
        assertTrue(visible(0.0, 100.0, 0.0))
        assertTrue(visible(0.0, -100.0, 0.0))
        assertTrue(visible(1500.0, 0.0, 0.0))
        assertTrue(visible(-1500.0, 0.0, 0.0))
        assertTrue(visible(0.0, 0.0, 1500.0))
        assertTrue(visible(0.0, 0.0, -1500.0))
    }
}
