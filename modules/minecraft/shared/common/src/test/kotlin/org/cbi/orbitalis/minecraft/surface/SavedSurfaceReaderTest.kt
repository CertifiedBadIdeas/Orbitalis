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

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.storage.RegionFileVersion
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedSurfaceReaderTest {
    private fun fixture(
        directory: Path,
        codec: RegionFileVersion,
        external: Boolean = false,
        tagX: Int = -1,
    ): ChunkPos {
        val pos = ChunkPos(-1, 32)
        val output = ByteArrayOutputStream()
        DataOutputStream(codec.wrap(output)).use { stream ->
            NbtIo.write(
                CompoundTag().apply {
                    putInt("xPos", tagX)
                    putInt("zPos", pos.z)
                    putString("Marker", "terrain")
                },
                stream,
            )
        }
        val bytes = output.toByteArray()
        val region = ByteBuffer.allocate(3 * 4096)
        val slot = 31
        region.putInt(slot * 4, (2 shl 8) or 1)
        region.putInt(4096 + slot * 4, 123)
        region.position(8192)
        region.putInt(if (external) 1 else bytes.size + 1)
        region.put((codec.id or if (external) 128 else 0).toByte())
        if (external) Files.write(directory.resolve("c.-1.32.mcc"), bytes) else region.put(bytes)
        Files.write(directory.resolve("r.-1.1.mca"), region.array())
        return pos
    }

    private fun temporary(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("orbitalis-atlas-reader-")
        try {
            test(directory)
        } finally {
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun nativeCompressionFormatsAndExternalEntriesAreReadWithoutWritingFiles() =
        temporary { directory ->
            for (codec in listOf(
                RegionFileVersion.VERSION_GZIP,
                RegionFileVersion.VERSION_DEFLATE,
                RegionFileVersion.VERSION_NONE,
                RegionFileVersion.VERSION_LZ4,
            )) {
                for (external in listOf(false, true)) {
                    val pos = fixture(directory, codec, external)
                    val path = directory.resolve("r.-1.1.mca")
                    val before = Files.readAllBytes(path)
                    val stamp = Files.getLastModifiedTime(path)
                    assertEquals("terrain", SavedSurfaceReader.read(directory, pos)?.getString("Marker"))
                    assertEquals(before.toList(), Files.readAllBytes(path).toList())
                    assertEquals(stamp, Files.getLastModifiedTime(path))
                }
            }
        }

    @Test fun missingTruncatedWrongCoordinateAndUnsupportedEntriesStayUnknown() =
        temporary { directory ->
            assertNull(SavedSurfaceReader.read(directory, ChunkPos(0, 0)))
            val pos = fixture(directory, RegionFileVersion.VERSION_NONE, tagX = 0)
            assertNull(SavedSurfaceReader.read(directory, pos))
            val path = directory.resolve("r.-1.1.mca")
            Files.write(path, byteArrayOf(0))
            assertNull(SavedSurfaceReader.read(directory, pos))
            fixture(directory, RegionFileVersion.VERSION_NONE)
            val bytes = Files.readAllBytes(path)
            bytes[8196] = 127
            Files.write(path, bytes)
            assertNull(SavedSurfaceReader.read(directory, pos))
        }

    @Test fun independentReadersCanReadTheSameRegionConcurrently() =
        temporary { directory ->
            val pos = fixture(directory, RegionFileVersion.VERSION_DEFLATE)
            val workers =
                java.util.concurrent.Executors
                    .newFixedThreadPool(2)
            try {
                val reads =
                    (0 until 16).map {
                        workers.submit<String?> { SavedSurfaceReader.read(directory, pos)?.getString("Marker") }
                    }
                assertEquals(List(16) { "terrain" }, reads.map { it.get(5, java.util.concurrent.TimeUnit.SECONDS) })
            } finally {
                workers.shutdownNow()
            }
        }

    @Test fun oversizedOrMissingExternalEntriesDoNotAllocateTheirDeclaredPayload() =
        temporary { directory ->
            val pos = fixture(directory, RegionFileVersion.VERSION_NONE, external = true)
            Files.delete(directory.resolve("c.-1.32.mcc"))
            assertNull(SavedSurfaceReader.read(directory, pos))
            fixture(directory, RegionFileVersion.VERSION_NONE)
            val path = directory.resolve("r.-1.1.mca")
            val bytes = Files.readAllBytes(path)
            ByteBuffer.wrap(bytes).putInt(8192, Int.MAX_VALUE)
            Files.write(path, bytes)
            assertNull(SavedSurfaceReader.read(directory, pos))
        }
}
