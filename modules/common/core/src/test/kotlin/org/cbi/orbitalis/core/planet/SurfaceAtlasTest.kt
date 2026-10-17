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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceAtlasTest {
    @Test fun currentValuesStreamIsBoundedIdempotentAndDoesNotLoseReplacements() {
        val atlas = SurfaceAtlas()
        val a = SurfaceAtlas.Sample(0, 0, 80, 0x336699)
        assertTrue(atlas.put(a))
        val cursor = atlas.after(0, 1).first
        assertFalse(atlas.put(a))
        atlas.put(a.copy(x = 1))
        atlas.put(a.copy(height = 120))
        val changes = atlas.after(cursor, 256)
        assertEquals(listOf(80, 120), changes.second.map { it.height })
        assertEquals(2, atlas.size)
        assertEquals(2, atlas.after(0, 256).second.size)
        assertTrue(atlas.after(changes.first, 1).second.isEmpty())
    }

    @Test fun unknownCellsStayAbsentAndNegativeCellsUseFloorDivision() {
        val atlas = SurfaceAtlas()
        assertTrue(atlas.overview().isEmpty())
        atlas.put(SurfaceAtlas.Sample(-1, -1, 100, 0xff0000))
        atlas.put(SurfaceAtlas.Sample(-4, -4, 200, 0x0000ff))
        atlas.put(SurfaceAtlas.Sample(0, 0, 20, 0x00ff00))
        val cells = atlas.overview()
        assertEquals(2, cells.size)
        val west = cells.getValue(-1 to -1)
        assertEquals(150.0, west.height)
        assertEquals(0x7f007f, west.color)
        assertEquals(2, west.known)
        assertFalse(cells.containsKey(-2 to -2))
    }

    @Test fun boundsIncludePartialEdgeChunksAndRejectBadRecords() {
        val low = SurfaceAtlas.Sample(-313, -313, -64, 0)
        val high = SurfaceAtlas.Sample(312, 312, 320, 0xffffff)
        assertEquals(0, low.key)
        assertEquals(SurfaceAtlas.MAX_SAMPLES - 1, high.key)
        assertThrows(IllegalArgumentException::class.java) { low.copy(x = -314) }
        assertThrows(IllegalArgumentException::class.java) { high.copy(z = 313) }
        assertThrows(IllegalArgumentException::class.java) { low.copy(height = 4097) }
        assertThrows(IllegalArgumentException::class.java) { low.copy(color = -1) }
        assertThrows(IllegalArgumentException::class.java) { SurfaceAtlas().after(0, 257) }
    }
}
