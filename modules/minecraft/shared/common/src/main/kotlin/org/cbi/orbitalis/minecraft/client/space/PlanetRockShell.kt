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

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Decorative closed-bottom rock shell; the square top boundary stays anchored to surface coordinates. */
object PlanetRockShell {
    private const val CELLS = 64

    private fun noise(
        x: Double,
        z: Double,
    ): Double =
        sin(x * 0.0017 + z * 0.0009) * 0.55 +
            cos(z * 0.0031 - x * 0.0011) * 0.3 + sin(x * 0.0093 + z * 0.0077) * 0.15

    private fun vertex(
        i: Int,
        j: Int,
    ): Vector3 {
        val step = SpaceScene.HALF_SIZE * 2.0 / CELLS
        val x = -SpaceScene.HALF_SIZE + i * step
        val z = -SpaceScene.HALF_SIZE + j * step
        // Boundary vertices remain exact; jitter only breaks up the interior facet grid.
        val interior = i in 1 until CELLS && j in 1 until CELLS
        val px = x + if (interior) sin(i * 19.31 + j * 7.13) * step * 0.28 else 0.0
        val pz = z + if (interior) cos(i * 3.17 - j * 17.91) * step * 0.28 else 0.0
        val centre = (1.0 - max(abs(px), abs(pz)) / SpaceScene.HALF_SIZE).coerceIn(0.0, 1.0)
        val depth = 460.0 + 1730.0 * centre.pow(0.7) + noise(px, pz) * 250.0
        return Vector3(px, -depth, pz)
    }

    private fun color(
        base: Int,
        variation: Double,
    ): Int {
        fun channel(shift: Int) = (((base shr shift and 255) * variation).toInt().coerceIn(0, 255) shl shift)
        return channel(16) or channel(8) or channel(0)
    }

    private fun triangle(
        a: Vector3,
        b: Vector3,
        c: Vector3,
        rgb: Int,
    ): SpaceScene.Face = SpaceScene.Face(listOf(a, b, c, c), rgb, (b - a).cross(c - a).normalized(), 0.42)

    val faces: List<SpaceScene.Face> =
        buildList {
            val grid = Array(CELLS + 1) { i -> Array(CELLS + 1) { j -> vertex(i, j) } }
            for (i in 0 until CELLS) {
                for (j in 0 until CELLS) {
                    val a = grid[i][j]
                    val b = grid[i + 1][j]
                    val c = grid[i + 1][j + 1]
                    val d = grid[i][j + 1]
                    val tone = 1.0 + noise(a.x * 2.7, a.z * 2.7) * 0.16
                    val rgb = color(0x8f887c, tone)
                    // Alternating diagonals avoid a directional pattern across the underside.
                    if ((i + j) % 2 == 0) {
                        add(triangle(a, b, c, rgb))
                        add(triangle(a, c, d, color(rgb, 0.97)))
                    } else {
                        add(triangle(a, b, d, rgb))
                        add(triangle(b, c, d, color(rgb, 1.03)))
                    }
                }
            }
            val boundary =
                buildList {
                    for (i in 0 until CELLS) add(grid[i][0])
                    for (j in 0 until CELLS) add(grid[CELLS][j])
                    for (i in CELLS downTo 1) add(grid[i][CELLS])
                    for (j in CELLS downTo 1) add(grid[0][j])
                }
            val strata = intArrayOf(0x91775a, 0xa2937b, 0x7c807b, 0x9b9281, 0x757570, 0x8d8377, 0x797c78, 0x928a7e)

            fun band(
                v: Vector3,
                layer: Int,
            ): Vector3 {
                if (layer == 0) return Vector3(v.x, SpaceScene.TOP, v.z)
                if (layer == strata.size) return v
                val fraction = layer.toDouble() / strata.size
                val ripple = sin(fraction * Math.PI) * noise(v.x * 1.3 + layer * 41, v.z * 1.3) * 0.025
                return Vector3(v.x, SpaceScene.TOP + (v.y - SpaceScene.TOP) * (fraction + ripple), v.z)
            }
            for (i in boundary.indices) {
                val a = boundary[i]
                val b = boundary[(i + 1) % boundary.size]
                for (layer in strata.indices) {
                    val upperA = band(a, layer)
                    val upperB = band(b, layer)
                    val lowerA = band(a, layer + 1)
                    val lowerB = band(b, layer + 1)
                    val rgb = color(strata[layer], 1.0 + noise(a.x * 4, a.z * 4) * 0.13)
                    add(triangle(upperA, upperB, lowerB, rgb))
                    add(triangle(upperA, lowerB, lowerA, rgb))
                }
            }
        }
}
