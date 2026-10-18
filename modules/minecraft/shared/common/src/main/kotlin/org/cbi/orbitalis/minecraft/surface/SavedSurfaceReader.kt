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
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.storage.RegionFileVersion
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Disposable read-only Anvil snapshots. Never opens a writer, creates a file or installs a chunk. */
object SavedSurfaceReader {
    private const val SECTOR_BYTES = 4096
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun read(
        directory: Path,
        pos: ChunkPos,
    ): CompoundTag? {
        val path = directory.resolve("r.${Math.floorDiv(pos.x, 32)}.${Math.floorDiv(pos.z, 32)}.mca")
        if (!Files.isRegularFile(path)) return null
        FileChannel.open(path, StandardOpenOption.READ).use { file ->
            val slot = (pos.x and 31) + (pos.z and 31) * 32

            fun word(offset: Long): Int {
                val value = ByteBuffer.allocate(4)
                if (!readFully(file, value, offset)) return 0
                return value.flip().int
            }
            val location = word(slot * 4L)
            val stamp = word(SECTOR_BYTES + slot * 4L)
            val offset = (location ushr 8).toLong() * SECTOR_BYTES
            val sectors = location and 255
            if (offset < SECTOR_BYTES * 2 || sectors == 0) return null
            val header = ByteBuffer.allocate(5)
            if (!readFully(file, header, offset)) return null
            header.flip()
            val length = header.int
            val compression = header.get().toInt() and 255
            // Custom codecs are not part of the vanilla Anvil contract; leave their presentation unknown.
            val codec = (compression and 127).takeIf { it in 1..4 }?.let(RegionFileVersion::fromId) ?: return null
            if (length < 1 || length > MAX_BYTES) return null
            val bytes =
                if (compression and 128 != 0) {
                    if (length != 1) return null
                    val external = directory.resolve("c.${pos.x}.${pos.z}.mcc")
                    if (!Files.isRegularFile(external) || Files.size(external) > MAX_BYTES) return null
                    FileChannel.open(external, StandardOpenOption.READ).use { input ->
                        val buffer = ByteBuffer.allocate(input.size().toInt())
                        if (!readFully(input, buffer, 0L)) return null
                        buffer.array()
                    }
                } else {
                    if (length.toLong() + 4 > sectors.toLong() * SECTOR_BYTES) return null
                    val buffer = ByteBuffer.allocate(length - 1)
                    if (!readFully(file, buffer, offset + 5)) return null
                    buffer.array()
                }
            val tag =
                DataInputStream(codec.wrap(ByteArrayInputStream(bytes))).use {
                    NbtIo.read(it, NbtAccounter.create(MAX_BYTES.toLong()))
                }
            // A concurrent native save wins. Changed entries are retried on a later scan.
            if (word(slot * 4L) != location || word(SECTOR_BYTES + slot * 4L) != stamp) return null
            return tag.takeIf { it.getInt("xPos") == pos.x && it.getInt("zPos") == pos.z }
        }
    }

    private fun readFully(
        file: FileChannel,
        buffer: ByteBuffer,
        offset: Long,
    ): Boolean {
        while (buffer.hasRemaining()) {
            if (file.read(buffer, offset + buffer.position()) <= 0) return false
        }
        return true
    }
}
