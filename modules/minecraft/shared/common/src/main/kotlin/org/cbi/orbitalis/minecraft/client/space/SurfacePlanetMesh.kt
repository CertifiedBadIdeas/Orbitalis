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

import org.cbi.orbitalis.core.planet.SurfaceAtlas
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.floor

/** Fixed overview budget: 314 squared top cells, at most two interior skirts per cell. */
object SurfacePlanetMesh {
    const val STEP = 32.0
    private val low = floor(-SpaceScene.HALF_SIZE / STEP).toInt()
    private val high = floor((SpaceScene.HALF_SIZE - 1.0) / STEP).toInt()

    fun faces(atlas: SurfaceAtlas): Sequence<SpaceScene.Face> =
        sequence {
            val cells = atlas.overview()

            fun height(
                x: Int,
                z: Int,
            ) = cells[x to z]?.height ?: SpaceScene.TOP

            fun color(
                x: Int,
                z: Int,
            ) = cells[x to z]?.color ?: fallback(x * STEP, z * STEP)
            for (x in low..high) {
                for (z in low..high) {
                    val x0 = maxOf(-SpaceScene.HALF_SIZE, x * STEP)
                    val x1 = minOf(SpaceScene.HALF_SIZE, (x + 1) * STEP)
                    val z0 = maxOf(-SpaceScene.HALF_SIZE, z * STEP)
                    val z1 = minOf(SpaceScene.HALF_SIZE, (z + 1) * STEP)
                    val h = height(x, z)
                    val rgb = color(x, z)
                    yield(
                        SpaceScene.Face(
                            listOf(Vector3(x0, h, z0), Vector3(x1, h, z0), Vector3(x1, h, z1), Vector3(x0, h, z1)),
                            rgb,
                            Vector3(0.0, 1.0, 0.0),
                        ),
                    )
                    // Each shared edge is emitted once, including known/fallback and unequal terrain heights.
                    if (x < high && h != height(x + 1, z)) {
                        val other = height(x + 1, z)
                        val normal = Vector3(if (h > other) 1.0 else -1.0, 0.0, 0.0)
                        yield(
                            SpaceScene.Face(
                                listOf(Vector3(x1, h, z0), Vector3(x1, h, z1), Vector3(x1, other, z1), Vector3(x1, other, z0)),
                                if (h >
                                    other
                                ) {
                                    rgb
                                } else {
                                    color(x + 1, z)
                                },
                                normal,
                            ),
                        )
                    }
                    if (z < high && h != height(x, z + 1)) {
                        val other = height(x, z + 1)
                        val normal = Vector3(0.0, 0.0, if (h > other) 1.0 else -1.0)
                        yield(
                            SpaceScene.Face(
                                listOf(Vector3(x0, h, z1), Vector3(x1, h, z1), Vector3(x1, other, z1), Vector3(x0, other, z1)),
                                if (h >
                                    other
                                ) {
                                    rgb
                                } else {
                                    color(x, z + 1)
                                },
                                normal,
                            ),
                        )
                    }
                    // The terrain edge joins the unchanged lower side strata even above/below sea level.
                    if (h != SpaceScene.TOP) {
                        if (x == low || x == high) {
                            val edge = if (x == low) x0 else x1
                            yield(
                                SpaceScene.Face(
                                    listOf(
                                        Vector3(edge, h, z0),
                                        Vector3(edge, h, z1),
                                        Vector3(edge, SpaceScene.TOP, z1),
                                        Vector3(edge, SpaceScene.TOP, z0),
                                    ),
                                    rgb,
                                    Vector3(
                                        if (x ==
                                            low
                                        ) {
                                            -1.0
                                        } else {
                                            1.0
                                        },
                                        0.0,
                                        0.0,
                                    ),
                                ),
                            )
                        }
                        if (z == low || z == high) {
                            val edge = if (z == low) z0 else z1
                            yield(
                                SpaceScene.Face(
                                    listOf(
                                        Vector3(x0, h, edge),
                                        Vector3(x1, h, edge),
                                        Vector3(x1, SpaceScene.TOP, edge),
                                        Vector3(x0, SpaceScene.TOP, edge),
                                    ),
                                    rgb,
                                    Vector3(
                                        0.0,
                                        0.0,
                                        if (z ==
                                            low
                                        ) {
                                            -1.0
                                        } else {
                                            1.0
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
            yieldAll(SpaceScene.body.filter { it.normal.y <= 0.0 })
        }

    private fun fallback(
        x: Double,
        z: Double,
    ): Int {
        val step = SpaceScene.HALF_SIZE * 2.0 / 32.0
        val i = floor((x + SpaceScene.HALF_SIZE) / step).coerceIn(0.0, 31.0)
        val j = floor((z + SpaceScene.HALF_SIZE) / step).coerceIn(0.0, 31.0)
        val land = kotlin.math.sin(i * 0.31) + kotlin.math.cos(j * 0.27) + kotlin.math.sin((i + j) * 0.47)
        return if (land > 0.45) {
            0x56834c
        } else if (land > 0.15) {
            0xb3a475
        } else {
            0x245a85
        }
    }
}
