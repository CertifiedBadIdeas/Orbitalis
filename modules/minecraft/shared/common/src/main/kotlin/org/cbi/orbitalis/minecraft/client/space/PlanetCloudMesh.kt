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

import kotlin.math.ceil
import kotlin.math.floor

/** Periodic textured volume: runtime alpha cuts the same 12m columns as native clouds. */
internal object PlanetCloudMesh {
    const val CELL = 12f
    const val PERIOD = 3072f
    const val THICKNESS = 4f
    private const val INSET = 1f / 1024f
    private const val Z_PHASE = 0.33000001311302185 * 12.0

    data class Vertex(
        val x: Float,
        val y: Float,
        val z: Float,
        val u: Float,
        val v: Float,
        val shade: Float,
    )

    data class Tile(
        val x: Float,
        val z: Float,
    )

    fun tiles(
        half: Double,
        time: Float,
    ): List<Tile> {
        require(half.isFinite() && half > 0.0 && half <= 10000.0)
        val phase = ((time * 0.03f).toDouble() % PERIOD + PERIOD) % PERIOD
        val xs = floor((-half + phase) / PERIOD).toInt() until ceil((half + phase) / PERIOD).toInt()
        val zs = floor((-half + Z_PHASE) / PERIOD).toInt() until ceil((half + Z_PHASE) / PERIOD).toInt()
        return xs.flatMap { x -> zs.map { z -> Tile((x * PERIOD - phase).toFloat(), (z * PERIOD - Z_PHASE).toFloat()) } }
    }

    fun vertices(fancy: Boolean): List<Vertex> =
        buildList {
            fun vertex(
                x: Float,
                y: Float,
                z: Float,
                shade: Float,
                u: Float = x / PERIOD,
                v: Float = z / PERIOD,
            ) {
                add(Vertex(x, y, z, u, v, shade))
            }

            fun cap(
                y: Float,
                shade: Float,
                top: Boolean,
            ) {
                val corners =
                    if (top) {
                        listOf(0f to 0f, 0f to PERIOD, PERIOD to PERIOD, PERIOD to 0f)
                    } else {
                        listOf(0f to 0f, PERIOD to 0f, PERIOD to PERIOD, 0f to PERIOD)
                    }
                for ((x, z) in corners) vertex(x, y, z, shade)
            }
            if (!fancy) {
                cap(0f, 1f, true)
                return@buildList
            }
            cap(0f, 0.7f, false)
            cap(THICKNESS - INSET, 1f, true)
            for (cell in 0 until 256) {
                val low = cell * CELL
                val high = (cell + 1) * CELL - CELL * INSET
                val uv = (cell + 0.5f) / 256f
                // Opposite sides sample the same column; depth resolves internal textured slices.
                for ((x, positive) in listOf(low to false, high to true)) {
                    val corners =
                        if (positive) {
                            listOf(0f to 0f, THICKNESS to 0f, THICKNESS to PERIOD, 0f to PERIOD)
                        } else {
                            listOf(0f to 0f, 0f to PERIOD, THICKNESS to PERIOD, THICKNESS to 0f)
                        }
                    for ((y, z) in corners) vertex(x, y, z, 0.9f, uv, z / PERIOD)
                }
                for ((z, positive) in listOf(low to false, high to true)) {
                    val corners =
                        if (positive) {
                            listOf(0f to 0f, PERIOD to 0f, PERIOD to THICKNESS, 0f to THICKNESS)
                        } else {
                            listOf(0f to 0f, 0f to THICKNESS, PERIOD to THICKNESS, PERIOD to 0f)
                        }
                    for ((x, y) in corners) vertex(x, y, z, 0.8f, x / PERIOD, uv)
                }
            }
        }
}
