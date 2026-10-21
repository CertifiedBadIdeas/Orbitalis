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

import dev.ryanhcode.sable.platform.SablePlotPlatform
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.ChunkPos

/** Read-created native air chunks are a cache, not additional ship cargo or control authority. */
object SableShipChunks {
    /** A 32 by 32 metre footprint can straddle three native cells along either axis. */
    const val MAX_OCCUPIED_CHUNKS = 9

    fun occupied(body: ServerSubLevel): List<PlotChunkHolder> {
        val entityChunks =
            body.level
                .getAllEntities()
                .filter { body.plot.contains(it.position()) }
                .map { ChunkPos(it.blockPosition()).toLong() }
                .toSet()
        return body.plot.loadedChunks.filter { holder ->
            val chunk = holder.chunk
            if (chunk.sections.any { !it.hasOnlyAir() } || chunk.blockEntitiesPos.isNotEmpty() ||
                chunk.blockTicks.count() > 0 || chunk.fluidTicks.count() > 0 || holder.pos.toLong() in entityChunks
            ) {
                true
            } else {
                // Auxiliary lights and mod attachments can carry state even in an all-air chunk.
                // Use the same pinned platform producers as native serialization, without importing
                // loader-specific attachment classes into shared code.
                val metadata = CompoundTag()
                SablePlotPlatform.INSTANCE.writeLightData(metadata, body.level.registryAccess(), chunk)
                SablePlotPlatform.INSTANCE.writeChunkAttachments(metadata, body.level.registryAccess(), chunk)
                metadata.allKeys.any { key ->
                    val value = metadata.get(key)
                    when (value) {
                        is CompoundTag -> !value.isEmpty
                        is net.minecraft.nbt.ListTag -> !value.isEmpty()
                        else -> true
                    }
                }
            }
        }
    }

    /** Keep native addresses/payloads intact; omit only empty live chunks proved above. */
    fun compactCapture(
        body: ServerSubLevel,
        snapshot: CompoundTag,
    ) {
        val chunks = snapshot.getCompound("plot").getCompound("chunks")

        fun key(holder: PlotChunkHolder) =
            body.plot
                .toLocal(holder.pos)
                .toLong()
                .toString()
        val loaded =
            body.plot.loadedChunks
                .map(::key)
                .toSet()
        check(loaded.containsAll(chunks.allKeys)) { "Native snapshot contains chunks unavailable for live empty-state proof" }
        val retained = occupied(body).map(::key).toSet()
        require(retained.size in 1..MAX_OCCUPIED_CHUNKS) { "Ship exceeds nine occupied native plot chunks" }
        check(chunks.allKeys.containsAll(retained)) { "Native snapshot is missing occupied chunks" }
        for (key in (chunks.allKeys.toSet() - retained)) {
            val chunk = chunks.getCompound(key)
            check(
                chunk.allKeys.all { it in setOf("sections", "block_entities", "block_ticks", "fluid_ticks", "heightmaps") } &&
                    chunk.getCompound("sections").isEmpty && chunk.getList("block_entities", 10).isEmpty() &&
                    chunk.getList("block_ticks", 10).isEmpty() && chunk.getList("fluid_ticks", 10).isEmpty(),
            ) { "Read-created chunk contains native persistent state" }
            // Heightmaps are derived from blocks. The native loader recreates them for an air
            // cache chunk on demand; plot-level metadata and attached entities stay untouched.
            chunks.remove(key)
        }
        check(chunks.allKeys == retained) { "Captured occupied chunk set changed" }
    }
}
