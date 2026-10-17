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
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpaceSceneTest {
    @Test
    fun skyProjectionPreservesDirectionsAndAngularSizeAtAllSides() {
        val observers =
            listOf(
                Vector3(0.0, 1064.0, 0.0),
                Vector3(0.0, -2064.0, 0.0),
                Vector3(1500.0, 64.0, 1500.0),
                Vector3(-1500.0, 64.0, -1500.0),
                Vector3(0.0, 80000.0, 0.0),
            )
        for (observer in observers) {
            val scale = SpaceScene.scale(observer)
            for (face in SpaceScene.body) {
                for (vertex in face.vertices) {
                    val relative = vertex - observer
                    val projected = relative * scale
                    assertTrue(projected.length() <= 60.000001, "Mesh must fit the near sky shell")
                    assertEquals(atan2(relative.x, relative.y), atan2(projected.x, projected.y), 1e-12)
                    assertEquals(atan2(relative.z, relative.y), atan2(projected.z, projected.y), 1e-12)
                }
            }
        }
    }

    @Test
    fun undersideAndEdgesOccludeFarFaces() {
        fun normals(observer: Vector3) =
            SpaceScene.body
                .filter { SpaceScene.visible(it, observer) }
                .map { it.normal }
                .toSet()
        assertEquals(setOf(Vector3(0.0, 1.0, 0.0)), normals(Vector3(0.0, 1064.0, 0.0)))
        assertEquals(setOf(Vector3(0.0, -1.0, 0.0)), normals(Vector3(0.0, -1064.0, 0.0)))
        assertEquals(setOf(Vector3(1.0, 0.0, 0.0)), normals(Vector3(2000.0, 0.0, 0.0)))
        assertEquals(
            setOf(Vector3(0.0, 1.0, 0.0), Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0)),
            normals(Vector3(2000.0, 2000.0, -2000.0)),
        )
    }

    @Test
    fun retreatMakesTheBodySmaller() {
        val vertex = Vector3(SpaceScene.HALF_SIZE, SpaceScene.TOP, 0.0)
        val near = vertex - Vector3(0.0, SpaceScene.TOP + 1000.0, 0.0)
        val far = vertex - Vector3(0.0, SpaceScene.TOP + 10000.0, 0.0)
        assertTrue(atan2(near.x, -near.y) > atan2(far.x, -far.y))
        assertEquals(2000.0, SpaceScene.HALF_SIZE * 2.0)
    }
}
