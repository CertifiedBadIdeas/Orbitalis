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
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.Util
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.surface.SurfaceAtlasPayload
import org.joml.Matrix4f
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Private depth attachment gives relief self-occlusion without placing sky depth in front of ships. */
object SurfacePlanetRenderer {
    private var atlas = SurfaceAtlas()
    private var session: UUID? = null
    private var receivedRevision = -1L
    private var meshRevision = -1L
    private var lastBuild = 0L
    private var mesh: VertexBuffer? = null
    private var generation = 0L
    private var pending: CompletableFuture<PreparedMesh>? = null

    private class PreparedMesh(
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

    private fun prepare(
        samples: List<SurfaceAtlas.Sample>,
        revision: Long,
        generation: Long,
    ): PreparedMesh {
        val snapshot = SurfaceAtlas()
        samples.forEach(snapshot::put)
        val storage = ByteBufferBuilder(1 shl 20)
        try {
            val buffer = BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for (face in SurfacePlanetMesh.faces(snapshot)) {
                val light = 0.18 + 0.82 * maxOf(0.0, face.normal.dot(SpaceScene.SUN_DIRECTION))
                val r = ((face.color shr 16 and 255) * light).toInt()
                val g = ((face.color shr 8 and 255) * light).toInt()
                val b = ((face.color and 255) * light).toInt()
                for (v in face.vertices) buffer.addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat()).setColor(r, g, b, 255)
            }
            return PreparedMesh(storage, buffer.buildOrThrow(), revision, generation)
        } catch (error: Throwable) {
            storage.close()
            throw error
        }
    }

    private fun upload(prepared: PreparedMesh) {
        prepared.use {
            val gpu = mesh ?: VertexBuffer(VertexBuffer.Usage.STATIC).also { mesh = it }
            gpu.bind()
            try {
                gpu.upload(it.data)
            } finally {
                VertexBuffer.unbind()
            }
            meshRevision = it.revision
        }
    }

    fun accept(payload: SurfaceAtlasPayload) {
        RenderSystem.assertOnRenderThread()
        if (payload.reset) {
            generation++
            atlas = SurfaceAtlas()
            session = payload.session
            receivedRevision = -1L
            meshRevision = -1L
            lastBuild = 0L
        }
        if (payload.session != session || payload.revision < receivedRevision) return
        for (sample in payload.samples) atlas.put(sample)
        receivedRevision = payload.revision
    }

    fun clear() {
        RenderSystem.assertOnRenderThread()
        generation++
        pending?.thenAccept { it.close() }
        pending = null
        atlas = SurfaceAtlas()
        session = null
        receivedRevision = -1L
        meshRevision = -1L
        mesh?.close()
        mesh = null
    }

    private fun updateMesh() {
        val now = System.nanoTime()
        if (mesh == null) upload(prepare(emptyList(), 0L, generation))
        pending?.let { future ->
            if (future.isDone) {
                pending = null
                val prepared = future.join()
                if (prepared.generation == generation) upload(prepared) else prepared.close()
            }
        }
        if (pending == null && meshRevision != atlas.revision && now - lastBuild > 500_000_000L) {
            val samples = atlas.all().toList()
            val revision = atlas.revision
            val currentGeneration = generation
            pending = CompletableFuture.supplyAsync({ prepare(samples, revision, currentGeneration) }, Util.backgroundExecutor())
            lastBuild = now
        }
    }

    /** Draw the same cached planet into the caller's viewport with an independent camera. */
    fun renderScene(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
    ) {
        updateMesh()
        val modelView = Matrix4f(view).scale(scale.toFloat()).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat())
        checkNotNull(mesh).bind()
        try {
            checkNotNull(mesh).drawWithShader(modelView, projection, checkNotNull(GameRenderer.getPositionColorShader()))
        } finally {
            VertexBuffer.unbind()
        }
    }
}
