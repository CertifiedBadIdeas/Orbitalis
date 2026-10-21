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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProceduralCloudFieldTest {
    private val field = ProceduralCloudField(123456789L)

    @Test
    fun `columns are deterministic periodic patchy and vertically quantized`() {
        val duplicate = ProceduralCloudField(field.seed)
        val different = ProceduralCloudField(field.seed + 1)
        var filled = 0
        var changed = 0
        val thicknesses = mutableSetOf<Double>()
        for (x in -160..160 step 3) {
            for (z in -160..160 step 3) {
                val c = field.column(x, z)
                assertEquals(c, duplicate.column(x, z))
                assertEquals(c, field.column(x + 4096, z - 8192))
                assertEquals(c, field.column(x - 4096, z + 4096))
                if (c != different.column(x, z)) changed++
                if (c == null) continue
                filled++
                assertTrue(c.bottom >= 0.0 && c.top <= ProceduralCloudField.MAX_HEIGHT && c.bottom < c.top)
                assertEquals(0.0, c.bottom % 8.0)
                assertEquals(0.0, c.top % 8.0)
                thicknesses.add(c.top - c.bottom)
            }
        }
        assertTrue(filled in 500..9000, "Coverage should form separated masses: $filled")
        assertTrue(changed > 1000)
        assertTrue(thicknesses.size >= 5)
    }

    @Test
    fun `sector geometry is bounded aligned outward and periodic at every detail level`() {
        for (step in listOf(1, 2, 4, 8)) {
            for ((sx, sz) in listOf(-2 to -1, -1 to 0, 0 to 0, 1 to 1)) {
                val faces = field.sector(sx, sz, step)
                assertEquals(faces, field.sector(sx, sz, step))
                assertTrue(faces.size <= ProceduralCloudField.MAX_FACES_PER_SECTOR / (step * step))
                val translated = field.sector(sx + 128, sz - 128, step)
                assertEquals(faces.size, translated.size)
                faces.zip(translated).forEach { (face, moved) ->
                    assertEquals(face.normal, moved.normal)
                    assertEquals(face.vertices.map { it + Vector3(65536.0, 0.0, -65536.0) }, moved.vertices)
                    assertEquals(4, face.vertices.size)
                    assertEquals(1.0, face.normal.length())
                    val edge1 = face.vertices[1] - face.vertices[0]
                    val edge2 = face.vertices[2] - face.vertices[0]
                    assertTrue(edge1.cross(edge2).dot(face.normal) > 0.0)
                    face.vertices.forEach { v ->
                        assertTrue(v.x in sx * 512.0..(sx + 1) * 512.0)
                        assertTrue(v.z in sz * 512.0..(sz + 1) * 512.0)
                        assertTrue(v.y in 0.0..80.0)
                        assertEquals(0.0, v.x % (16.0 * step), 0.0)
                        assertEquals(0.0, v.z % (16.0 * step), 0.0)
                    }
                }
                val caps = faces.filter { it.normal.y > 0.0 }
                for (cap in caps) {
                    val cellX = (cap.vertices.minOf { it.x } / 16.0).toInt()
                    val cellZ = (cap.vertices.minOf { it.z } / 16.0).toInt()
                    assertEquals(field.column(cellX + step / 2, cellZ + step / 2)?.top, cap.vertices[0].y)
                }
            }
        }
        assertFailsWith<IllegalArgumentException> { field.sector(0, 0, 3) }
        assertFailsWith<IllegalArgumentException> { field.sector(Int.MAX_VALUE, 0) }
    }

    @Test
    fun `all side bands equal the occupied column minus its neighbor`() {
        var sides = 0
        for (step in listOf(1, 2, 4, 8)) {
            for (sx in -1..1) {
                for (sz in -1..1) {
                    val faces = field.sector(sx, sz, step)
                    for (face in faces.filter { it.normal.y == 0.0 }) {
                        val width = 16.0 * step
                        val minX = face.vertices.minOf { it.x }
                        val minZ = face.vertices.minOf { it.z }
                        val x = ((minX - if (face.normal.x > 0) width else 0.0) / 16.0).toInt() + step / 2
                        val z = ((minZ - if (face.normal.z > 0) width else 0.0) / 16.0).toInt() + step / 2
                        val own = requireNotNull(field.column(x, z))
                        val neighbor = field.column(x + (face.normal.x * step).toInt(), z + (face.normal.z * step).toInt())
                        val low = face.vertices.minOf { it.y }
                        val high = face.vertices.maxOf { it.y }
                        assertEquals(own.top - own.bottom, face.thickness)
                        assertTrue(low >= own.bottom && high <= own.top)
                        if (neighbor != null) assertTrue(high <= neighbor.bottom || low >= neighbor.top)
                        sides++
                    }
                    for (cap in faces.filter { it.normal.y != 0.0 }) {
                        val x = (cap.vertices.minOf { it.x } / 16.0).toInt() + step / 2
                        val z = (cap.vertices.minOf { it.z } / 16.0).toInt() + step / 2
                        val own = requireNotNull(field.column(x, z))
                        assertEquals(own.top - own.bottom, cap.thickness)
                    }
                }
            }
        }
        assertTrue(sides > 100)
    }

    @Test
    fun `neighboring sector halo removes interior side faces including negative sectors`() {
        var overlappingPairs = 0
        for (step in listOf(1, 2, 4, 8)) {
            for (sx in -2..1) {
                val left = field.sector(sx, -1, step)
                val right = field.sector(sx + 1, -1, step)
                val boundary = (sx + 1) * 512.0
                for (z in -32 until 0 step step) {
                    val a = field.column((sx + 1) * 32 - step + step / 2, z + step / 2) ?: continue
                    val b = field.column((sx + 1) * 32 + step / 2, z + step / 2) ?: continue
                    val low = maxOf(a.bottom, b.bottom)
                    val high = minOf(a.top, b.top)
                    if (high <= low) continue
                    overlappingPairs++
                    val seamFaces =
                        (left + right).filter { f ->
                            f.normal.x != 0.0 && f.vertices.all { it.x == boundary } &&
                                f.vertices.minOf { it.z } == z * 16.0
                        }
                    seamFaces.forEach { f ->
                        assertTrue(f.vertices.maxOf { it.y } <= low || f.vertices.minOf { it.y } >= high)
                    }
                }
            }
        }
        assertTrue(overlappingPairs > 10)
    }
}
