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

import org.cbi.orbitalis.core.planet.InnerPlanetSurface
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InnerPlanetMeshTest {
    @Test fun fractionalFiniteFootprintsAreClippedAndPreserveNativeSampleHeights() {
        for (kind in InnerPlanetSurface.Kind.entries) {
            val half = if (kind == InnerPlanetSurface.Kind.MERCURY) 191.5 else 474.5
            val mesh = InnerPlanetMesh(kind, half)
            val terrain = InnerPlanetSurface(kind, 719, half)
            val faces = mesh.sectors.flatMap { mesh.faces(719, it).toList() }
            val tops = faces.filter { it.normal.y > 0 }
            assertEquals(
                half * half * 4,
                tops.sumOf {
                    val a = it.vertices[0]
                    val b = it.vertices[2]
                    (b.x - a.x) * (b.z - a.z)
                },
            )
            assertTrue(tops.size <= 256 * 256)
            for (face in tops) {
                val a = face.vertices[0]
                val b = face.vertices[2]
                val x =
                    kotlin.math.floor(kotlin.math.floor(a.x / mesh.step) * mesh.step + mesh.step / 2).toInt().coerceIn(
                        ceil(-half).toInt(),
                        ceil(half).toInt() - 1,
                    )
                val z =
                    kotlin.math.floor(kotlin.math.floor(a.z / mesh.step) * mesh.step + mesh.step / 2).toInt().coerceIn(
                        ceil(-half).toInt(),
                        ceil(half).toInt() - 1,
                    )
                assertEquals(terrain.height(x, z)!!.toDouble() + 1, a.y)
                assertTrue(b.x > a.x && b.z > a.z)
            }
            assertTrue(faces.all { f -> f.vertices.all { it.x in -half..half && it.z in -half..half && it.y in -64.0..125.0 } })
        }
    }

    @Test fun coarsePlanetsRemainBoundedAtAllSelectedSizes() {
        for (half in listOf(191.5, 474.5, 1915.0, 4745.0, 9490.0)) {
            val mesh = InnerPlanetMesh(InnerPlanetSurface.Kind.VENUS, half)
            val faces = mesh.sectors.flatMap { mesh.coarse(it).toList() }
            val tops = faces.filter { it.normal.y > 0 }
            assertEquals(
                half * half * 4,
                tops.sumOf {
                    val a = it.vertices[0]
                    val b = it.vertices[2]
                    (b.x - a.x) * (b.z - a.z)
                },
                0.001,
            )
            assertTrue(faces.size < 10000)
            assertTrue(faces.all { f -> f.vertices.all { it.x in -half..half && it.z in -half..half } })
            assertTrue(mesh.step <= 16.0)
        }
    }
}
