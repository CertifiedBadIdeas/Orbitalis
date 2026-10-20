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
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.InnerPlanetSurface
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.client.space.PlanetMeshBuffers.PreparedMesh
import org.cbi.orbitalis.minecraft.client.space.PlanetMeshBuffers.prepare
import org.cbi.orbitalis.minecraft.client.space.PlanetMeshBuffers.upload
import org.cbi.orbitalis.minecraft.lighting.SkyLighting
import org.joml.Matrix4f
import java.util.concurrent.CompletableFuture

/** Two derived finite planets; four background jobs and two GPU uploads total per render update. */
object InnerPlanetRenderer {
    private class Entry(
        val halfSize: Double,
        val seed: Long?,
        val mesh: InnerPlanetMesh,
    ) {
        var underside: VertexBuffer? = null
        var fallback: VertexBuffer? = null
        val refined = linkedMapOf<SurfacePlanetMesh.Sector, VertexBuffer>()
        val pending = linkedMapOf<SurfacePlanetMesh.Sector, CompletableFuture<PreparedMesh>>()
        var nextSector = 0
        var retryAfter = 0L

        fun close() {
            pending.values.forEach { it.thenAccept(PreparedMesh::close) }
            pending.clear()
            refined.values.forEach(VertexBuffer::close)
            refined.clear()
            underside?.close()
            fallback?.close()
        }
    }

    private val entries = linkedMapOf<String, Entry>()
    private var nextBody = 0

    fun clear() {
        entries.values.forEach(Entry::close)
        entries.clear()
        nextBody = 0
    }

    private fun fallback(entry: Entry): Sequence<SpaceScene.Face> =
        sequence {
            for (sector in entry.mesh.sectors) if (sector !in entry.refined) yieldAll(entry.mesh.coarse(sector))
        }

    private fun update(
        profile: SolarSystemProfile,
        seeds: Map<String, Long>,
    ) {
        for (definition in profile.innerPlanets) {
            val current = entries[definition.id]
            val seed = seeds[definition.id]
            if (current == null || current.halfSize != definition.halfSize || current.seed != seed) {
                current?.close()
                val kind =
                    if (definition.id ==
                        SolarSystemProfile.MERCURY
                    ) {
                        InnerPlanetSurface.Kind.MERCURY
                    } else {
                        InnerPlanetSurface.Kind.VENUS
                    }
                entries[definition.id] = Entry(definition.halfSize, seed, InnerPlanetMesh(kind, definition.halfSize))
            }
        }
        // Initialization has the same upload cap as refinement; no hundreds-of-buffers burst.
        var uploads = 0
        for (entry in entries.values) {
            if (entry.underside == null && uploads < 2) {
                entry.underside = upload(prepare(entry.mesh.underside(), 0, 0))
                uploads++
            }
            if (entry.fallback == null && entry.refined.size != entry.mesh.sectors.size && uploads < 2) {
                entry.fallback = upload(prepare(fallback(entry), 0, 0))
                uploads++
            }
        }
        if (uploads == 0) {
            val list = entries.values.toList()
            for (i in list.indices) {
                val entry = list[(nextBody + i) % list.size]
                val ready = entry.pending.entries.firstOrNull { it.value.isDone } ?: continue
                entry.pending.remove(ready.key)
                try {
                    entry.refined[ready.key] = upload(ready.value.join())
                    if (entry.refined.size == entry.mesh.sectors.size) {
                        entry.fallback?.close()
                        entry.fallback = null
                    } else {
                        entry.fallback = upload(prepare(fallback(entry), 0, 0), entry.fallback)
                    }
                } catch (error: Exception) {
                    LogUtils.getLogger().warn("Could not prepare inner planet LOD sector {}", ready.key, error)
                    entry.retryAfter = System.nanoTime() + 1_000_000_000L
                }
                nextBody = (nextBody + i + 1) % list.size
                break
            }
        }
        // Shared cap across both planets, including old jobs still waiting for upload.
        var pending = entries.values.sumOf { it.pending.size }
        for (entry in entries.values) {
            if (entry.seed == null || System.nanoTime() < entry.retryAfter) continue
            repeat(entry.mesh.sectors.size) {
                if (pending >= 4) return
                val sector = entry.mesh.sectors[entry.nextSector]
                entry.nextSector = (entry.nextSector + 1) % entry.mesh.sectors.size
                if (sector in entry.refined || sector in entry.pending) return@repeat
                entry.pending[sector] =
                    CompletableFuture.supplyAsync({ prepare(entry.mesh.faces(entry.seed, sector), 0, 0) }, Util.backgroundExecutor())
                pending++
            }
        }
    }

    fun render(
        profile: SolarSystemProfile,
        tick: Long,
        seeds: Map<String, Long> = emptyMap(),
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        seconds: Double = 0.0,
        excludeBody: String? = null,
    ) {
        update(profile, seeds)
        val sun = SkyLighting.sunPosition(profile, tick, seconds)
        for (definition in profile.innerPlanets) {
            if (definition.id == excludeBody) continue
            val entry = entries.getValue(definition.id)
            val frame = profile.innerPlanetSurfaceFrame(definition.id, tick, seconds)
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
            // Subtract astronomical root coordinates as doubles before conversion to camera space.
            val offset = (frame.origin.position - observer) * scale
            val transform =
                Matrix4f(
                    view,
                ).translate(offset.x.toFloat(), offset.y.toFloat(), offset.z.toFloat()).scale(scale.toFloat()).mul(rotation)
            val shader = PlanetLighting.prepare(r.inverse((sun - frame.origin.position).normalized()))
            for (gpu in entry.refined.values + listOfNotNull(entry.underside, entry.fallback)) {
                gpu.bind()
                try {
                    gpu.drawWithShader(transform, projection, shader)
                } finally {
                    VertexBuffer.unbind()
                }
            }
        }
    }
}
