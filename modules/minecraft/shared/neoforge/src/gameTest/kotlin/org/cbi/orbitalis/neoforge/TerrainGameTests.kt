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

package org.cbi.orbitalis.neoforge

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.status.ChunkPyramid
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain

@PrefixGameTestTemplate(false)
object TerrainGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "terrain", timeoutTicks = 400)
    fun finiteTerrainEdges(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val bounds = FiniteSurfaceTerrain.bounds(level)!!
        helper.assertTrue(bounds.minX == -5000 && bounds.maxX == 5000, "Generation missed early border initialization")
        // Both signs, all four corners and both axes; the default border bisects these chunks.
        for ((x, z) in listOf(4999 to 4999, 4999 to -5000, -5000 to 4999, -5000 to -5000)) {
            val chunk = level.getChunk(x shr 4, z shr 4)
            helper.assertTrue(chunk.persistedStatus == ChunkStatus.FULL && chunk.isLightCorrect, "Native completion did not finish")
            helper.assertTrue(!chunk.getBlockState(BlockPos(x, level.minBuildHeight, z)).isAir, "Interior flat ground was removed")
            for (localX in 0..15) {
                for (localZ in 0..15) {
                    val worldX = chunk.pos.minBlockX + localX
                    val worldZ = chunk.pos.minBlockZ + localZ
                    if (!bounds.contains(worldX, worldZ)) {
                        for (y in level.minBuildHeight until level.maxBuildHeight) {
                            helper.assertTrue(
                                chunk.getBlockState(BlockPos(worldX, y, worldZ)).isAir,
                                "Terrain leaked at $worldX,$y,$worldZ",
                            )
                        }
                        helper.assertTrue(
                            chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) == level.minBuildHeight - 1,
                            "Stale outside heightmap",
                        )
                    }
                }
            }
        }
        for ((x, z) in listOf(313 to 0, -314 to 0, 0 to 313, 0 to -314, 313 to 313, -314 to -314)) {
            val chunk = level.getChunk(x, z)
            helper.assertTrue(chunk.persistedStatus == ChunkStatus.FULL && chunk.isLightCorrect, "Exterior dependency stalled")
            helper.assertTrue(chunk.sections.all { it.hasOnlyAir() }, "Exterior chunk generated terrain")
            helper.assertTrue(chunk.getNoiseBiome(x * 4, 0, z * 4).`is`(Biomes.THE_VOID), "Exterior biome was not void")
            helper.assertTrue(chunk.allStarts.isEmpty() && chunk.allReferences.isEmpty(), "Exterior structure data generated")
        }
        val interior = level.getChunk(100, 100)
        helper.assertTrue(!interior.getBlockState(BlockPos(1600, level.minBuildHeight, 1600)).isAir, "Interior generation changed")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "terrain")
    fun finiteFeatureWrites(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val chunk = level.getChunk(312, 0)
        // Rejection must happen before any neighbour-cache lookup. No cache is needed for this path.
        val region = WorldGenRegion(level, null, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), chunk)
        for (pos in listOf(BlockPos(5000, 70, 0), BlockPos(-5001, 70, 0), BlockPos(0, 70, 5000), BlockPos(0, 70, -5001))) {
            helper.assertTrue(!region.ensureCanWrite(pos), "Feature admitted external column")
            helper.assertTrue(!region.setBlock(pos, Blocks.STONE.defaultBlockState(), 2), "Feature wrote external block")
        }
        val chicken =
            net.minecraft.world.entity.EntityType.CHICKEN
                .create(level)!!
        chicken.setPos(5000.5, 70.0, 0.5)
        helper.assertTrue(!region.addFreshEntity(chicken), "Worldgen spawned entity outside the border")
        // Normal runtime writes and already-generated chunks are not worldgen and stay native.
        val pos = BlockPos(5001, 150, 0)
        level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2)
        helper.assertTrue(level.getBlockState(pos).`is`(Blocks.GOLD_BLOCK), "Runtime placement was intercepted")
        level.removeBlock(pos, false)
        helper.succeed()
    }
}
