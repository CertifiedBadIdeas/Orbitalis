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

/** Fixed overview budget: 626 squared top cells, at most two interior skirts per cell. */
object SurfacePlanetMesh {
    const val STEP = 16.0
    private val low = floor(-SpaceScene.HALF_SIZE / STEP).toInt()
    private val high = floor((SpaceScene.HALF_SIZE - 1.0) / STEP).toInt()

    data class Sector(
        val x: Int,
        val z: Int,
    )

    const val SECTOR_CHUNKS = 32
    val sectors =
        (Math.floorDiv(low, SECTOR_CHUNKS)..Math.floorDiv(high, SECTOR_CHUNKS)).flatMap { x ->
            (Math.floorDiv(low, SECTOR_CHUNKS)..Math.floorDiv(high, SECTOR_CHUNKS)).map { z -> Sector(x, z) }
        }

    fun sector(
        x: Int,
        z: Int,
    ) = Sector(Math.floorDiv(x, SECTOR_CHUNKS), Math.floorDiv(z, SECTOR_CHUNKS))

    /** Preserve the existing fallback coast pattern with clipped macro cells while fine sectors build. */
    fun coarse(
        sector: Sector,
        halfSize: Double = SpaceScene.HALF_SIZE,
        top: Double = SpaceScene.TOP,
        surfaceColor: Int? = null,
        strata: List<Int> = listOf(0x64686a, 0x7d7f7d, 0x8f887c, 0x8a8274),
    ): Sequence<SpaceScene.Face> =
        sequence {
            val x0 = maxOf(-halfSize, sector.x * SECTOR_CHUNKS * STEP)
            val x1 = minOf(halfSize, (sector.x + 1) * SECTOR_CHUNKS * STEP)
            val z0 = maxOf(-halfSize, sector.z * SECTOR_CHUNKS * STEP)
            val z1 = minOf(halfSize, (sector.z + 1) * SECTOR_CHUNKS * STEP)
            val macro = halfSize * 2.0 / 32.0
            val xs = (listOf(x0, x1) + (1..31).map { -halfSize + it * macro }.filter { it > x0 && it < x1 }).sorted()
            val zs = (listOf(z0, z1) + (1..31).map { -halfSize + it * macro }.filter { it > z0 && it < z1 }).sorted()
            for ((a, b) in xs.zipWithNext()) {
                for ((c, d) in zs.zipWithNext()) {
                    yield(
                        SpaceScene.Face(
                            listOf(
                                Vector3(a, top, c),
                                Vector3(b, top, c),
                                Vector3(b, top, d),
                                Vector3(a, top, d),
                            ),
                            (surfaceColor ?: fallback((a + b) / 2, (c + d) / 2)),
                            Vector3(0.0, 1.0, 0.0),
                        ),
                    )
                    yieldAll(
                        PlanetRockShell.walls(
                            a,
                            b,
                            c,
                            d,
                            top,
                            (surfaceColor ?: fallback((a + b) / 2, (c + d) / 2)),
                            halfSize,
                            SpaceScene.BOTTOM,
                            strata,
                        ),
                    )
                }
            }
        }

    fun faces(
        atlas: SurfaceAtlas,
        sector: Sector? = null,
        halfSize: Double = SpaceScene.HALF_SIZE,
        bottom: Double = SpaceScene.BOTTOM,
        strata: List<Int> = listOf(0x64686a, 0x7d7f7d, 0x8f887c, 0x8a8274),
    ): Sequence<SpaceScene.Face> =
        sequence {
            val low = floor(-halfSize / STEP).toInt()
            val high = floor((halfSize - 1.0) / STEP).toInt()
            val cells = atlas.overview()

            fun height(
                x: Int,
                z: Int,
            ) = cells[x to z]?.height ?: SpaceScene.TOP

            fun color(
                x: Int,
                z: Int,
            ) = cells[x to z]?.color ?: fallback(x * STEP, z * STEP)
            val xs =
                if (sector ==
                    null
                ) {
                    low..high
                } else {
                    maxOf(low, sector.x * SECTOR_CHUNKS)..minOf(high, (sector.x + 1) * SECTOR_CHUNKS - 1)
                }
            val zs =
                if (sector ==
                    null
                ) {
                    low..high
                } else {
                    maxOf(low, sector.z * SECTOR_CHUNKS)..minOf(high, (sector.z + 1) * SECTOR_CHUNKS - 1)
                }
            for (x in xs) {
                for (z in zs) {
                    val x0 = maxOf(-halfSize, x * STEP)
                    val x1 = minOf(halfSize, (x + 1) * STEP)
                    val z0 = maxOf(-halfSize, z * STEP)
                    val z1 = minOf(halfSize, (z + 1) * STEP)
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
                    if (x == low || x == high || z == low || z == high) {
                        yieldAll(PlanetRockShell.walls(x0, x1, z0, z1, h, rgb, halfSize, bottom, strata))
                    }
                }
            }
            if (sector == null) yieldAll(PlanetRockShell.underside(halfSize, bottom))
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
