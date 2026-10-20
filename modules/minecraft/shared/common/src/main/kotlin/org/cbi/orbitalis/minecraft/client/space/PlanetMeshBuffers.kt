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

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat

/** Shared CPU mesh storage and render-thread upload ownership for celestial surface sectors. */
object PlanetMeshBuffers {
    class PreparedMesh(
        val storage: ByteBufferBuilder,
        val data: MeshData,
        val revision: Long,
        val generation: Long,
    ) : AutoCloseable {
        override fun close() {
            data.close()
            storage.close()
        }
    }

    fun prepare(
        faces: Sequence<SpaceScene.Face>,
        revision: Long,
        generation: Long,
    ): PreparedMesh {
        val storage = ByteBufferBuilder(1 shl 16)
        try {
            val buffer = BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL)
            for (face in faces) {
                val r = face.color shr 16 and 255
                val g = face.color shr 8 and 255
                val b = face.color and 255
                for (v in face.vertices) {
                    buffer
                        .addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
                        .setColor(r, g, b, (face.ambient.coerceIn(0.0, 1.0) * 255).toInt())
                        .setNormal(face.normal.x.toFloat(), face.normal.y.toFloat(), face.normal.z.toFloat())
                }
            }
            return PreparedMesh(storage, buffer.buildOrThrow(), revision, generation)
        } catch (error: Throwable) {
            storage.close()
            throw error
        }
    }

    fun upload(
        prepared: PreparedMesh,
        existing: VertexBuffer? = null,
    ): VertexBuffer {
        prepared.use {
            val gpu = existing ?: VertexBuffer(VertexBuffer.Usage.STATIC)
            gpu.bind()
            try {
                gpu.upload(it.data)
            } catch (error: Throwable) {
                if (existing == null) gpu.close()
                throw error
            } finally {
                VertexBuffer.unbind()
            }
            return gpu
        }
    }
}
