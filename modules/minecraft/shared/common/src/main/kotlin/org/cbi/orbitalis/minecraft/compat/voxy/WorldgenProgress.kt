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

import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain

/** Exact boundary correction for WorldGenV2's native 32-by-32 chunk counting nodes. */
class WorldgenProgress(
    private val bounds: FiniteSurfaceTerrain.Bounds,
    private val position: ChunkPos,
    private val radius: Int,
    private val completed: (ChunkPos) -> Boolean,
) {
    init {
        require(radius >= 0)
    }

    /** null preserves the native fast path, including its completion bitmasks for interior nodes. */
    fun partialNode(
        x: Int,
        z: Int,
    ): Int? {
        val minX = x.toLong() * 32
        val minZ = z.toLong() * 32
        val maxX = minX + 31
        val maxZ = minZ + 31
        val lowX = maxOf(minX, Math.floorDiv(bounds.minX, 16).toLong())
        val lowZ = maxOf(minZ, Math.floorDiv(bounds.minZ, 16).toLong())
        val highX = minOf(maxX, Math.floorDiv(bounds.maxX - 1, 16).toLong())
        val highZ = minOf(maxZ, Math.floorDiv(bounds.maxZ - 1, 16).toLong())
        if (bounds.minX >= bounds.maxX || bounds.minZ >= bounds.maxZ || lowX > highX || lowZ > highZ) return 0
        val radiusSquared = radius.toLong() * radius

        fun inside(
            cx: Long,
            cz: Long,
        ): Boolean {
            val dx = cx - position.x
            val dz = cz - position.z
            // Restrict differences before squaring so extreme native coordinates cannot overflow.
            return dx in -radius.toLong()..radius.toLong() && dz in -radius.toLong()..radius.toLong() &&
                dx * dx + dz * dz <= radiusSquared
        }
        if (lowX == minX && lowZ == minZ && highX == maxX && highZ == maxZ &&
            inside(minX, minZ) && inside(maxX, minZ) && inside(minX, maxZ) && inside(maxX, maxZ)
        ) {
            return null
        }
        var count = 0
        for (cx in lowX..highX) {
            for (cz in lowZ..highZ) {
                if (inside(cx, cz) && !completed(ChunkPos(cx.toInt(), cz.toInt()))) count++
            }
        }
        return count
    }
}
