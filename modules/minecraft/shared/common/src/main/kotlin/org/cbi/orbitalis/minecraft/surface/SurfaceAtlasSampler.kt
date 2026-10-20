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
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.SurfaceAtlas

/** Identical probes for live chunks and read-only disk snapshots. */
object SurfaceAtlasSampler {
    fun sample(
        level: ServerLevel,
        cx: Int,
        cz: Int,
        heightAt: (Int, Int) -> Int,
        stateAt: (BlockPos) -> BlockState,
        biomeAt: (Int, Int, Int) -> Biome,
    ): SurfaceAtlas.Sample {
        val bounds = FlatOverworld.footprint(level)
        var height = 0
        var red = 0
        var green = 0
        var blue = 0
        var water = 0
        for (dx in intArrayOf(2, 6, 10, 14)) {
            for (dz in intArrayOf(2, 6, 10, 14)) {
                val x = (cx * 16 + dx).coerceIn(bounds.minX, bounds.maxX - 1)
                val z = (cz * 16 + dz).coerceIn(bounds.minZ, bounds.maxZ - 1)
                val y = heightAt(x and 15, z and 15)
                val pos = BlockPos(x, y, z)
                val state = stateAt(pos)
                if (state.fluidState.`is`(FluidTags.WATER)) water++
                val biome = biomeAt(x and 15, y, z and 15)
                val color =
                    when {
                        state.fluidState.`is`(FluidTags.WATER) -> biome.waterColor

                        state.`is`(BlockTags.LEAVES) -> biome.foliageColor

                        state.`is`(Blocks.GRASS_BLOCK) || state.`is`(Blocks.SHORT_GRASS) || state.`is`(Blocks.TALL_GRASS) ||
                            state.`is`(Blocks.FERN) -> biome.getGrassColor(x.toDouble(), z.toDouble())

                        else -> state.getMapColor(level, pos).col
                    }
                height += y
                red += color shr 16 and 255
                green += color shr 8 and 255
                blue += color and 255
            }
        }
        return SurfaceAtlas.Sample(
            cx,
            cz,
            (height / 16 + 1).coerceIn(-4096, 4096),
            ((red / 16) shl 16) or ((green / 16) shl 8) or (blue / 16),
            (water * 255 + 8) / 16,
        )
    }
}
