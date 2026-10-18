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
    fun undersideHasOnlyItsPerimeterOpenAndOutwardNormals() {
        data class Edge(
            val a: Vector3,
            val b: Vector3,
        )

        fun edge(
            a: Vector3,
            b: Vector3,
        ): Edge {
            val ordered = compareValuesBy(a, b, { it.x }, { it.y }, { it.z }) <= 0
            return if (ordered) Edge(a, b) else Edge(b, a)
        }
        val edges = mutableMapOf<Edge, Int>()
        for (face in PlanetRockShell.faces) {
            val vertices = face.vertices.distinct()
            assertEquals(3, vertices.size)
            assertEquals(1.0, face.normal.length(), 1e-10)
            assertTrue(face.normal.dot((vertices[1] - vertices[0]).cross(vertices[2] - vertices[0])) > 0)
            for (i in vertices.indices) {
                val key = edge(vertices[i], vertices[(i + 1) % vertices.size])
                edges[key] = (edges[key] ?: 0) + 1
            }
            assertTrue(vertices.all { it.x in -5000.0..5000.0 && it.z in -5000.0..5000.0 })
            assertTrue(vertices.all { it.y == -64.0 })
            if (vertices.all { it.y < 0 }) assertTrue(face.normal.y <= 0.0)
        }
        val open = edges.filterValues { it == 1 }.keys
        assertEquals(256, open.size)
        assertTrue(edges.values.all { it in 1..2 }, "No holes or non-manifold joins")
        assertTrue(open.all { it.a.y == SpaceScene.BOTTOM && it.b.y == SpaceScene.BOTTOM })
        assertTrue(
            open.all { edge ->
                listOf(edge.a, edge.b).all { kotlin.math.abs(it.x) == 5000.0 || kotlin.math.abs(it.z) == 5000.0 }
            },
        )
        assertTrue(PlanetRockShell.faces.size <= 8192)
        val area =
            PlanetRockShell.faces.sumOf {
                val (a, b, c) = it.vertices
                (b - a).cross(c - a).length() * 0.5
            }
        assertEquals(100_000_000.0, area, 1e-5)
    }

    @Test
    fun retreatMakesTheBodySmaller() {
        val vertex = Vector3(SpaceScene.HALF_SIZE, SpaceScene.TOP, 0.0)
        val near = vertex - Vector3(0.0, SpaceScene.TOP + 1000.0, 0.0)
        val far = vertex - Vector3(0.0, SpaceScene.TOP + 10000.0, 0.0)
        assertTrue(atan2(near.x, -near.y) > atan2(far.x, -far.y))
        assertEquals(10000.0, SpaceScene.HALF_SIZE * 2.0)
    }
}
