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

package org.cbi.orbitalis.minecraft.client.space

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.logging.LogUtils
import org.cbi.orbitalis.core.cloud.ProceduralCloudField
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Bounded pure-field workers; render thread exclusively owns uploads, LRU eviction and GPU disposal. */
internal object CloudSectorCache {
    private const val MAX_PENDING = 16
    private const val MAX_UPLOADS = 8
    private const val MAX_ENTRIES = 2048
    private const val MAX_VERTICES = 2_000_000L
    private const val MAX_BYTES = 64L * 1024L * 1024L
    private const val BYTES_PER_VERTEX = 24L

    private data class Key(
        val seed: Long,
        val x: Int,
        val z: Int,
        val step: Int,
    )

    private data class Entry(
        val buffer: VertexBuffer?,
        val vertices: Long,
    )

    private data class Pending(
        val generation: Long,
        val future: Future<List<ProceduralCloudField.Face>>,
    )

    private val entries = LinkedHashMap<Key, Entry>(128, 0.75f, true)
    private val pending = linkedMapOf<Key, Pending>()
    private val threadNumber = AtomicInteger()
    private val workers =
        ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_PENDING),
            { work -> Thread(work, "Orbitalis-cloud-field-${threadNumber.incrementAndGet()}").apply { isDaemon = true } },
        )
    private var generation = 0L
    private var vertices = 0L
    private var warned = false

    /** Null includes pending, genuinely empty and failed sectors; empty/failure markers avoid repeated work. */
    fun request(
        seed: Long,
        sectorX: Int,
        sectorZ: Int,
        step: Int,
    ): VertexBuffer? {
        RenderSystem.assertOnRenderThread()
        require(step in setOf(1, 2, 4, 8)) { "Unsupported cloud sector resolution" }
        val key = Key(seed, sectorX, sectorZ, step)
        entries[key]?.let { return it.buffer }
        if (key in pending || pending.size >= MAX_PENDING) return null
        try {
            val future =
                workers.submit<List<ProceduralCloudField.Face>> {
                    ProceduralCloudField(seed).sector(sectorX, sectorZ, step)
                }
            pending[key] = Pending(generation, future)
        } catch (error: Exception) {
            // A temporarily full executor after reset must not evict buffers already selected this frame.
            warn(error)
        }
        return null
    }

    /** Call once before this frame's requests/draws, so evicted buffers are never retained by a caller. */
    fun pumpUploads() {
        RenderSystem.assertOnRenderThread()
        val iterator = pending.iterator()
        var uploads = 0
        while (iterator.hasNext() && uploads < MAX_UPLOADS) {
            val (key, job) = iterator.next()
            if (!job.future.isDone) continue
            iterator.remove()
            uploads++
            if (job.generation != generation) continue
            try {
                val faces = job.future.get()
                val count = faces.size.toLong() * 4L
                check(count <= MAX_VERTICES && count * BYTES_PER_VERTEX <= MAX_BYTES) { "Cloud sector exceeds its GPU budget" }
                val buffer = if (faces.isEmpty()) null else upload(key, faces)
                entries[key] = Entry(buffer, count)
                vertices += count
            } catch (error: Exception) {
                entries[key] = Entry(null, 0L)
                warn(error)
            }
            evict()
        }
    }

    /** Queued work is cancelled and purged; at most two already running pure jobs may finish and be discarded. */
    fun clear() {
        RenderSystem.assertOnRenderThread()
        generation++
        pending.values.forEach { it.future.cancel(false) }
        pending.clear()
        workers.purge()
        entries.values.forEach { it.buffer?.close() }
        entries.clear()
        vertices = 0L
        warned = false
    }

    private fun upload(
        key: Key,
        faces: List<ProceduralCloudField.Face>,
    ): VertexBuffer {
        val storage = ByteBufferBuilder(1 shl 16)
        val gpu = VertexBuffer(VertexBuffer.Usage.STATIC)
        try {
            val builder = BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            val offsetX = key.x * ProceduralCloudField.SECTOR_SIZE
            val offsetZ = key.z * ProceduralCloudField.SECTOR_SIZE
            for (face in faces) {
                for (v in face.vertices) {
                    builder
                        .addVertex((v.x - offsetX).toFloat(), v.y.toFloat(), (v.z - offsetZ).toFloat())
                        .setUv(face.thickness.toFloat(), 0f)
                        .setColor(255, 255, 255, 230)
                }
            }
            builder.buildOrThrow().use { mesh ->
                gpu.bind()
                try {
                    gpu.upload(mesh)
                } finally {
                    VertexBuffer.unbind()
                }
            }
            return gpu
        } catch (error: Throwable) {
            gpu.close()
            throw error
        } finally {
            storage.close()
        }
    }

    private fun evict() {
        val iterator = entries.iterator()
        while ((entries.size > MAX_ENTRIES || vertices > MAX_VERTICES || vertices * BYTES_PER_VERTEX > MAX_BYTES) && iterator.hasNext()) {
            val entry = iterator.next().value
            iterator.remove()
            vertices -= entry.vertices
            entry.buffer?.close()
        }
    }

    private fun warn(error: Exception) {
        if (warned) return
        warned = true
        LogUtils.getLogger().warn("Could not build or upload a cloud sector; retaining empty markers until cloud cache reset", error)
    }
}
