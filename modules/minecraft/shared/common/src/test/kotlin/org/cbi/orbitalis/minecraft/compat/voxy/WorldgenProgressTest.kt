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

package org.cbi.orbitalis.minecraft.compat.voxy

import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.LunarSurface
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldgenProgressTest {
    @Test fun configuredPlanetsRetainExactFractionalNativeColumnBoundaries() {
        for (width in listOf(1000, 2000, 5000, 10000, 20000)) {
            val profile = SolarSystemProfile(11.0, selectedEarthWidth = width)
            for (half in listOf(profile.geometry.moonHalfSize) + profile.innerPlanets.map { it.halfSize }) {
                val bounds = WorldgenSurfaceBounds.square(half)
                val terrain = LunarSurface(0L, half)
                for (x in listOf(bounds.minX - 1, bounds.minX, 0, bounds.maxX - 1, bounds.maxX)) {
                    assertEquals(terrain.contains(x, 0), bounds.contains(x, 0))
                    assertEquals(terrain.contains(0, x), bounds.contains(0, x))
                }
                assertFalse(bounds.intersectsChunk(Math.floorDiv(bounds.maxX + 15, 16), 0))
                assertTrue(bounds.intersectsChunk(Math.floorDiv(bounds.maxX - 1, 16), 0))
            }
        }
    }

    @Test fun partialNodesMatchBruteForceAcrossRectangleCircleAndCompletionEdges() {
        for (bounds in listOf(
            FiniteSurfaceTerrain.Bounds(-9, -1, 23, 77),
            WorldgenSurfaceBounds.square(957.5),
            WorldgenSurfaceBounds.square(5000.0),
        )) {
            for (center in listOf(ChunkPos(0, 0), ChunkPos(29, -30), ChunkPos(-61, 63))) {
                for (radius in listOf(0, 1, 15, 32, 64, 128, 512)) {
                    val completed: (ChunkPos) -> Boolean = { ((it.x * 31 + it.z) and 3) == 0 }
                    val progress = WorldgenProgress(bounds, center, radius, completed)
                    for (nx in -3..2) {
                        for (nz in -3..2) {
                            var expected = 0
                            var eligible = 0
                            for (x in nx * 32 until nx * 32 + 32) {
                                for (z in nz * 32 until nz * 32 + 32) {
                                    val dx = x.toLong() - center.x
                                    val dz = z.toLong() - center.z
                                    if (bounds.intersectsChunk(x, z) && dx * dx + dz * dz <= radius.toLong() * radius) {
                                        eligible++
                                        if (!completed(ChunkPos(x, z))) expected++
                                    }
                                }
                            }
                            val actual = progress.partialNode(nx, nz)
                            if (actual == null) assertEquals(1024, eligible) else assertEquals(expected, actual)
                        }
                    }
                }
            }
        }
    }

    @Test fun fullyInteriorNodesKeepNativeFastPathAndEmptyNodesDoNotCheckCompletion() {
        var checks = 0
        val bounds = WorldgenSurfaceBounds.square(1000.0)
        val progress =
            WorldgenProgress(bounds, ChunkPos(0, 0), 512) {
                checks++
                false
            }
        assertNull(progress.partialNode(0, 0))
        assertEquals(0, progress.partialNode(100, 100))
        assertEquals(0, checks)
        val empty = WorldgenProgress(FiniteSurfaceTerrain.Bounds(0, 0, 0, 0), ChunkPos(0, 0), 0) { error("empty") }
        assertEquals(0, empty.partialNode(0, 0))
    }

    @Test fun countingScopeRestoresNestedContextsAndExceptionsWithoutLeakingAcrossThreads() {
        assertNull(WorldgenBorderScope.current.get())
        assertNull(WorldgenBorderScope.counting.get())
        val outer = WorldgenSurfaceBounds.square(5000.0)
        val inner = WorldgenSurfaceBounds.square(957.5)
        val progress = WorldgenProgress(outer, ChunkPos(0, 0), 512) { false }
        WorldgenBorderScope.count(outer, progress) {
            assertEquals(outer, WorldgenBorderScope.current.get())
            assertFalse(WorldgenBorderScope.outside(0, 0, 1))
            assertTrue(WorldgenBorderScope.outside(100, 0, 1))
            assertFailsWith<IllegalStateException> {
                WorldgenBorderScope.count(inner, progress) { error("test") }
            }
            assertEquals(outer, WorldgenBorderScope.current.get())
            val observed =
                java.util.concurrent.atomic
                    .AtomicReference<FiniteSurfaceTerrain.Bounds?>()
            val child = Thread { observed.set(WorldgenBorderScope.current.get()) }
            child.start()
            child.join()
            assertNull(observed.get())
        }
        assertNull(WorldgenBorderScope.current.get())
        assertNull(WorldgenBorderScope.counting.get())
    }
}
