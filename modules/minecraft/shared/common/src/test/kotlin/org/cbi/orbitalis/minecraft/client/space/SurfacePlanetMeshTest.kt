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
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfacePlanetMeshTest {
    @Test fun unknownAtlasCoversWholeFootprintWithFallbackAndHasBoundedGeometry() {
        val faces = SurfacePlanetMesh.faces(SurfaceAtlas()).toList()
        val tops = faces.filter { it.normal.y > 0.0 }
        assertEquals(626 * 626, tops.size)
        val area =
            tops.sumOf { face ->
                val a = face.vertices[0]
                val b = face.vertices[2]
                (b.x - a.x) * (b.z - a.z)
            }
        assertEquals(100_000_000.0, area)
        assertTrue(tops.all { it.vertices.all { v -> v.y == SpaceScene.TOP } })
        assertTrue(faces.size < 1200000)
    }

    @Test fun realReliefReplacesFallbackAndSkirtsJoinUnknownNeighboursAndPlanetEdges() {
        val atlas = SurfaceAtlas()
        atlas.put(SurfaceAtlas.Sample(0, 0, 200, 0xff00ff))
        atlas.put(SurfaceAtlas.Sample(-313, -313, 120, 0x11ffff))
        val faces = SurfacePlanetMesh.faces(atlas).toList()
        val peak = faces.single { it.normal.y > 0 && it.color == 0xff00ff }
        assertTrue(peak.vertices.all { it.y == 200.0 })
        assertEquals(setOf(0.0, 16.0), peak.vertices.map { it.x }.toSet())
        assertTrue(
            faces.any {
                it.normal.y == 0.0 &&
                    it.vertices.any { v ->
                        v.y == 200.0
                    } && it.vertices.any { v -> v.y == SpaceScene.TOP }
            },
        )
        assertTrue(faces.any { it.normal.x == -1.0 && it.vertices.all { v -> v.x == -5000.0 } && it.vertices.any { v -> v.y == 120.0 } })
        assertTrue(faces.flatMap { it.vertices }.all { it.x in -5000.0..5000.0 && it.z in -5000.0..5000.0 })
    }

    @Test fun sectorOwnedWallsFollowEdgeHeightsAndStopAtTheNativeBottom() {
        val atlas = SurfaceAtlas()
        atlas.put(SurfaceAtlas.Sample(-313, -313, 220, 0x778855))
        val sector = SurfacePlanetMesh.sector(-313, -313)
        val faces =
            SurfacePlanetMesh
                .faces(atlas, sector)
                .filter {
                    it.normal.x == -1.0 && it.vertices.all { v -> v.x == -5000.0 && v.z in -5000.0..-4992.0 }
                }.toList()
        assertEquals(220.0, faces.flatMap { it.vertices }.maxOf { it.y })
        assertEquals(-64.0, faces.flatMap { it.vertices }.minOf { it.y })
        val area = faces.sumOf { (it.vertices[1] - it.vertices[0]).length() * (it.vertices[3] - it.vertices[0]).length() }
        assertEquals(8.0 * (220.0 + 64.0), area)
        for (face in faces) {
            val (a, b, c) = face.vertices
            assertTrue((b - a).cross(c - a).dot(face.normal) > 0)
        }
        assertTrue(PlanetRockShell.walls(0.0, 16.0, 0.0, 16.0, 220.0, 0).none())
    }

    @Test fun perimeterLayersCoverThePhysicalWallAtAllHeightsWithoutOvershoot() {
        for (height in listOf(-64.0, -63.0, -10.0, 0.0, 32.0, 64.0, 320.0)) {
            val faces = PlanetRockShell.walls(-5000.0, -4992.0, 0.0, 16.0, height, 0).toList()
            assertTrue(faces.all { face -> face.vertices.all { it.y in -64.0..height } })
            val area = faces.sumOf { (it.vertices[1] - it.vertices[0]).length() * (it.vertices[3] - it.vertices[0]).length() }
            assertEquals(16.0 * (height + 64.0), area, 1e-8)
        }
    }

    @Test fun coarseInitialSectorsHaveClosedExteriorWallsBeforeFineMeshesArrive() {
        val walls =
            SurfacePlanetMesh.sectors
                .asSequence()
                .flatMap { SurfacePlanetMesh.coarse(it) }
                .filter { it.normal.y == 0.0 }
                .toList()
        val area = walls.sumOf { (it.vertices[1] - it.vertices[0]).length() * (it.vertices[3] - it.vertices[0]).length() }
        assertEquals(40_000.0 * 128.0, area, 1e-8)
        assertTrue(walls.all { it.vertices.all { v -> v.y in -64.0..64.0 } })
    }

    @Test fun allFourCliffsHaveOutwardNormalsAndJoinTheSameLowerPlane() {
        val cases =
            listOf(
                listOf(-5000.0, -4992.0, 0.0, 16.0) to Vector3(-1.0, 0.0, 0.0),
                listOf(4992.0, 5000.0, 0.0, 16.0) to Vector3(1.0, 0.0, 0.0),
                listOf(0.0, 16.0, -5000.0, -4992.0) to Vector3(0.0, 0.0, -1.0),
                listOf(0.0, 16.0, 4992.0, 5000.0) to Vector3(0.0, 0.0, 1.0),
            )
        for ((bounds, normal) in cases) {
            val (x0, x1, z0, z1) = bounds
            val walls = PlanetRockShell.walls(x0, x1, z0, z1, 120.0, 0).toList()
            assertTrue(walls.isNotEmpty())
            assertTrue(walls.all { it.normal.dot(normal) > 1.0 - 1e-12 })
            assertEquals(-64.0, walls.flatMap { it.vertices }.minOf { it.y })
            assertTrue(
                walls.all {
                    val (a, b, c) = it.vertices
                    (b - a).cross(c - a).dot(normal) > 0.0
                },
            )
        }
    }
}
