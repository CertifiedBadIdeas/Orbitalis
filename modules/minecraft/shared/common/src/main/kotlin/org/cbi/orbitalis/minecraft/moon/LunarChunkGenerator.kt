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

package org.cbi.orbitalis.minecraft.moon

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderSet
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.blending.Blender
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import net.minecraft.world.level.levelgen.structure.StructureSet
import org.cbi.orbitalis.core.planet.LunarSurface
import java.util.Optional
import java.util.concurrent.CompletableFuture

/** Native finite terrain. The native world seed is captured before chunk workers are started. */
class LunarChunkGenerator(
    val lunarBiome: Holder<Biome>,
) : FlatLevelSource(FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(emptyList())), lunarBiome, emptyList())) {
    @Volatile private var sampledTerrain: LunarSurface? = null
    val terrain: LunarSurface get() = checkNotNull(sampledTerrain) { "Lunar generator seed has not been initialized" }

    override fun codec(): MapCodec<out ChunkGenerator> = CODEC

    override fun createState(
        sets: HolderLookup<StructureSet>,
        random: RandomState,
        seed: Long,
    ): ChunkGeneratorStructureState {
        sampledTerrain = LunarSurface(LunarSurface.terrainSeed(seed))
        return super.createState(sets, random, seed)
    }

    override fun fillFromNoise(
        blender: Blender,
        random: RandomState,
        structures: StructureManager,
        chunk: ChunkAccess,
    ): CompletableFuture<ChunkAccess> {
        val surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
        val floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
        val pos = BlockPos.MutableBlockPos()
        for (x in 0..15) {
            for (z in 0..15) {
                val top = terrain.height(chunk.pos.minBlockX + x, chunk.pos.minBlockZ + z) ?: continue
                for (y in maxOf(LunarSurface.BOTTOM, chunk.minBuildHeight)..minOf(top, chunk.maxBuildHeight - 1)) {
                    val state = block(y, top)
                    chunk.setBlockState(pos.set(x, y, z), state, false)
                    surface.update(x, y, z, state)
                    floor.update(x, y, z, state)
                }
            }
        }
        return CompletableFuture.completedFuture(chunk)
    }

    override fun getBaseHeight(
        x: Int,
        z: Int,
        type: Heightmap.Types,
        height: LevelHeightAccessor,
        random: RandomState,
    ): Int = terrain.height(x, z)?.let { (it + 1).coerceIn(height.minBuildHeight, height.maxBuildHeight) } ?: height.minBuildHeight

    override fun getBaseColumn(
        x: Int,
        z: Int,
        height: LevelHeightAccessor,
        random: RandomState,
    ): NoiseColumn {
        val top = terrain.height(x, z)
        return NoiseColumn(
            height.minBuildHeight,
            Array(height.height) { index ->
                val y = height.minBuildHeight + index
                if (top != null && y in LunarSurface.BOTTOM..top) block(y, top) else Blocks.AIR.defaultBlockState()
            },
        )
    }

    override fun getMinY(): Int = -64

    override fun getGenDepth(): Int = 384

    override fun getSeaLevel(): Int = -64

    override fun getSpawnHeight(height: LevelHeightAccessor): Int = LunarSurface.SEA_LEVEL + 32

    private fun block(
        y: Int,
        top: Int,
    ): BlockState =
        when {
            y >= top - 2 -> Blocks.ANDESITE.defaultBlockState()
            y >= top - 12 -> Blocks.TUFF.defaultBlockState()
            else -> Blocks.BASALT.defaultBlockState()
        }

    companion object {
        val CODEC: MapCodec<LunarChunkGenerator> = Biome.CODEC.fieldOf("biome").xmap(::LunarChunkGenerator, LunarChunkGenerator::lunarBiome)
    }
}
