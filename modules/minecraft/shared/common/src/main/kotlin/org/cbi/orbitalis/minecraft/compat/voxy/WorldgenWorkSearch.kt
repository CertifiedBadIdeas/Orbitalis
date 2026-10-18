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

/** A partial native leaf may contain no eligible work without being globally completed. */
object WorldgenWorkSearch {
    fun findPending(
        tracked: MutableSet<Long>,
        find: (MutableSet<Long>) -> List<ChunkPos>?,
        needed: (ChunkPos) -> Boolean,
    ): List<ChunkPos>? {
        val searched = tracked.toMutableSet()
        while (!Thread.currentThread().isInterrupted) {
            val batch = find(searched) ?: return null
            val pending = batch.filter(needed)
            if (pending.isNotEmpty()) {
                val first = batch.first()
                if (tracked.add(ChunkPos.asLong(first.x shr 2, first.z shr 2))) return pending
            }
        }
        return null
    }
}
