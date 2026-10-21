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

package org.cbi.orbitalis.core.cloud

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Immutable, periodic cloud columns. Coordinates and heights are metres, independent of time. */
class ProceduralCloudField(
    val seed: Long,
) {
    /** Offsets above the renderer's cloud base, quantized to eight metres. */
    data class Column(
        val bottom: Double,
        val top: Double,
    )

    /**
     * Outward counterclockwise quad; x/z are Earth-local, y is relative to the cloud base.
     * [thickness] is the complete source column thickness in metres, even on partial side bands.
     */
    data class Face(
        val vertices: List<Vector3>,
        val normal: Vector3,
        val thickness: Double,
    )

    fun column(
        cellX: Int,
        cellZ: Int,
    ): Column? {
        val x = Math.floorMod(cellX, PERIOD_CELLS).toDouble() + 0.5
        val z = Math.floorMod(cellZ, PERIOD_CELLS).toDouble() + 0.5
        val warpedX = x + (noise(x, z, 64, 1) - 0.5) * 16.0
        val warpedZ = z + (noise(x, z, 64, 2) - 0.5) * 16.0
        val density =
            noise(warpedX, warpedZ, 32, 3) * 0.55 +
                noise(warpedX, warpedZ, 16, 4) * 0.30 + noise(warpedX, warpedZ, 4, 5) * 0.15
        if (density <= 0.54) return null
        val mass = ((density - 0.54) / 0.25).coerceIn(0.0, 1.0)
        val bottom = floor(noise(warpedX, warpedZ, 16, 6) * 3.0).toInt()
        val top = min(10, bottom + 2 + floor(mass * 7.0).toInt())
        return Column(bottom * 8.0, top * 8.0)
    }

    /**
     * Exterior quads for a 512m tile, including a sampled halo but no halo geometry.
     * Coarse representatives are globally aligned; neighboring tiles must use the same step.
     */
    fun sector(
        sectorX: Int,
        sectorZ: Int,
        step: Int = 1,
    ): List<Face> {
        require(step == 1 || step == 2 || step == 4 || step == 8) { "Cloud step must be 1, 2, 4 or 8" }
        val originX = sectorX.toLong() * SECTOR_CELLS
        val originZ = sectorZ.toLong() * SECTOR_CELLS
        require(originX >= Int.MIN_VALUE.toLong() + 8 && originX <= Int.MAX_VALUE.toLong() - 40)
        require(originZ >= Int.MIN_VALUE.toLong() + 8 && originZ <= Int.MAX_VALUE.toLong() - 40)
        val size = SECTOR_CELLS / step
        val halo =
            Array(size + 2) { ix ->
                Array(size + 2) { iz ->
                    column((originX + (ix - 1) * step + step / 2).toInt(), (originZ + (iz - 1) * step + step / 2).toInt())
                }
            }
        val faces = ArrayList<Face>()
        val width = CELL * step
        for (ix in 0 until size) {
            for (iz in 0 until size) {
                val c = halo[ix + 1][iz + 1] ?: continue
                val x = (originX + ix * step) * CELL
                val z = (originZ + iz * step) * CELL

                fun quad(
                    normal: Vector3,
                    vararg points: Vector3,
                ) {
                    faces.add(Face(points.toList(), normal, c.top - c.bottom))
                }
                quad(
                    Vector3(0.0, 1.0, 0.0),
                    Vector3(x, c.top, z),
                    Vector3(x, c.top, z + width),
                    Vector3(x + width, c.top, z + width),
                    Vector3(x + width, c.top, z),
                )
                quad(
                    Vector3(0.0, -1.0, 0.0),
                    Vector3(x, c.bottom, z),
                    Vector3(x + width, c.bottom, z),
                    Vector3(
                        x + width,
                        c.bottom,
                        z + width,
                    ),
                    Vector3(x, c.bottom, z + width),
                )

                fun side(
                    neighbor: Column?,
                    dx: Int,
                    dz: Int,
                ) {
                    fun band(
                        low: Double,
                        high: Double,
                    ) {
                        if (high <= low) return
                        when {
                            dx > 0 -> {
                                quad(
                                    Vector3(1.0, 0.0, 0.0),
                                    Vector3(x + width, low, z),
                                    Vector3(x + width, high, z),
                                    Vector3(x + width, high, z + width),
                                    Vector3(x + width, low, z + width),
                                )
                            }

                            dx < 0 -> {
                                quad(
                                    Vector3(-1.0, 0.0, 0.0),
                                    Vector3(x, low, z),
                                    Vector3(x, low, z + width),
                                    Vector3(
                                        x,
                                        high,
                                        z + width,
                                    ),
                                    Vector3(x, high, z),
                                )
                            }

                            dz > 0 -> {
                                quad(
                                    Vector3(0.0, 0.0, 1.0),
                                    Vector3(x, low, z + width),
                                    Vector3(x + width, low, z + width),
                                    Vector3(x + width, high, z + width),
                                    Vector3(x, high, z + width),
                                )
                            }

                            else -> {
                                quad(
                                    Vector3(0.0, 0.0, -1.0),
                                    Vector3(x, low, z),
                                    Vector3(x, high, z),
                                    Vector3(x + width, high, z),
                                    Vector3(x + width, low, z),
                                )
                            }
                        }
                    }
                    if (neighbor == null) {
                        band(c.bottom, c.top)
                    } else {
                        band(c.bottom, min(c.top, neighbor.bottom))
                        band(max(c.bottom, neighbor.top), c.top)
                    }
                }
                side(halo[ix][iz + 1], -1, 0)
                side(halo[ix + 2][iz + 1], 1, 0)
                side(halo[ix + 1][iz], 0, -1)
                side(halo[ix + 1][iz + 2], 0, 1)
            }
        }
        return faces
    }

    private fun noise(
        x: Double,
        z: Double,
        scale: Int,
        salt: Long,
    ): Double {
        val gx = x / scale
        val gz = z / scale
        val ix = floor(gx).toInt()
        val iz = floor(gz).toInt()

        fun smooth(t: Double) = t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
        val tx = smooth(gx - floor(gx))
        val tz = smooth(gz - floor(gz))

        fun sample(
            dx: Int,
            dz: Int,
        ): Double {
            val period = PERIOD_CELLS / scale
            var h =
                seed xor (Math.floorMod(ix + dx, period).toLong() * -7046029254386353131L) xor
                    (Math.floorMod(iz + dz, period).toLong() * -4658895280553007687L) xor (salt * -7723592293110705685L)
            h = (h xor (h ushr 30)) * -4658895280553007687L
            h = (h xor (h ushr 27)) * -7723592293110705685L
            h = h xor (h ushr 31)
            return (h ushr 11).toDouble() / 9007199254740992.0
        }
        val a = sample(0, 0) * (1.0 - tx) + sample(1, 0) * tx
        val b = sample(0, 1) * (1.0 - tx) + sample(1, 1) * tx
        return a * (1.0 - tz) + b * tz
    }

    companion object {
        const val CELL = 16.0
        const val SECTOR_SIZE = 512.0
        const val MAX_HEIGHT = 80.0
        const val PERIOD = 65536.0

        // Two caps and up to two exposed bands on each side, per occupied base cell.
        const val MAX_FACES_PER_SECTOR = 10240
        private const val PERIOD_CELLS = 4096
        private const val SECTOR_CELLS = 32
    }
}
