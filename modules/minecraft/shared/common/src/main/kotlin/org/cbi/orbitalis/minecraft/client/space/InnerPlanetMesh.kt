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

import org.cbi.orbitalis.core.planet.InnerPlanetSurface
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import kotlin.math.ceil
import kotlin.math.floor

/** Immutable body-local sector geometry, sampled from the same seeded geology as native chunks. */
class InnerPlanetMesh(
    val kind: InnerPlanetSurface.Kind,
    val halfSize: Double,
) {
    init {
        require(halfSize.isFinite() && halfSize > 0.0)
    }

    val strata =
        if (kind == InnerPlanetSurface.Kind.MERCURY) {
            listOf(0x49484a, 0x626264, 0x777577, 0x858282)
        } else {
            listOf(0x4c4440, 0x6b5541, 0x946c36, 0xb58a46)
        }
    val baseColor = if (kind == InnerPlanetSurface.Kind.MERCURY) 0x8a8784 else 0xb58b46
    val sectors: List<SurfacePlanetMesh.Sector>
    val step: Double = LunarPlanetMesh.cellStep(halfSize)

    init {
        val extent = SurfacePlanetMesh.SECTOR_CHUNKS * SurfacePlanetMesh.STEP
        val low = floor(-halfSize / extent).toInt()
        val high = ceil(halfSize / extent).toInt() - 1
        sectors = (low..high).flatMap { x -> (low..high).map { z -> SurfacePlanetMesh.Sector(x, z) } }
    }

    fun coarse(sector: SurfacePlanetMesh.Sector): Sequence<SpaceScene.Face> =
        SurfacePlanetMesh.coarse(sector, halfSize, 65.0, baseColor, strata)

    fun underside(): Sequence<SpaceScene.Face> = PlanetRockShell.underside(halfSize, -64.0, strata.first()).asSequence()

    fun faces(
        terrainSeed: Long,
        sector: SurfacePlanetMesh.Sector,
    ): Sequence<SpaceScene.Face> {
        val terrain = InnerPlanetSurface(kind, terrainSeed, halfSize)
        val low = floor(-halfSize / step).toInt()
        val high = ceil(halfSize / step).toInt() - 1
        val count = (SurfacePlanetMesh.SECTOR_CHUNKS * SurfacePlanetMesh.STEP / step).toInt()
        val cells = linkedMapOf<Pair<Int, Int>, SurfaceAtlas.Cell>()
        // A local one-cell halo seals independent sector boundaries without reading another job.
        for (x in maxOf(low, sector.x * count - 1)..minOf(high, (sector.x + 1) * count)) {
            for (z in maxOf(low, sector.z * count - 1)..minOf(high, (sector.z + 1) * count)) {
                val px = floor(x * step + step / 2).toInt().coerceIn(ceil(-halfSize).toInt(), ceil(halfSize).toInt() - 1)
                val pz = floor(z * step + step / 2).toInt().coerceIn(ceil(-halfSize).toInt(), ceil(halfSize).toInt() - 1)
                val top = checkNotNull(terrain.height(px, pz))
                cells[x to z] = SurfaceAtlas.Cell(x, z, top + 1.0, color(terrain.darkRock(px, pz), top), 1)
            }
        }
        return SurfacePlanetMesh.facesFromCells(cells, sector, halfSize, -64.0, strata, step)
    }

    private fun color(
        dark: Boolean,
        height: Int,
    ): Int {
        if (dark) return 0x56534f
        val base = baseColor
        val variation = (height - 64) * 0.002

        fun channel(shift: Int) = (((base shr shift and 255) * (1.0 + variation)).toInt().coerceIn(0, 255) shl shift)
        return channel(16) or channel(8) or channel(0)
    }
}
