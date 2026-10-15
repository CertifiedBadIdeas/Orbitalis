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
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/** Temporary terrain, owned only while this server's diagnostic scene exists. */
internal class SableFlightPad(
    private val level: ServerLevel,
    private val offset: BlockPos = BlockPos.ZERO,
) {
    private val originals = linkedMapOf<BlockPos, BlockState>()
    private val chunks = mutableSetOf<ChunkPos>()
    private val material = Blocks.OBSIDIAN.defaultBlockState()

    fun prepare() {
        val cells = (-2..2).flatMap { x -> (118..122).map { z -> BlockPos(x, 104, z).offset(offset) } }
        require(cells.all { level.getBlockState(it).isAir }) { "Flight pad at (0,104,120) must be clear; existing blocks are preserved" }
        require(
            (105..113).all { y ->
                (-1..1).all { x -> (119..121).all { z -> level.getBlockState(BlockPos(x, y, z).offset(offset)).isAir } }
            },
        ) {
            "Clear the launch column above the flight pad first"
        }
        cells.map(::ChunkPos).toSet().forEach { chunk ->
            level.chunkSource.addRegionTicket(TICKET, chunk, 2, chunk)
            chunks.add(chunk)
            level.getChunk(chunk.x, chunk.z)
        }
        cells.forEach { position ->
            originals[position] = level.getBlockState(position)
            check(level.setBlock(position, material, 3)) { "Could not place diagnostic flight pad" }
        }
    }

    /** Prepare the destination before releasing any source terrain; preserve both on rejection. */
    fun translated(delta: BlockPos): SableFlightPad {
        require(originals.all { (position, _) -> level.getBlockState(position) == material }) {
            "Frame shift rejected: the temporary pad has been edited"
        }
        val next = SableFlightPad(level, offset.offset(delta))
        try {
            next.prepare()
        } catch (error: Exception) {
            next.clear()
            throw error
        }
        clear()
        return next
    }

    fun clear() {
        originals.forEach { (position, original) ->
            // Preserve subsequent player edits, including replacement by a different block.
            if (level.getBlockState(position) == material) level.setBlock(position, original, 3)
        }
        originals.clear()
        chunks.forEach { level.chunkSource.removeRegionTicket(TICKET, it, 2, it) }
        chunks.clear()
    }

    companion object {
        private val TICKET = TicketType.create<ChunkPos>("orbitalis_sable_flight", Comparator.comparingLong { it.toLong() })
    }
}
