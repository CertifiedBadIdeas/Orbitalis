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
import kotlin.math.cos
import kotlin.math.sin

/** Presentation of the real finite slab: flat bedrock boundary and sector-owned perimeter strata. */
object PlanetRockShell {
    private const val CELLS = 64

    private fun noise(
        x: Double,
        z: Double,
    ): Double =
        sin(x * 0.0017 + z * 0.0009) * 0.55 + cos(z * 0.0031 - x * 0.0011) * 0.3 +
            sin(x * 0.0093 + z * 0.0077) * 0.15

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
    ) = SpaceScene.Face(listOf(a, b, c, c), rgb, (b - a).cross(c - a).normalized(), 0.42)

    /** Only this immutable underside belongs to the static body buffer; walls belong to LOD sectors. */
    val faces: List<SpaceScene.Face> =
        buildList {
            fun vertex(
                i: Int,
                j: Int,
            ): Vector3 {
                val step = SpaceScene.HALF_SIZE * 2.0 / CELLS
                val interior = i in 1 until CELLS && j in 1 until CELLS
                val x = -SpaceScene.HALF_SIZE + i * step + if (interior) sin(i * 19.31 + j * 7.13) * step * 0.28 else 0.0
                val z = -SpaceScene.HALF_SIZE + j * step + if (interior) cos(i * 3.17 - j * 17.91) * step * 0.28 else 0.0
                return Vector3(x, SpaceScene.BOTTOM, z)
            }
            val grid = Array(CELLS + 1) { i -> Array(CELLS + 1) { j -> vertex(i, j) } }
            for (i in 0 until CELLS) {
                for (j in 0 until CELLS) {
                    val a = grid[i][j]
                    val b = grid[i + 1][j]
                    val c = grid[i + 1][j + 1]
                    val d = grid[i][j + 1]
                    val rgb = color(0x7f807c, 1.0 + noise(a.x * 2.7, a.z * 2.7) * 0.13)
                    if ((i + j) % 2 == 0) {
                        add(triangle(a, b, c, rgb))
                        add(triangle(a, c, d, color(rgb, 0.98)))
                    } else {
                        add(triangle(a, b, d, rgb))
                        add(triangle(b, c, d, color(rgb, 1.02)))
                    }
                }
            }
        }

    /** Emit outer walls only, from the local terrain top down to the unchanged native bottom. */
    fun walls(
        x0: Double,
        x1: Double,
        z0: Double,
        z1: Double,
        height: Double,
        surfaceColor: Int,
    ): Sequence<SpaceScene.Face> =
        sequence {
            if (height <= SpaceScene.BOTTOM) return@sequence
            val h = SpaceScene.HALF_SIZE
            if (x0 != -h && x1 != h && z0 != -h && z1 != h) return@sequence
            // Absolute layer levels align along adjacent cells; the thin top band carries the sampled surface tone.
            val cuts =
                (listOf(SpaceScene.BOTTOM, -32.0, 0.0, 32.0, height - 2.0, height))
                    .filter { it >= SpaceScene.BOTTOM && it <= height }
                    .distinct()
                    .sorted()
            val sides =
                buildList {
                    if (x0 == -h) add(Vector3(x0, 0.0, z1) to Vector3(x0, 0.0, z0))
                    if (x1 == h) add(Vector3(x1, 0.0, z0) to Vector3(x1, 0.0, z1))
                    if (z0 == -h) add(Vector3(x0, 0.0, z0) to Vector3(x1, 0.0, z0))
                    if (z1 == h) add(Vector3(x1, 0.0, z1) to Vector3(x0, 0.0, z1))
                }
            for ((a, b) in sides) {
                for ((bottom, top) in cuts.zipWithNext()) {
                    val base =
                        when {
                            top == height -> surfaceColor
                            top <= -32.0 -> 0x64686a
                            top <= 0.0 -> 0x7d7f7d
                            top <= 32.0 -> 0x8f887c
                            else -> 0x8a8274
                        }
                    val tone = color(base, 1.0 + noise(a.x * 4.0, a.z * 4.0) * 0.12)
                    val normal = (b - a).cross(Vector3(0.0, -1.0, 0.0)).normalized()
                    yield(
                        SpaceScene.Face(
                            listOf(
                                Vector3(a.x, top, a.z),
                                Vector3(b.x, top, b.z),
                                Vector3(b.x, bottom, b.z),
                                Vector3(a.x, bottom, a.z),
                            ),
                            tone,
                            normal,
                            0.42,
                        ),
                    )
                }
            }
        }
}
