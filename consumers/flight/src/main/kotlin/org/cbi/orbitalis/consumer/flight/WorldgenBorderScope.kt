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

package org.cbi.orbitalis.consumer.flight

import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain

internal const val WORLDGEN_FIND_WORK =
    "Lcom/ethan/voxyworldgenv2/core/DistanceGraph;" +
        "findWork(Lnet/minecraft/world/level/ChunkPos;ILjava/util/Set;)Ljava/util/List;"

/** Scoped to one manager's Overworld search; other dimensions and graph callers stay native. */
internal object WorldgenBorderScope {
    val current = ThreadLocal<FiniteSurfaceTerrain.Bounds?>()

    fun outside(
        x: Int,
        z: Int,
        size: Int,
    ): Boolean {
        val bounds = current.get() ?: return false
        // WorldGenV2 graph cells are four-chunk batches at the leaf level.
        val width = size.toLong() * 64
        val minX = x.toLong() * width
        val minZ = z.toLong() * width
        return bounds.minX >= bounds.maxX || bounds.minZ >= bounds.maxZ ||
            minX >= bounds.maxX || minX + width <= bounds.minX ||
            minZ >= bounds.maxZ || minZ + width <= bounds.minZ
    }
}
