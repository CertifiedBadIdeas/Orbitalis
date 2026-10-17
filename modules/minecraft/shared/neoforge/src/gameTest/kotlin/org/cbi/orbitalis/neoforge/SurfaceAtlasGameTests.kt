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

import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import org.cbi.orbitalis.minecraft.surface.SurfaceAtlasData
import org.cbi.orbitalis.minecraft.surface.SurfaceAtlasPayload
import java.util.UUID

@PrefixGameTestTemplate(false)
object SurfaceAtlasGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "atlas", timeoutTicks = 100)
    fun nativeSurfaceAtlasPersistence(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        // The harness places its structures outside the finite planet; explicitly own a small
        // fixture chunk inside the physical footprint instead of sampling the remote template.
        val chunk = level.getChunkAt(BlockPos(256, 160, 256))
        val data = SurfaceAtlasData.get(level)

        fun roof(
            y: Int,
            gold: Boolean,
        ) {
            for (x in 0..15) {
                for (z in 0..15) {
                    level.setBlock(
                        BlockPos(chunk.pos.minBlockX + x, y, chunk.pos.minBlockZ + z),
                        (if (gold) Blocks.GOLD_BLOCK else Blocks.IRON_BLOCK).defaultBlockState(),
                        2,
                    )
                }
            }
        }
        roof(160, true)
        data.capture(level, chunk)
        val first = data.atlas.all().single { it.x == chunk.pos.x && it.z == chunk.pos.z }
        helper.assertTrue(first.height == 161, "Native heightmap did not determine relief")
        helper.assertTrue(
            first.color ==
                Blocks.GOLD_BLOCK
                    .defaultBlockState()
                    .getMapColor(level, BlockPos(chunk.pos.minBlockX, 160, chunk.pos.minBlockZ))
                    .col,
            "Native map colour lost",
        )
        roof(184, false)
        data.capture(level, chunk)
        val changed = data.atlas.all().single { it.key == first.key }
        helper.assertTrue(changed.height == 185 && changed.color != first.color, "Loaded terrain change did not refresh atlas")
        level.dataStorage.save()
        net.neoforged.neoforge.common.IOUtilities
            .waitUntilIOWorkerComplete()
        val storage =
            DimensionDataStorage(
                level.server
                    .getWorldPath(LevelResource.ROOT)
                    .resolve("data")
                    .toFile(),
                level.server.fixerUpper,
                level.registryAccess(),
            )
        val reloaded =
            storage.computeIfAbsent(
                SavedData.Factory(::SurfaceAtlasData, { tag, _ -> SurfaceAtlasData.load(tag) }, null),
                SurfaceAtlasData.DATA_NAME,
            )
        helper.assertTrue(reloaded.atlas.all().contains(changed), "Atlas did not survive native disk save/reload")
        val future = SurfaceAtlasData.load(CompoundTag().apply { putInt("Version", 99) })
        helper.assertTrue(!future.supported && !future.capture(level, chunk) && !future.isDirty, "Unknown atlas version became writable")
        val malformed =
            SurfaceAtlasData.load(
                CompoundTag().apply {
                    putInt("Version", 1)
                    putIntArray("Samples", intArrayOf(999999, 0, 100, 0, first.x, first.z, 50, 0x123456))
                },
            )
        helper.assertTrue(malformed.atlas.size == 1, "Malformed atlas record accepted or valid record lost")
        val ungenerated = level.chunkSource.getChunkNow(300, 300)
        data.capture(level, chunk)
        helper.assertTrue(level.chunkSource.getChunkNow(300, 300) === ungenerated, "Sampling caused unrelated terrain loading")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "atlas", timeoutTicks = 100)
    fun surfaceAtlasPacketBounds(helper: GameTestHelper) {
        val sample = SurfaceAtlas.Sample(-313, 312, 80, 0x12ef34)
        val payload = SurfaceAtlasPayload(UUID.randomUUID(), true, 123, listOf(sample))
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            SurfaceAtlasPayload.CODEC.encode(buffer, payload)
            helper.assertTrue(
                SurfaceAtlasPayload.CODEC.decode(buffer) == payload && buffer.readableBytes() == 0,
                "Atlas packet roundtrip failed",
            )
            buffer.clear()
            buffer
                .writeUUID(payload.session)
                .writeBoolean(false)
                .writeLong(1)
                .writeVarInt(257)
            var rejected = false
            try {
                SurfaceAtlasPayload.CODEC.decode(buffer)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Oversized atlas packet accepted")
        } finally {
            buffer.release()
        }
        helper.succeed()
    }
}
