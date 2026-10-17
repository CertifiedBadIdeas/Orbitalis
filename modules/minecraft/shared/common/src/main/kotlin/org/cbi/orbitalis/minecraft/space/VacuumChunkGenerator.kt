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

package org.cbi.orbitalis.minecraft.space

import com.mojang.serialization.MapCodec
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import java.util.Optional

/** An explicit vacuum contract, independent of arbitrary flat-world/datapack terrain settings. */
class VacuumChunkGenerator(
    val vacuumBiome: Holder<Biome>,
) : FlatLevelSource(FlatLevelGeneratorSettings(Optional.of(HolderSet.direct(emptyList())), vacuumBiome, emptyList())) {
    init {
        require(vacuumBiome.`is`(Biomes.THE_VOID)) { "Orbital vacuum requires minecraft:the_void" }
    }

    override fun codec(): MapCodec<out ChunkGenerator> = CODEC

    companion object {
        val CODEC: MapCodec<VacuumChunkGenerator> =
            Biome.CODEC.fieldOf("biome").xmap(::VacuumChunkGenerator, VacuumChunkGenerator::vacuumBiome)
    }
}
