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
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import kotlin.math.cos
import kotlin.math.sin

/** Presentation only: a finite flat body in surface coordinates, independent of diagnostic gravity. */
object SpaceScene {
    const val HALF_SIZE = FlatPlanetSpace.HALF_SIZE
    const val TOP = FlatPlanetSpace.SEA_LEVEL
    const val BOTTOM = -FlatPlanetSpace.SEA_LEVEL
    val SUN_DIRECTION = Vector3(-0.4, 0.8, -0.3).normalized()

    data class Face(
        val vertices: List<Vector3>,
        val color: Int,
        val normal: Vector3,
        val ambient: Double = 0.18,
    )

    val body: List<Face> =
        buildList {
            val cells = 32
            val step = HALF_SIZE * 2.0 / cells
            for (i in 0 until cells) {
                for (j in 0 until cells) {
                    val x = -HALF_SIZE + i * step
                    val z = -HALF_SIZE + j * step
                    val land = sin(i * 0.31) + cos(j * 0.27) + sin((i + j) * 0.47)
                    val color =
                        if (land > 0.45) {
                            0x56834c
                        } else if (land > 0.15) {
                            0xb3a475
                        } else {
                            0x245a85
                        }
                    add(
                        Face(
                            listOf(
                                Vector3(x, TOP, z),
                                Vector3(x + step, TOP, z),
                                Vector3(x + step, TOP, z + step),
                                Vector3(x, TOP, z + step),
                            ),
                            color,
                            Vector3(0.0, 1.0, 0.0),
                        ),
                    )
                }
            }
            addAll(PlanetRockShell.faces)
        }

    /** One uniform camera-relative scale preserves perspective while fitting inside the sky pass. */
    fun scale(
        observer: Vector3,
        verticalExtent: Double = maxOf(kotlin.math.abs(TOP), kotlin.math.abs(BOTTOM)),
    ): Double = 60.0 / (observer.length() + Vector3(HALF_SIZE, verticalExtent, HALF_SIZE).length())

    fun visible(
        face: Face,
        observer: Vector3,
    ): Boolean = (observer - face.vertices.first()).dot(face.normal) > 0.0
}
