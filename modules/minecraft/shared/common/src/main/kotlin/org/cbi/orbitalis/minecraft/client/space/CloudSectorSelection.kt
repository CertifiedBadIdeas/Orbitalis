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

import org.cbi.orbitalis.core.cloud.ProceduralCloudField
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One detail level per view keeps independently cached sector boundaries identical. */
internal object CloudSectorSelection {
    const val FADE_DISTANCE = 6000.0

    data class Sector(
        val x: Int,
        val z: Int,
    )

    fun step(
        distance: Double,
        pixelsPerMetre: Double?,
        fast: Boolean,
    ): Int {
        val normal =
            if (pixelsPerMetre != null) {
                when {
                    pixelsPerMetre >= 0.5 -> 1
                    pixelsPerMetre >= 0.15 -> 2
                    pixelsPerMetre >= 0.04 -> 4
                    else -> 8
                }
            } else {
                when {
                    distance < 64.0 -> 1
                    distance < 800.0 -> 2
                    distance < 3000.0 -> 4
                    else -> 8
                }
            }
        return if (fast) min(8, normal * 2) else normal
    }

    fun sectors(
        halfSize: Double,
        eye: Vector3,
        windX: Double,
        windZ: Double,
        local: Boolean,
    ): List<Sector> {
        require(halfSize.isFinite() && halfSize > 0.0)
        require(eye.x.isFinite() && eye.z.isFinite() && windX.isFinite() && windZ.isFinite())
        val radius = if (local) FADE_DISTANCE else Double.POSITIVE_INFINITY
        val loX = max(-halfSize, eye.x - radius)
        val hiX = min(halfSize, eye.x + radius)
        val loZ = max(-halfSize, eye.z - radius)
        val hiZ = min(halfSize, eye.z + radius)
        if (loX >= hiX || loZ >= hiZ) return emptyList()
        val size = ProceduralCloudField.SECTOR_SIZE

        fun sector(value: Double) = floor(value / size).toInt()
        val result = mutableListOf<Sector>()
        for (x in sector(loX - windX)..sector(hiX - windX)) {
            for (z in sector(loZ - windZ)..sector(hiZ - windZ)) {
                val cx = (x + 0.5) * size + windX
                val cz = (z + 0.5) * size + windZ
                val dx = max(0.0, abs(cx - eye.x) - size / 2)
                val dz = max(0.0, abs(cz - eye.z) - size / 2)
                if (!local || dx * dx + dz * dz <= radius * radius) result += Sector(x, z)
            }
        }
        return result.sortedBy {
            val dx = (it.x + 0.5) * size + windX - eye.x
            val dz = (it.z + 0.5) * size + windZ - eye.z
            dx * dx + dz * dz
        }
    }
}
