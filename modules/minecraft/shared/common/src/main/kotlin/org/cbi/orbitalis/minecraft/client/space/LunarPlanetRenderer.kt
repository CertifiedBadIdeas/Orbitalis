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

import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.logging.LogUtils
import net.minecraft.Util
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.client.space.PlanetMeshBuffers.PreparedMesh
import org.cbi.orbitalis.minecraft.client.space.PlanetMeshBuffers.prepare
import org.cbi.orbitalis.minecraft.client.space.PlanetMeshBuffers.upload
import org.joml.Matrix4f
import java.util.concurrent.CompletableFuture

/** Bounded background sector preparation and GPU uploads, shared by space and the orbital map. */
object LunarPlanetRenderer {
    private var seed: Long? = null
    private var generation = 0L
    private var body: VertexBuffer? = null
    private val meshes = linkedMapOf<SurfacePlanetMesh.Sector, VertexBuffer>()
    private val pending = linkedMapOf<SurfacePlanetMesh.Sector, CompletableFuture<PreparedMesh>>()
    private val refined = mutableSetOf<SurfacePlanetMesh.Sector>()
    private var nextSector = 0
    private var retryAfter = 0L

    fun clear() {
        generation++
        pending.values.forEach { it.thenAccept { prepared -> prepared.close() } }
        pending.clear()
        meshes.values.forEach(VertexBuffer::close)
        meshes.clear()
        refined.clear()
        body?.close()
        body = null
        seed = null
        nextSector = 0
        retryAfter = 0L
    }

    private fun update(terrainSeed: Long) {
        if (seed != terrainSeed) {
            clear()
            seed = terrainSeed
        }
        if (body == null) {
            body = upload(prepare(LunarPlanetMesh.underside(), 0L, generation))
            for (sector in LunarPlanetMesh.sectors) meshes[sector] = upload(prepare(LunarPlanetMesh.coarse(sector), -1L, generation))
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
                    refined.add(sector)
                } else {
                    prepared.close()
                }
            } catch (error: Exception) {
                LogUtils
                    .getLogger()
                    .warn("Could not prepare lunar LOD sector {}", sector, error)
                retryAfter = System.nanoTime() + 1_000_000_000L
            }
        }
        if (System.nanoTime() < retryAfter) return
        repeat(LunarPlanetMesh.sectors.size) {
            if (pending.size >= 4) return
            val sector = LunarPlanetMesh.sectors[nextSector]
            nextSector = (nextSector + 1) % LunarPlanetMesh.sectors.size
            if (sector in refined || sector in pending) return@repeat
            val current = generation
            pending[sector] =
                CompletableFuture.supplyAsync(
                    { prepare(LunarPlanetMesh.faces(terrainSeed, sector), 0L, current) },
                    Util.backgroundExecutor(),
                )
        }
    }

    fun render(
        profile: SolarSystemProfile,
        tick: Long,
        terrainSeed: Long,
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
    ) {
        update(terrainSeed)
        val frame = profile.lunarSurfaceFrame(tick)
        val r = frame.orientation
        val rotation =
            Matrix4f().set(
                floatArrayOf(
                    r.xAxis.x.toFloat(),
                    r.xAxis.y.toFloat(),
                    r.xAxis.z.toFloat(),
                    0f,
                    r.yAxis.x.toFloat(),
                    r.yAxis.y.toFloat(),
                    r.yAxis.z.toFloat(),
                    0f,
                    r.zAxis.x.toFloat(),
                    r.zAxis.y.toFloat(),
                    r.zAxis.z.toFloat(),
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                ),
            )
        val offset = frame.origin.position - observer
        val transform =
            Matrix4f(
                view,
            ).scale(scale.toFloat()).translate(offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat()).mul(rotation)
        for (gpu in meshes.values + listOfNotNull(body)) {
            gpu.bind()
            try {
                gpu.drawWithShader(transform, projection, checkNotNull(GameRenderer.getPositionColorShader()))
            } finally {
                VertexBuffer.unbind()
            }
        }
    }
}
