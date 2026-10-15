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

package org.cbi.orbitalis.minecraft.lab

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.ChartVelocity
import org.cbi.orbitalis.core.planet.CubeFace
import org.cbi.orbitalis.core.planet.CubeSphere
import org.cbi.orbitalis.core.planet.SurfacePoint
import org.cbi.orbitalis.core.planet.SurfaceState

/** Explicit laboratory chart layout, not a production chunk atlas or saved-data format. */
object PlanetLabCoordinates {
    val DIMENSION: ResourceKey<Level> =
        ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("orbitalis", "planet_lab"),
        )
    const val HALF_WIDTH = 16
    const val FLOOR_Y = 64
    private const val CHART_SPACING = 128
    val planet = CubeSphere(HALF_WIDTH.toDouble())
    val observationFloor = BlockPos(0, FLOOR_Y, 96)
    val orbitalOrigin = Vec3(0.0, 96.0, 128.0)

    fun chartCentre(face: CubeFace) = BlockPos(face.ordinal * CHART_SPACING, FLOOR_Y, 0)

    fun locate(
        position: Vec3,
        velocityPerTick: Vec3 = Vec3.ZERO,
    ): SurfaceState? {
        if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) return null
        if (!velocityPerTick.x.isFinite() || !velocityPerTick.y.isFinite() || !velocityPerTick.z.isFinite()) return null
        if (position.y <= FLOOR_Y + 1 - planet.radiusMetres) return null
        val face =
            CubeFace.entries.firstOrNull {
                val centre = chartCentre(it)
                kotlin.math.abs(position.x - centre.x - 0.5) <= HALF_WIDTH && kotlin.math.abs(position.z - centre.z - 0.5) <= HALF_WIDTH
            } ?: return null
        val centre = chartCentre(face)
        return SurfaceState(
            SurfacePoint(
                face,
                (position.x - centre.x - 0.5) / HALF_WIDTH,
                -(position.z - centre.z - 0.5) / HALF_WIDTH,
                position.y - FLOOR_Y - 1,
            ),
            ChartVelocity(velocityPerTick.x * 20 / HALF_WIDTH, -velocityPerTick.z * 20 / HALF_WIDTH, velocityPerTick.y * 20),
        )
    }
}
