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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CubicPlanetVolumeTest {
    private val volume = CubicPlanetVolume()

    @Test
    fun `each interior edge corner and central voxel has one owner and exact round trip`() {
        val coordinates = listOf(-256, -255, -17, -16, -1, 0, 15, 16, 254, 255)
        for (x in coordinates) {
            for (y in coordinates) {
                for (z in coordinates) {
                    val cell = PlanetCell(x, y, z)
                    val owner = volume.owner(cell)
                    assertEquals(cell, volume.toPlanet(owner, volume.toLocal(owner, cell)))
                    assertEquals(1, CubicFace.entries.count { volume.owns(it, volume.toLocal(it, cell)) }, "$cell")
                }
            }
        }
        assertFalse(volume.contains(PlanetCell(256, 0, 0)))
        assertFalse(volume.contains(PlanetCell(0, -257, 0)))
    }

    @Test
    fun `opposite face tunnels have no gaps across tie planes and centre`() {
        for (axis in 0..2) {
            for (offset in listOf(-200, -1, 0, 200)) {
                val visited = mutableSetOf<Pair<CubicFace, PlanetCell>>()
                for (step in -256..255) {
                    val c =
                        when (axis) {
                            0 -> PlanetCell(step, offset, 0)
                            1 -> PlanetCell(0, step, offset)
                            else -> PlanetCell(offset, 0, step)
                        }
                    val face = volume.owner(c)
                    assertTrue(visited.add(face to volume.toLocal(face, c)))
                    assertTrue(volume.owns(face, volume.toLocal(face, c)))
                }
                assertEquals(512, visited.size)
            }
        }
    }

    @Test
    fun `every voxel touched by an observer box is covered by interest including negative chunks`() {
        val anchors = mutableListOf(PlanetCell(0, 0, 0), PlanetCell(-1, -1, -1))
        for (x in listOf(-256, -16, 0, 15, 255)) {
            for (y in listOf(-256, -16, 0, 15, 255)) {
                for (z in listOf(-256, -16, 0, 15, 255)) anchors.add(PlanetCell(x, y, z))
            }
        }
        for (anchor in anchors) {
            for (offset in listOf(0, 8)) {
                val volume = CubicPlanetVolume(chunkOriginOffset = offset)
                val p = anchor.centre()
                val chunks = volume.chunksNear(p, 2.0)
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        for (dz in -2..2) {
                            val cell = PlanetCell(anchor.x + dx, anchor.y + dy, anchor.z + dz)
                            if (!volume.contains(cell)) continue
                            val face = volume.owner(cell)
                            val local = volume.toLocal(face, cell)
                            assertTrue(
                                PlanetChunk(face, Math.floorDiv(local.x + offset, 16), Math.floorDiv(local.z + offset, 16)) in chunks,
                                "Missing $cell near $anchor with chunk offset $offset",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `face diagonals keep a single face while edges corners and centre prepare every neighbour`() {
        for (face in CubicFace.entries) {
            val interior = volume.toPlanet(face, Vector3(32.0, 1.0, 32.0))
            assertEquals(setOf(face), volume.chunksNear(interior, 16.0).map { it.face }.toSet())
        }
        assertEquals(
            6,
            volume
                .chunksNear(Vector3.ZERO, 2.0)
                .map { it.face }
                .toSet()
                .size,
        )
        for (sx in listOf(-1, 1)) {
            for (sy in listOf(-1, 1)) {
                for (sz in listOf(-1, 1)) {
                    assertEquals(
                        3,
                        volume
                            .chunksNear(Vector3(sx * 256.0, sy * 256.0, sz * 256.0), 2.0)
                            .map { it.face }
                            .toSet()
                            .size,
                    )
                }
            }
        }
        for (axis in 0..2) {
            for (a in listOf(-256.0, 256.0)) {
                for (b in listOf(-256.0, 256.0)) {
                    val edge =
                        when (axis) {
                            0 -> Vector3(0.0, a, b)
                            1 -> Vector3(a, 0.0, b)
                            else -> Vector3(a, b, 0.0)
                        }
                    assertEquals(
                        2,
                        volume
                            .chunksNear(edge, 2.0)
                            .map { it.face }
                            .toSet()
                            .size,
                        "$edge",
                    )
                }
            }
        }
        assertTrue(volume.chunksNear(Vector3(1000.0, 0.0, 0.0), 32.0).isEmpty())
    }
}
