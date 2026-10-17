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
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.storage.ChunkSerializer
import net.minecraft.world.level.chunk.storage.ChunkStorage
import net.minecraft.world.level.chunk.storage.RegionStorageInfo
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.space.SpaceWorldData
import org.cbi.orbitalis.minecraft.space.SpaceWorlds
import org.cbi.orbitalis.minecraft.space.VacuumChunkData
import org.cbi.orbitalis.minecraft.space.VacuumChunkGenerator
import java.nio.file.Files

@PrefixGameTestTemplate(false)
object VacuumStorageGameTests {
    private data class ModelCase(
        val name: String,
        val vacuum: Boolean,
        val payloadSize: Int,
        val expected: Boolean,
    )

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 200, batch = "sparse_storage")
    fun vacuumStorageAndConstruction(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        helper.assertTrue(SpaceWorlds.objects(level), "Fresh orbital world did not select objects mode")
        helper.assertTrue(level.chunkSource.generator is VacuumChunkGenerator, "Vacuum generator was not decoded")
        helper.assertTrue(!SpaceWorlds.objects(helper.level), "Ordinary test world became sparse")
        val chunk = level.getChunk(5200, 5200)
        val pos = BlockPos(5200 * 16, 64, 5200 * 16)
        helper.assertTrue(!level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3), "Static block accepted in vacuum")
        helper.assertTrue(level.getBlockState(pos).isAir, "Static block survived rejection")
        val pristine = ChunkSerializer.write(level, chunk)
        helper.assertTrue(VacuumChunkData.regenerable(pristine), "Native empty chunk not recognized: $pristine")
        val folder = SpaceWorlds.regionFolder(level)
        val info = RegionStorageInfo("orbitalis-vacuum-test", level.dimension(), "chunk")

        fun storage(path: java.nio.file.Path = folder) = ChunkStorage(info, path, level.server.fixerUpper, true)
        val vacant = ChunkPos(1_000_000, 1_000_000)
        val retained = ChunkPos(1_000_001, 1_000_000)
        val cleared = ChunkPos(1_000_002, 1_000_000)

        fun at(
            pos: ChunkPos,
            tag: CompoundTag,
        ) = tag.copy().apply {
            putInt("xPos", pos.x)
            putInt("zPos", pos.z)
        }
        val unknown = pristine.copy().apply { putString("another_mod:payload", "preserve me") }
        helper.assertTrue(!VacuumChunkData.regenerable(unknown), "Unknown payload classified as disposable")
        val attachment = pristine.copy().apply { put("neoforge:attachments", CompoundTag().apply { putInt("value", 7) }) }
        helper.assertTrue(!VacuumChunkData.regenerable(attachment), "Attachment classified as disposable")
        val ticking = pristine.copy().apply { put("block_ticks", ListTag().apply { add(CompoundTag().apply { putInt("t", 1) }) }) }
        helper.assertTrue(!VacuumChunkData.regenerable(ticking), "Pending tick classified as disposable")
        val blocks = pristine.copy()
        val section =
            CompoundTag().apply {
                putByte("Y", 4)
                put(
                    "block_states",
                    CompoundTag().apply {
                        put("palette", ListTag().apply { add(CompoundTag().apply { putString("Name", "minecraft:stone") }) })
                    },
                )
            }
        blocks.put("sections", ListTag().apply { add(section) })
        helper.assertTrue(!VacuumChunkData.regenerable(blocks), "Real blocks classified as disposable")
        val biome = pristine.copy()
        biome.put(
            "sections",
            ListTag().apply {
                add(
                    CompoundTag().apply {
                        putByte("Y", 4)
                        put(
                            "biomes",
                            CompoundTag().apply { put("palette", ListTag().apply { add(StringTag.valueOf("minecraft:plains")) }) },
                        )
                    },
                )
            },
        )
        helper.assertTrue(!VacuumChunkData.regenerable(biome), "Edited biome classified as disposable")
        biome.putString("Status", "minecraft:structure_references")
        helper.assertTrue(VacuumChunkData.regenerable(biome), "Native pre-biome placeholder was treated as a biome edit")
        biome.putString("Status", "minecraft:biomes")
        helper.assertTrue(!VacuumChunkData.regenerable(biome), "Completed biome generation lost its edit")
        val inhabited = pristine.copy().apply { putLong("InhabitedTime", 1L) }
        helper.assertTrue(!VacuumChunkData.regenerable(inhabited), "Inhabited time was discarded")
        val light =
            pristine.copy().apply {
                put(
                    "sections",
                    ListTag().apply {
                        add(
                            CompoundTag().apply {
                                putByte("Y", 4)
                                putByteArray("BlockLight", byteArrayOf(1))
                            },
                        )
                    },
                )
            }
        helper.assertTrue(!VacuumChunkData.regenerable(light), "Nonzero block light was discarded")
        storage().use { disk ->
            disk.write(vacant, at(vacant, pristine)).join()
            disk.write(retained, at(retained, unknown)).join()
            disk.write(cleared, at(cleared, attachment)).join()
            disk.flushWorker()
        }
        storage().use { disk ->
            helper.assertTrue(disk.read(vacant).join().isEmpty, "Pristine vacuum acquired a durable region entry")
            helper.assertTrue(
                disk
                    .read(retained)
                    .join()
                    .orElseThrow()
                    .getString("another_mod:payload") == "preserve me",
                "Unknown payload lost",
            )
            helper.assertTrue(
                disk
                    .read(cleared)
                    .join()
                    .orElseThrow()
                    .contains("neoforge:attachments"),
                "Attachment lost",
            )
            disk.write(cleared, at(cleared, pristine)).join()
            disk.flushWorker()
        }
        storage().use { disk ->
            val restored = disk.read(cleared).join().orElseThrow()
            helper.assertTrue(!restored.contains("neoforge:attachments"), "Skipping clear resurrected old data")
            helper.assertTrue(VacuumChunkData.regenerable(restored), "Existing empty entry was not replaced correctly")
        }
        val entities = folder.parent.resolve("entities")
        helper.assertTrue(!SpaceWorlds.sparseRegion(entities), "Entity store was registered as disposable terrain")
        storage(entities).use { disk ->
            disk.write(vacant, at(vacant, pristine)).join()
            disk.flushWorker()
        }
        storage(entities).use { disk -> helper.assertTrue(disk.read(vacant).join().isPresent, "Entity-store write was suppressed") }
        println("ORBITALIS_SPARSE_STORAGE pristine=omitted unknown=retained attachments=retained clear=written entities=retained")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100, batch = "sparse_metadata")
    fun vacuumMetadataCompatibility(helper: GameTestHelper) {
        val level = helper.level
        val space = checkNotNull(level.server.getLevel(FlatPlanetSpace.SPACE))
        helper.assertTrue(
            dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
                .of(space)
                .ignoreChunks(),
            "Default native vacuum policy missing",
        )
        org.cbi.orbitalis.minecraft.integration.sable.SableVacuumWorld
            .close(level.server)
        helper.assertTrue(
            !dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
                .of(space)
                .ignoreChunks(),
            "Vacuum policy leaked beyond server lifecycle",
        )
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
            net.neoforged.neoforge.event
                .OnDatapackSyncEvent(level.server.playerList, null),
        )
        helper.assertTrue(
            dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
                .of(space)
                .ignoreChunks(),
            "Datapack synchronization did not restore vacuum policy",
        )
        val folder = Files.createTempDirectory("orbitalis-space-model-").toFile()
        try {
            val factory = SavedData.Factory({ SpaceWorldData.create(true) }, { tag, _ -> SpaceWorldData.load(tag) }, null)
            val storage = DimensionDataStorage(folder, level.server.fixerUpper, level.registryAccess())
            storage.computeIfAbsent(factory, SpaceWorldData.NAME)
            storage.save()
            net.neoforged.neoforge.common.IOUtilities
                .waitUntilIOWorkerComplete()
            val fresh = DimensionDataStorage(folder, level.server.fixerUpper, level.registryAccess())
            helper.assertTrue(fresh.computeIfAbsent(factory, SpaceWorldData.NAME).objects, "Objects model did not survive disk reload")
            helper.assertTrue(!SpaceWorldData.create(false).objects, "Legacy model enabled sparse terrain")
            for ((name, vacuum, payloadSize, expected) in listOf(
                ModelCase("fresh", true, 0, true),
                ModelCase("header", true, 8192, true),
                ModelCase("legacy", true, 12288, false),
                ModelCase("datapack", false, 0, false),
            )) {
                val directory = folder.toPath().resolve(name)
                val regions = directory.resolve("region")
                Files.createDirectories(regions)
                if (payloadSize > 0) Files.write(regions.resolve("r.0.0.mca"), ByteArray(payloadSize))
                val modelStorage = DimensionDataStorage(directory.resolve("data").toFile(), level.server.fixerUpper, level.registryAccess())
                helper.assertTrue(SpaceWorldData.open(modelStorage, vacuum, regions).objects == expected, "Unsafe initial model: $name")
            }
            val future =
                CompoundTag().apply {
                    putInt("Version", 2)
                    putString("Model", "objects")
                    putString("Future", "opaque")
                }
            val loaded = SpaceWorldData.load(future)
            helper.assertTrue(!loaded.objects, "Unknown schema enabled sparse terrain")
            helper.assertTrue(loaded.save(CompoundTag(), level.registryAccess()) == future, "Unknown schema was rewritten")
            helper.assertTrue(!SpaceWorldData.load(CompoundTag()).objects, "Missing schema enabled sparse terrain")
        } finally {
            folder.deleteRecursively()
        }
        helper.succeed()
    }
}
