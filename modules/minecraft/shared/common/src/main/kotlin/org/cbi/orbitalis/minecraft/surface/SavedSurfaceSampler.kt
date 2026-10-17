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

import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.util.SimpleBitStorage
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.PalettedContainerRO
import org.cbi.orbitalis.core.planet.SurfaceAtlas

/** Decodes presentation data only: no chunk, entity, POI, light or ticket is installed. */
object SavedSurfaceSampler {
    private val statesCodec =
        PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY,
            BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState(),
        )

    fun sample(
        level: ServerLevel,
        tag: CompoundTag,
    ): SurfaceAtlas.Sample? {
        val cx = tag.getInt("xPos")
        val cz = tag.getInt("zPos")
        if (cx !in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK || cz !in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK) return null
        if (tag.getString("Status") != "minecraft:full") return null
        // Older/newer saves must be opened normally by Minecraft's data fixer first.
        if (tag.getInt("DataVersion") !=
            net.minecraft.SharedConstants
                .getCurrentVersion()
                .dataVersion.version
        ) {
            return null
        }
        val heights = tag.getCompound("Heightmaps").getLongArray("WORLD_SURFACE")
        if (heights.isEmpty()) return null
        val storage = SimpleBitStorage(Mth.ceillog2(level.height + 1), 256, heights)
        val sections = tag.getList("sections", 10).map { it as CompoundTag }.associateBy { it.getByte("Y").toInt() }
        val biomes = level.registryAccess().registryOrThrow(Registries.BIOME)
        val biomeCodec =
            PalettedContainer.codecRO(
                biomes.asHolderIdMap(),
                biomes.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES,
                biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS),
            )
        val states = mutableMapOf<Int, PalettedContainer<BlockState>>()
        val biomeSections =
            mutableMapOf<Int, PalettedContainerRO<Holder<Biome>>>()
        return SurfaceAtlasSampler.sample(
            level,
            cx,
            cz,
            { x, z -> storage.get(z * 16 + x) + level.minBuildHeight - 1 },
            { pos ->
                if (pos.y < level.minBuildHeight) {
                    Blocks.AIR.defaultBlockState()
                } else {
                    val section = pos.y shr 4
                    states
                        .getOrPut(section) {
                            statesCodec.parse(NbtOps.INSTANCE, sections.getValue(section).getCompound("block_states")).getOrThrow()
                        }.get(pos.x and 15, pos.y and 15, pos.z and 15)
                }
            },
            { x, y, z ->
                val biomeY = y.coerceIn(level.minBuildHeight, level.maxBuildHeight - 1)
                val section = biomeY shr 4
                biomeSections
                    .getOrPut(section) {
                        biomeCodec.parse(NbtOps.INSTANCE, sections.getValue(section).getCompound("biomes")).getOrThrow()
                    }.get(x shr 2, (biomeY and 15) shr 2, z shr 2)
                    .value()
            },
        )
    }
}
