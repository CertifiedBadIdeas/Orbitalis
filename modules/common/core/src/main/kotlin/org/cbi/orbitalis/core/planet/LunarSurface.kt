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

package org.cbi.orbitalis.core.planet

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Version-one finite lunar geology. Integer world columns are sampled independently of chunk order. */
class LunarSurface(
    val seed: Long,
) {
    fun contains(
        x: Int,
        z: Int,
    ): Boolean = x >= -HALF_SIZE && x < HALF_SIZE && z >= -HALF_SIZE && z < HALF_SIZE

    /** Top solid block; null denotes genuine exterior vacuum rather than an invisible floor. */
    fun height(
        x: Int,
        z: Int,
    ): Int? {
        if (!contains(x, z)) return null
        var height = SEA_LEVEL + noise(x / 320.0, z / 320.0) * 9.0 + noise(x / 48.0, z / 48.0) * 2.0
        val cx = Math.floorDiv(x, CELL)
        val cz = Math.floorDiv(z, CELL)
        for (gx in cx - 1..cx + 1) {
            for (gz in cz - 1..cz + 1) {
                val px = (gx + unit(gx, gz, 1)) * CELL
                val pz = (gz + unit(gx, gz, 2)) * CELL
                val radius = 18.0 + unit(gx, gz, 3) * 75.0
                val r = hypot(x - px, z - pz) / radius
                if (r < 1.0) {
                    val bowl = 1.0 - r * r
                    height -= radius * 0.23 * bowl * bowl
                } else if (r < 1.3) {
                    val t = (r - 1.0) / 0.3
                    height += radius * 0.07 * 16.0 * t * t * (1.0 - t) * (1.0 - t)
                }
            }
        }
        return height.roundToInt().coerceIn(24, 88)
    }

    private fun noise(
        x: Double,
        z: Double,
    ): Double {
        val gx = floor(x).toInt()
        val gz = floor(z).toInt()

        fun smooth(t: Double) = t * t * (3.0 - 2.0 * t)
        val tx = smooth(x - gx)
        val tz = smooth(z - gz)
        val a = unit(gx, gz, 0) * (1.0 - tx) + unit(gx + 1, gz, 0) * tx
        val b = unit(gx, gz + 1, 0) * (1.0 - tx) + unit(gx + 1, gz + 1, 0) * tx
        return (a * (1.0 - tz) + b * tz) * 2.0 - 1.0
    }

    private fun unit(
        x: Int,
        z: Int,
        salt: Int,
    ): Double {
        var n = seed xor (x.toLong() * 341873128712L) xor (z.toLong() * 132897987541L) xor (salt.toLong() * 42317861L)
        n = (n xor (n ushr 30)) * -4658895280553007687L
        n = (n xor (n ushr 27)) * -7723592293110705685L
        n = n xor (n ushr 31)
        return (n ushr 11).toDouble() / 9007199254740992.0
    }

    companion object {
        /** A client may know lunar geology without receiving the Overworld generation seed. */
        fun terrainSeed(worldSeed: Long): Long {
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            hash.update("orbitalis:moon:terrain:v1".toByteArray(Charsets.UTF_8))
            return java.nio.ByteBuffer
                .wrap(
                    hash.digest(
                        java.nio.ByteBuffer
                            .allocate(8)
                            .putLong(worldSeed)
                            .array(),
                    ),
                ).long
        }

        const val VERSION = 1
        const val HALF_SIZE = 1400
        const val SEA_LEVEL = 64
        const val BOTTOM = -64
        const val ASCENT_HEIGHT = 600.0
        const val DESCENT_HEIGHT = 400.0
        private const val CELL = 192
    }
}
