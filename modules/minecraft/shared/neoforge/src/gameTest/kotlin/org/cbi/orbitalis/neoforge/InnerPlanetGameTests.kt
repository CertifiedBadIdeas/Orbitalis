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
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.innerplanets.InnerPlanetChunkGenerator
import org.cbi.orbitalis.minecraft.innerplanets.NativeInnerPlanets

@PrefixGameTestTemplate(false)
object InnerPlanetGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "inner_planet_terrain", timeoutTicks = 1200)
    fun nativeInnerPlanetTerrain(helper: GameTestHelper) {
        for (key in NativeInnerPlanets.dimensions) {
            val world = checkNotNull(helper.level.server.getLevel(key))
            val generator = world.chunkSource.generator as InnerPlanetChunkGenerator
            val terrain = generator.terrain
            world.getChunk(0, 0)
            val top = checkNotNull(terrain.height(0, 0))
            helper.assertTrue(!world.getBlockState(BlockPos(0, top, 0)).isAir, "Native inner planet omitted surface: $key")
            helper.assertTrue(world.getBlockState(BlockPos(0, top + 1, 0)).isAir, "Native inner planet height mismatch: $key")
            helper.assertTrue(
                world.getBlockState(BlockPos(0, -64, 0)).`is`(Blocks.BASALT),
                "Native inner planet bottom is not natural basalt: $key",
            )
            helper.assertTrue(!world.getBlockState(BlockPos(0, -64, 0)).`is`(Blocks.BEDROCK), "Inner planet has bedrock: $key")
            val outside = kotlin.math.ceil(terrain.halfSize).toInt() + 16
            world.getChunk(Math.floorDiv(outside, 16), 0)
            helper.assertTrue(world.getBlockState(BlockPos(outside, 64, 0)).isAir, "Exterior terrain was generated: $key")
            helper.assertTrue(world.getBlockState(BlockPos(outside, -64, 0)).isAir, "Exterior floor was generated: $key")
        }
        helper.succeed()
    }
}
