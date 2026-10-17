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

import org.cbi.orbitalis.core.planet.SurfaceAtlas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfacePlanetMeshTest {
    @Test fun unknownAtlasCoversWholeFootprintWithFallbackAndHasBoundedGeometry() {
        val faces = SurfacePlanetMesh.faces(SurfaceAtlas()).toList()
        val tops = faces.filter { it.normal.y > 0.0 }
        assertEquals(314 * 314, tops.size)
        val area =
            tops.sumOf { face ->
                val a = face.vertices[0]
                val b = face.vertices[2]
                (b.x - a.x) * (b.z - a.z)
            }
        assertEquals(100_000_000.0, area)
        assertTrue(tops.all { it.vertices.all { v -> v.y == SpaceScene.TOP } })
        assertTrue(faces.size < 300000)
    }

    @Test fun realReliefReplacesFallbackAndSkirtsJoinUnknownNeighboursAndPlanetEdges() {
        val atlas = SurfaceAtlas()
        atlas.put(SurfaceAtlas.Sample(0, 0, 200, 0xff00ff))
        atlas.put(SurfaceAtlas.Sample(-313, -313, 120, 0x11ffff))
        val faces = SurfacePlanetMesh.faces(atlas).toList()
        val peak = faces.single { it.normal.y > 0 && it.color == 0xff00ff }
        assertTrue(peak.vertices.all { it.y == 200.0 })
        assertEquals(setOf(0.0, 32.0), peak.vertices.map { it.x }.toSet())
        assertTrue(
            faces.any {
                it.normal.y == 0.0 &&
                    it.vertices.any { v ->
                        v.y == 200.0
                    } && it.vertices.any { v -> v.y == SpaceScene.TOP }
            },
        )
        assertTrue(faces.any { it.color == 0x11ffff && it.normal.x == -1.0 && it.vertices.all { v -> v.x == -5000.0 } })
        assertTrue(faces.flatMap { it.vertices }.all { it.x in -5000.0..5000.0 && it.z in -5000.0..5000.0 })
    }
}
