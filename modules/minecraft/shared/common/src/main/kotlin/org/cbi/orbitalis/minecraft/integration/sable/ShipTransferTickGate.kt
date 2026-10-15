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

package org.cbi.orbitalis.minecraft.integration.sable

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import java.util.IdentityHashMap

/** Server-thread-only leases; independent of optional Sable classes and external engine payloads. */
object ShipTransferTickGate {
    private val leases = IdentityHashMap<Any, MutableMap<Level, MutableSet<Long>>>()

    fun hold(
        owner: Any,
        level: Level,
        chunks: Iterable<ChunkPos>,
    ) {
        val positions = leases.getOrPut(owner) { mutableMapOf() }.getOrPut(level) { mutableSetOf() }
        chunks.forEach { positions.add(it.toLong()) }
    }

    fun release(owner: Any) {
        leases.remove(owner)
    }

    @JvmStatic
    fun blocks(
        level: Level,
        position: BlockPos,
    ): Boolean {
        if (level.isClientSide) return false
        val chunk = ChunkPos.asLong(position.x shr 4, position.z shr 4)
        return leases.values.any { chunk in it[level].orEmpty() }
    }
}
