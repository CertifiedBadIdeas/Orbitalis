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

import org.cbi.orbitalis.core.planet.SurfaceAtlas

/** Render-thread index. Jobs receive immutable local samples including the positive-edge halo. */
class SurfaceMeshSectors {
    private val valid = SurfacePlanetMesh.sectors.toSet()
    private val samples = mutableMapOf<SurfacePlanetMesh.Sector, MutableMap<Int, SurfaceAtlas.Sample>>()
    private val revisions = mutableMapOf<SurfacePlanetMesh.Sector, Long>()

    fun revision(sector: SurfacePlanetMesh.Sector) = revisions[sector] ?: 0L

    fun put(sample: SurfaceAtlas.Sample) {
        val owner = SurfacePlanetMesh.sector(sample.x, sample.z)
        if (samples.getOrPut(owner) { hashMapOf() }.put(sample.key, sample) == sample) return
        val affected = setOf(owner, SurfacePlanetMesh.sector(sample.x - 1, sample.z), SurfacePlanetMesh.sector(sample.x, sample.z - 1))
        for (sector in affected) if (sector in valid) revisions[sector] = revision(sector) + 1L
    }

    fun snapshot(sector: SurfacePlanetMesh.Sector): List<SurfaceAtlas.Sample> {
        val x0 = sector.x * SurfacePlanetMesh.SECTOR_CHUNKS
        val z0 = sector.z * SurfacePlanetMesh.SECTOR_CHUNKS
        val result = ArrayList<SurfaceAtlas.Sample>()
        for (dx in 0..1) {
            for (dz in 0..1) {
                samples[SurfacePlanetMesh.Sector(sector.x + dx, sector.z + dz)]?.values?.filterTo(result) {
                    it.x in x0..x0 + SurfacePlanetMesh.SECTOR_CHUNKS && it.z in z0..z0 + SurfacePlanetMesh.SECTOR_CHUNKS
                }
            }
        }
        return result
    }
}
