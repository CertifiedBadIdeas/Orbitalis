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

package org.cbi.orbitalis.minecraft.compat.voxy

import net.minecraft.server.level.ServerLevel
import org.cbi.orbitalis.minecraft.innerplanets.InnerPlanetChunkGenerator
import org.cbi.orbitalis.minecraft.moon.LunarChunkGenerator
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain
import kotlin.math.ceil

/** Read the configured native generator, never the movement border or a second planet-size model. */
object WorldgenSurfaceBounds {
    fun get(level: ServerLevel): FiniteSurfaceTerrain.Bounds? =
        when (val generator = level.chunkSource.generator) {
            is InnerPlanetChunkGenerator -> square(generator.surfaceHalfSize)
            is LunarChunkGenerator -> square(generator.surfaceHalfSize)
            else -> FiniteSurfaceTerrain.bounds(level)
        }

    fun square(halfSize: Double): FiniteSurfaceTerrain.Bounds {
        require(halfSize.isFinite() && halfSize > 0.0 && halfSize <= 10000.0)
        // Native terrain admits integral columns x >= -halfSize and x < halfSize.
        val min = ceil(-halfSize).toInt()
        val max = ceil(halfSize).toInt()
        return FiniteSurfaceTerrain.Bounds(min, min, max, max)
    }
}
