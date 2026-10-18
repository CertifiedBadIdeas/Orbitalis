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

package org.cbi.orbitalis.minecraft.surface

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.chunk.status.ChunkStep
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Immutable persisted-footprint snapshots keep generation independent of the movement border. */
object FiniteSurfaceTerrain {
    data class Bounds(
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int,
    ) {
        fun contains(
            x: Int,
            z: Int,
        ) = x >= minX && x < maxX && z >= minZ && z < maxZ

        fun intersectsChunk(
            x: Int,
            z: Int,
        ) = minX < maxX && minZ < maxZ &&
            x.toLong() * 16 < maxX && x.toLong() * 16 + 16 > minX &&
            z.toLong() * 16 < maxZ && z.toLong() * 16 + 16 > minZ

        fun containsChunk(
            x: Int,
            z: Int,
        ) = x.toLong() * 16 >= minX && x.toLong() * 16 + 16 <= maxX &&
            z.toLong() * 16 >= minZ && z.toLong() * 16 + 16 <= maxZ
    }

    private val snapshots = ConcurrentHashMap<ServerLevel, Bounds>()

    fun attach(level: ServerLevel) {
        if (level.dimension() == Level.OVERWORLD) snapshots[level] = FlatOverworld.footprint(level)
    }

    fun close(server: MinecraftServer) {
        snapshots.keys.removeIf { it.server === server }
    }

    @JvmStatic
    fun bounds(level: ServerLevel): Bounds? = snapshots[level]

    @JvmStatic
    fun generate(
        level: ServerLevel,
        step: ChunkStep,
        chunk: ChunkAccess,
        original: () -> CompletableFuture<ChunkAccess>,
    ): CompletableFuture<ChunkAccess> {
        val bounds = bounds(level) ?: return original()
        val target = step.targetStatus()
        if (!chunk.persistedStatus.isBefore(target)) return original()
        val pos = chunk.pos
        if (!bounds.intersectsChunk(pos.x, pos.z) &&
            ((target.isOrAfter(ChunkStatus.STRUCTURE_STARTS) && !target.isAfter(ChunkStatus.FEATURES)) || target == ChunkStatus.SPAWN)
        ) {
            if (target == ChunkStatus.BIOMES) {
                val biome =
                    level
                        .registryAccess()
                        .registryOrThrow(
                            net.minecraft.core.registries.Registries.BIOME,
                        ).getHolderOrThrow(Biomes.THE_VOID)
                chunk.fillBiomesFromNoise({ _, _, _, _ -> biome }, level.chunkSource.randomState().sampler())
            }
            Heightmap.primeHeightmaps(chunk, target.heightmapsAfter())
            return CompletableFuture.completedFuture(chunk)
        }
        val result = original()
        return if (!bounds.containsChunk(pos.x, pos.z) &&
            target in setOf(ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS, ChunkStatus.FEATURES)
        ) {
            result.thenApply { generated ->
                trim(generated, bounds)
                generated
            }
        } else {
            result
        }
    }

    private fun trim(
        chunk: ChunkAccess,
        bounds: Bounds,
    ) {
        val pos = BlockPos.MutableBlockPos()
        chunk.sections.forEachIndexed { index, section ->
            if (!section.hasOnlyAir()) {
                val baseY = chunk.getSectionYFromSectionIndex(index) * 16
                for (x in 0..15) {
                    for (z in 0..15) {
                        val worldX = chunk.pos.minBlockX + x
                        val worldZ = chunk.pos.minBlockZ + z
                        if (!bounds.contains(worldX, worldZ)) {
                            for (y in 0..15) {
                                if (!section.getBlockState(x, y, z).isAir) {
                                    pos.set(worldX, baseY + y, worldZ)
                                    chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false)
                                    chunk.removeBlockEntity(pos)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
