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

/** Seeded finite terrain sampled without chunk/world access; no exterior floor or bedrock. */
class InnerPlanetSurface(
    val kind: Kind,
    val seed: Long,
    val halfSize: Double,
) {
    enum class Kind(
        val id: String,
    ) {
        MERCURY("mercury"),
        VENUS("venus"),
    }

    init {
        require(halfSize.isFinite() && halfSize > 0.0)
    }

    private val craters = LunarSurface(seed, halfSize)

    fun height(
        x: Int,
        z: Int,
    ): Int? {
        if (!craters.contains(x, z)) return null
        if (kind == Kind.MERCURY) return craters.height(x, z)
        var h = 64.0 + noise(x / 360.0, z / 360.0) * 15.0 + noise(x / 70.0, z / 70.0) * 4.0
        val cx = Math.floorDiv(x, 384)
        val cz = Math.floorDiv(z, 384)
        for (gx in cx - 1..cx + 1) {
            for (gz in cz - 1..cz + 1) {
                val px = (gx + unit(gx, gz, 1)) * 384.0
                val pz = (gz + unit(gx, gz, 2)) * 384.0
                val radius = 75.0 + unit(gx, gz, 3) * 80.0
                val d = hypot(x - px, z - pz) / radius
                if (d < 1.0) {
                    val cone = 1.0 - d * d
                    h += 40.0 * cone * cone
                    if (d < 0.2) {
                        val bowl = 1.0 - d * d / 0.04
                        h -= 18.0 * bowl * bowl
                    }
                }
            }
        }
        return h.roundToInt().coerceIn(32, 124)
    }

    /** Stable surface variation, also used by the orbital representation's native palette. */
    fun darkRock(
        x: Int,
        z: Int,
    ): Boolean = noise(x / 32.0, z / 32.0) > if (kind == Kind.VENUS) 0.25 else 0.55

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
        const val BOTTOM = -64

        fun terrainSeed(
            worldSeed: Long,
            kind: Kind,
        ): Long {
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            hash.update("orbitalis:${kind.id}:terrain:v1".toByteArray(Charsets.UTF_8))
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
    }
}
