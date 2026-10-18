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
import com.mojang.logging.LogUtils
import net.minecraft.Util
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.surface.SurfaceAtlasPayload
import org.joml.Matrix4f
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Independently replaceable sector buffers shared by orbital view and map. GPU work stays on render thread. */
object SurfacePlanetRenderer {
    private var sectors = SurfaceMeshSectors()
    private var session: UUID? = null
    private var receivedRevision = -1L
    private val meshes = linkedMapOf<SurfacePlanetMesh.Sector, VertexBuffer>()
    private val uploaded = mutableMapOf<SurfacePlanetMesh.Sector, Long>()
    private val pending = linkedMapOf<SurfacePlanetMesh.Sector, CompletableFuture<PreparedMesh>>()
    private var body: VertexBuffer? = null
    private var generation = 0L
    private var retryAfter = 0L
    private var nextSector = 0

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
        faces: Sequence<SpaceScene.Face>,
        revision: Long,
        generation: Long,
    ): PreparedMesh {
        val storage = ByteBufferBuilder(1 shl 16)
        try {
            val buffer = BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for (face in faces) {
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

    private fun upload(
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

    private fun discard() {
        generation++
        pending.values.forEach { future -> future.thenAccept { it.close() } }
        pending.clear()
        meshes.values.forEach(VertexBuffer::close)
        meshes.clear()
        uploaded.clear()
        body?.close()
        body = null
        retryAfter = 0L
        sectors = SurfaceMeshSectors()
    }

    fun accept(payload: SurfaceAtlasPayload) {
        RenderSystem.assertOnRenderThread()
        if (payload.reset) {
            discard()
            session = payload.session
            receivedRevision = -1L
        }
        if (payload.session != session || payload.revision < receivedRevision) return
        payload.samples.forEach(sectors::put)
        receivedRevision = payload.revision
    }

    fun clear() {
        RenderSystem.assertOnRenderThread()
        PlanetCloudRenderer.clear()
        discard()
        session = null
        receivedRevision = -1L
    }

    private fun updateMesh() {
        if (body == null) {
            body = upload(prepare(SpaceScene.body.asSequence().filter { it.normal.y <= 0.0 }, 0L, generation))
            // A few coarse quads per sector cover unknown data immediately; no whole fine-mesh build on the render thread.
            for (sector in SurfacePlanetMesh.sectors) {
                meshes[sector] = upload(prepare(SurfacePlanetMesh.coarse(sector), -1L, generation))
            }
        }
        val ready = pending.iterator()
        var uploads = 0
        while (ready.hasNext() && uploads < 2) {
            val (sector, future) = ready.next()
            if (!future.isDone) continue
            ready.remove()
            uploads++
            try {
                val prepared = future.join()
                if (prepared.generation == generation) {
                    meshes[sector] = upload(prepared, meshes[sector])
                    uploaded[sector] = prepared.revision
                } else {
                    prepared.close()
                }
            } catch (error: Exception) {
                LogUtils.getLogger().warn("Could not prepare planet LOD sector {}; retaining previous mesh", sector, error)
                retryAfter = System.nanoTime() + 1_000_000_000L
            }
        }
        if (System.nanoTime() < retryAfter) return
        repeat(SurfacePlanetMesh.sectors.size) {
            if (pending.size >= 4) return
            val sector = SurfacePlanetMesh.sectors[nextSector]
            nextSector = (nextSector + 1) % SurfacePlanetMesh.sectors.size
            val revision = sectors.revision(sector)
            if (sector in pending || uploaded[sector] == revision) return@repeat
            val samples = sectors.snapshot(sector)
            val currentGeneration = generation
            pending[sector] =
                CompletableFuture.supplyAsync({
                    val snapshot = SurfaceAtlas()
                    samples.forEach(snapshot::put)
                    prepare(SurfacePlanetMesh.faces(snapshot, sector), revision, currentGeneration)
                }, Util.backgroundExecutor())
        }
    }

    /** Draw the same cached planet into the caller's viewport with an independent camera. */
    fun renderScene(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        clouds: Boolean = true,
    ) {
        updateMesh()
        val modelView = Matrix4f(view).scale(scale.toFloat()).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat())
        for (mesh in meshes.values + listOfNotNull(body)) {
            mesh.bind()
            try {
                mesh.drawWithShader(modelView, projection, checkNotNull(GameRenderer.getPositionColorShader()))
            } finally {
                VertexBuffer.unbind()
            }
        }
        if (clouds) PlanetCloudRenderer.render(observer, view, projection, scale)
    }
}
