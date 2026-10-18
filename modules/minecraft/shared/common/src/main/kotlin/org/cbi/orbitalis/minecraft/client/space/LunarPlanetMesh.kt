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

import org.cbi.orbitalis.core.planet.LunarSurface
import org.cbi.orbitalis.core.planet.SurfaceAtlas

/** Deterministic 16m sectors use the shared slab mesh with lunar geology and no atmosphere. */
object LunarPlanetMesh {
    private val half = LunarSurface.HALF_SIZE.toDouble()
    private val low = Math.floorDiv(-LunarSurface.HALF_SIZE, 16)
    private val high = Math.floorDiv(LunarSurface.HALF_SIZE - 1, 16)
    private val strata = listOf(0x55565a, 0x747579, 0x85868a, 0x96979b)
    val sectors =
        (Math.floorDiv(low, 32)..Math.floorDiv(high, 32)).flatMap { x ->
            (Math.floorDiv(low, 32)..Math.floorDiv(high, 32)).map { z -> SurfacePlanetMesh.Sector(x, z) }
        }

    /** Keep the coarse slab out of ground-level sky while retaining it above, beside and below native chunks. */
    fun visibleFromSurface(position: org.cbi.orbitalis.core.planet.Vector3): Boolean =
        org.cbi.orbitalis.core.planet.PlanetFlightEnvelope.moon
            .distance(position) >= 32.0

    fun coarse(sector: SurfacePlanetMesh.Sector): Sequence<SpaceScene.Face> =
        SurfacePlanetMesh.coarse(sector, half, LunarSurface.SEA_LEVEL.toDouble(), 0x969698, strata)

    fun faces(
        seed: Long,
        sector: SurfacePlanetMesh.Sector,
    ): Sequence<SpaceScene.Face> {
        val terrain = LunarSurface(seed)
        val atlas = SurfaceAtlas()
        // One-cell halo seals height discontinuities across separately prepared sectors.
        for (x in maxOf(low, sector.x * 32 - 1)..minOf(high, (sector.x + 1) * 32)) {
            for (z in maxOf(low, sector.z * 32 - 1)..minOf(high, (sector.z + 1) * 32)) {
                val px = (x * 16 + 8).coerceIn(-LunarSurface.HALF_SIZE, LunarSurface.HALF_SIZE - 1)
                val pz = (z * 16 + 8).coerceIn(-LunarSurface.HALF_SIZE, LunarSurface.HALF_SIZE - 1)
                val height = checkNotNull(terrain.height(px, pz))
                val gray = (150 + (height - 64) / 4).coerceIn(125, 165)
                atlas.put(SurfaceAtlas.Sample(x, z, height + 1, (gray shl 16) or (gray shl 8) or (gray + 2)))
            }
        }
        return SurfacePlanetMesh.faces(atlas, sector, half, -64.0, strata)
    }

    fun underside(): Sequence<SpaceScene.Face> = PlanetRockShell.underside(half, -64.0, 0x66676b).asSequence()
}
