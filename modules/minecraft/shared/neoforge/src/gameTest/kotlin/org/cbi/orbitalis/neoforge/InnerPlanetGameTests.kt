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
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenSurfaceBounds
import org.cbi.orbitalis.minecraft.innerplanets.InnerPlanetChunkGenerator
import org.cbi.orbitalis.minecraft.innerplanets.NativeInnerPlanets
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain

@PrefixGameTestTemplate(false)
object InnerPlanetGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "inner_planet_terrain", timeoutTicks = 1200)
    fun nativeInnerPlanetTerrain(helper: GameTestHelper) {
        for (key in NativeInnerPlanets.dimensions) {
            val world = checkNotNull(helper.level.server.getLevel(key))
            val generator = world.chunkSource.generator as InnerPlanetChunkGenerator
            val terrain = generator.terrain
            val bounds = checkNotNull(WorldgenSurfaceBounds.get(world))
            helper.assertTrue(bounds == WorldgenSurfaceBounds.square(terrain.halfSize), "Voxy omitted configured inner planet bounds")
            helper.assertTrue(FiniteSurfaceTerrain.bounds(world) == null, "Voxy bounds changed native terrain interception")
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

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "inner_planet_packet", timeoutTicks = 100)
    fun innerPlanetSeedPacket(helper: GameTestHelper) {
        val profile =
            org.cbi.orbitalis.core.orbit
                .SolarSystemProfile(11.0, selectedEarthWidth = 2000)
        val seeds =
            mapOf(
                org.cbi.orbitalis.core.orbit.SolarSystemProfile.MERCURY to Long.MIN_VALUE,
                org.cbi.orbitalis.core.orbit.SolarSystemProfile.VENUS to Long.MAX_VALUE,
            )
        for (selected in listOf(emptyMap(), seeds.filterKeys { it.endsWith("mercury") }, seeds)) {
            val buffer =
                net.minecraft.network.FriendlyByteBuf(
                    io.netty.buffer.Unpooled
                        .buffer(),
                )
            try {
                val payload =
                    org.cbi.orbitalis.minecraft.flight
                        .PlayerGravityPayload(11.0, 0L, profile, 123L, true, selected)
                org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                    .encode(buffer, payload)
                val decoded =
                    org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                        .decode(buffer)
                helper.assertTrue(decoded == payload && !buffer.isReadable, "Derived terrain seeds/profile did not roundtrip")
                buffer.readerIndex(0)
                buffer.setByte(24, 4)
                var rejected = false
                try {
                    org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                        .decode(buffer)
                } catch (_: IllegalArgumentException) {
                    rejected = true
                }
                helper.assertTrue(rejected, "Unknown seed flags accepted")
            } finally {
                buffer.release()
            }
        }
        helper.succeed()
    }
}
