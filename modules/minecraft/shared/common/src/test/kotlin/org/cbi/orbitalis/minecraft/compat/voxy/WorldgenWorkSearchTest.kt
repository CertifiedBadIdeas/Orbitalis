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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorldgenWorkSearchTest {
    private fun key(pos: ChunkPos) = ChunkPos.asLong(pos.x shr 2, pos.z shr 2)

    @Test
    fun exhaustedPartialLeavesDoNotBlockLaterWorkOrBecomePersistedCompletion() {
        val border = listOf(ChunkPos(0, 312), ChunkPos(0, 313))
        val circle = listOf(ChunkPos(4, 310), ChunkPos(5, 310))
        val next = listOf(ChunkPos(8, 310), ChunkPos(9, 310))
        val batches = listOf(border, circle, next)
        val completed = setOf(border.first(), circle.first())
        val tracked = mutableSetOf<Long>()
        val calls = mutableListOf<List<ChunkPos>>()
        val find: (MutableSet<Long>) -> List<ChunkPos>? = { excluded ->
            batches.firstOrNull { excluded.add(key(it.first())) }?.also { calls.add(it) }
        }
        val needed: (ChunkPos) -> Boolean = { it.z <= 312 && it != circle.last() && it !in completed }
        assertEquals(next, WorldgenWorkSearch.findPending(tracked, find, needed))
        assertEquals(batches, calls)
        assertEquals(setOf(key(next.first())), tracked)
        assertEquals(listOf(border.last()), WorldgenWorkSearch.findPending(tracked, find) { it !in completed })
        assertEquals(setOf(key(next.first()), key(border.first())), tracked)
    }

    @Test
    fun noPendingWorkTerminatesWithoutReservingSkippedLeaves() {
        val leaf = listOf(ChunkPos(-1, -1))
        val tracked = mutableSetOf(123L)
        var calls = 0
        val result =
            WorldgenWorkSearch.findPending(tracked, { excluded ->
                calls++
                if (excluded.add(key(leaf.first()))) leaf else null
            }, { false })
        assertNull(result)
        assertEquals(2, calls)
        assertEquals(setOf(123L), tracked)
    }
}
