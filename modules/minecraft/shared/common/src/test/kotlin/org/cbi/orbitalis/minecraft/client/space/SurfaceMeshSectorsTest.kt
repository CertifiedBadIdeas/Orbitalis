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

class SurfaceMeshSectorsTest {
    @Test fun changesInvalidateOnlyOwnersOfTheChangedTopAndSharedEdges() {
        val index = SurfaceMeshSectors()
        val sample = SurfaceAtlas.Sample(32, 32, 100, 0x123456)
        index.put(sample)
        val dirty = SurfacePlanetMesh.sectors.filter { index.revision(it) != 0L }.toSet()
        assertEquals(setOf(SurfacePlanetMesh.Sector(1, 1), SurfacePlanetMesh.Sector(0, 1), SurfacePlanetMesh.Sector(1, 0)), dirty)
        index.put(sample)
        assertTrue(dirty.all { index.revision(it) == 1L })
        index.put(sample.copy(height = 120))
        assertTrue(dirty.all { index.revision(it) == 2L })
        assertEquals(0L, index.revision(SurfacePlanetMesh.Sector(0, 0)))
    }

    @Test fun immutableSnapshotsContainTheirLocalPositiveHaloAcrossNegativeCoordinates() {
        val index = SurfaceMeshSectors()
        val own = SurfaceAtlas.Sample(-1, -1, 100, 1)
        val halo = SurfaceAtlas.Sample(0, -1, 120, 2)
        val far = SurfaceAtlas.Sample(100, 100, 200, 3)
        listOf(own, halo, far).forEach(index::put)
        val snapshot = index.snapshot(SurfacePlanetMesh.Sector(-1, -1))
        assertEquals(setOf(own, halo), snapshot.toSet())
        index.put(own.copy(height = 200))
        assertEquals(setOf(own, halo), snapshot.toSet())
        assertEquals(SurfacePlanetMesh.Sector(-1, -1), SurfacePlanetMesh.sector(-1, -1))
    }

    @Test fun sharedHeightWallIsEmittedOnceByTheLeftSectorUsingTheHalo() {
        val index = SurfaceMeshSectors()
        index.put(SurfaceAtlas.Sample(31, 0, 120, 1))
        index.put(SurfaceAtlas.Sample(32, 0, 180, 2))
        val faces =
            listOf(SurfacePlanetMesh.Sector(0, 0), SurfacePlanetMesh.Sector(1, 0)).flatMap { sector ->
                val atlas = SurfaceAtlas()
                index.snapshot(sector).forEach(atlas::put)
                SurfacePlanetMesh.faces(atlas, sector).toList()
            }
        val wall = faces.filter { it.normal.x != 0.0 && it.vertices.all { v -> v.x == 512.0 && v.z in 0.0..16.0 } }
        assertEquals(1, wall.size)
        assertEquals(
            setOf(120.0, 180.0),
            wall
                .single()
                .vertices
                .map { it.y }
                .toSet(),
        )
    }

    @Test fun coarseAndFineSectorsCoverTheWholeFootprintWithoutOverlap() {
        assertEquals(400, SurfacePlanetMesh.sectors.size)

        fun area(face: SpaceScene.Face): Double = (face.vertices[2].x - face.vertices[0].x) * (face.vertices[2].z - face.vertices[0].z)
        val coarseArea = SurfacePlanetMesh.sectors.sumOf { SurfacePlanetMesh.coarse(it).sumOf(::area) }
        val fineArea =
            SurfacePlanetMesh.sectors.sumOf {
                SurfacePlanetMesh
                    .faces(
                        SurfaceAtlas(),
                        it,
                    ).filter { it.normal.y > 0 }
                    .sumOf(::area)
            }
        assertEquals(100_000_000.0, coarseArea)
        assertEquals(coarseArea, fineArea)
    }
}
