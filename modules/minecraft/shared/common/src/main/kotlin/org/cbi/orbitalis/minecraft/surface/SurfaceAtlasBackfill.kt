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

import com.mojang.logging.LogUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.storage.LevelResource
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** Two read-only disk workers; all world-dependent decoding and atlas mutations stay on server thread. */
class SurfaceAtlasBackfill(
    private val level: ServerLevel,
    private val data: SurfaceAtlasData,
) : AutoCloseable {
    private val workers =
        Executors.newFixedThreadPool(2) { task ->
            Thread(task, "Orbitalis surface atlas reader").apply { isDaemon = true }
        }
    private val directory = level.server.getWorldPath(LevelResource.ROOT).resolve("region")
    private var closed = false
    private var index: CompletableFuture<List<ChunkPos>>? = null
    private var remaining = emptyList<ChunkPos>().iterator()
    private val pending = linkedMapOf<ChunkPos, CompletableFuture<CompoundTag?>>()
    private var nextScan = 0
    private var warned = false

    fun tick() {
        if (closed || !data.supported) return
        val tick = level.server.tickCount
        if (index == null && !remaining.hasNext() && pending.isEmpty() && tick >= nextScan) {
            index = CompletableFuture.supplyAsync({ entries(directory) }, workers)
            nextScan = tick + 1200
        }
        index?.let { future ->
            if (future.isDone) {
                try {
                    remaining = future.join().iterator()
                } catch (e: Exception) {
                    warn(e)
                }
                index = null
            }
        }
        // Decode on the server thread (native map colour hooks may read world state),
        // with both a count and elapsed-time budget. Disk I/O itself is never awaited.
        val deadline = System.nanoTime() + 2_000_000L
        val ready = pending.iterator()
        var decoded = 0
        while (ready.hasNext() && decoded < 8 && System.nanoTime() < deadline) {
            val (pos, future) = ready.next()
            if (!future.isDone) continue
            ready.remove()
            decoded++
            try {
                val tag = future.join()
                if (tag != null && !data.atlas.contains(pos.x, pos.z) && tag.getInt("xPos") == pos.x && tag.getInt("zPos") == pos.z) {
                    SavedSurfaceSampler.sample(level, tag)?.let(data::record)
                }
            } catch (e: Exception) {
                warn(e)
            }
        }
        var examined = 0
        while (remaining.hasNext() && pending.size < 8 && examined++ < 256) {
            val pos = remaining.next()
            if (!data.atlas.contains(pos.x, pos.z)) {
                pending[pos] =
                    CompletableFuture.supplyAsync({ SavedSurfaceReader.read(directory, pos) }, workers)
            }
        }
    }

    override fun close() {
        closed = true
        index?.cancel(true)
        index = null
        pending.values.forEach { it.cancel(true) }
        pending.clear()
        remaining = emptyList<ChunkPos>().iterator()
        workers.shutdownNow()
    }

    private fun warn(error: Exception) {
        if (!warned) LogUtils.getLogger().warn("Could not backfill a saved surface atlas entry; retaining fallback", error)
        warned = true
    }

    companion object {
        /** Read only location tables. Native chunk storage owns decompression and pending writes. */
        fun entries(directory: Path): List<ChunkPos> {
            if (!Files.isDirectory(directory)) return emptyList()
            val result = mutableListOf<ChunkPos>()
            Files.newDirectoryStream(directory, "r.*.*.mca").use { files ->
                for (file in files) {
                    val parts = file.fileName.toString().split('.')
                    if (parts.size != 4) continue
                    val rx = parts[1].toIntOrNull() ?: continue
                    val rz = parts[2].toIntOrNull() ?: continue
                    val regions = Math.floorDiv(SurfaceAtlas.MIN_CHUNK, 32)..Math.floorDiv(SurfaceAtlas.MAX_CHUNK, 32)
                    if (rx !in regions || rz !in regions) continue
                    val header = ByteArray(4096)
                    val count = Files.newInputStream(file).use { it.readNBytes(header, 0, header.size) }
                    if (count != header.size) continue
                    val offsets = ByteBuffer.wrap(header)
                    repeat(1024) { slot ->
                        val location = offsets.int
                        val x = rx * 32 + slot % 32
                        val z = rz * 32 + slot / 32
                        if (location ushr 8 != 0 && x in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK &&
                            z in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK
                        ) {
                            result.add(ChunkPos(x, z))
                        }
                    }
                }
            }
            return result.sortedBy { it.x * it.x + it.z * it.z }
        }
    }
}
