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

package org.cbi.orbitalis.core.planet

import org.cbi.orbitalis.core.orbit.Rotation3
import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarVisibilityTest {
    private val observer = Vector3.ZERO
    private val source = SolarEmitterCube(Vector3(0.0, 100.0, 0.0), 1.0)
    private val left = OpaqueSolarBox(Vector3(-0.27, 50.0, 0.0), Vector3(0.28, 1.0, 1.0))
    private val right = left.copy(centre = Vector3(0.27, 50.0, 0.0))

    @Test
    fun `clear partial full and interior visibility remain bounded`() {
        assertEquals(1.0, SolarVisibility(source, emptyList()).visibility(observer))
        assertTrue(SolarVisibility(source, listOf(left)).visibility(observer) in 0.01..0.99)
        assertEquals(0.0, SolarVisibility(source, listOf(left, right)).visibility(observer))
        assertEquals(0.0, SolarVisibility(source, listOf(left)).visibility(left.centre))
    }

    @Test
    fun `overlapping silhouettes form a union independent of order and duplicates`() {
        val partial = SolarVisibility(source, listOf(left)).visibility(observer)
        assertEquals(partial, SolarVisibility(source, listOf(left, left)).visibility(observer))
        assertEquals(
            SolarVisibility(source, listOf(left, right)).visibility(observer),
            SolarVisibility(source, listOf(right, left, right)).visibility(observer),
        )
    }

    @Test
    fun `scene snapshots the supplied body collection`() {
        val bodies = mutableListOf(left)
        val scene = SolarVisibility(source, bodies)
        val before = scene.visibility(observer)
        bodies.add(right)
        assertEquals(before, scene.visibility(observer))
    }

    @Test
    fun `common translation rotation and scaling preserve illumination`() {
        val rotation = Rotation3.axisAngle(Vector3(1.0, 2.0, 3.0), PI / 3.0)
        val offset = Vector3(123456.0, -345678.0, 678901.0)
        val expected = SolarVisibility(source, listOf(left)).visibility(observer)
        for (scale in listOf(0.001, 1.0, 1000.0)) {
            val movedSource = SolarEmitterCube(offset + rotation.apply(source.centre * scale), scale, rotation)
            val movedBody = OpaqueSolarBox(offset + rotation.apply(left.centre * scale), left.halfSize * scale, rotation)
            assertEquals(expected, SolarVisibility(movedSource, listOf(movedBody)).visibility(offset))
        }
    }

    @Test
    fun `depth culling eliminates bodies behind observer and source`() {
        val behindSource = OpaqueSolarBox(Vector3(0.0, 110.0, 0.0), Vector3(1.0, 1.0, 1.0))
        val behindObserver = behindSource.copy(centre = Vector3(0.0, -10.0, 0.0))
        val scene = SolarVisibility(source, listOf(behindSource, behindObserver))
        assertTrue(scene.candidates(observer).isEmpty())
        assertEquals(1.0, scene.visibility(observer))
    }

    @Test
    fun `many off axis bodies leave only the useful candidate for ray testing`() {
        val offAxis = (1..256).map { OpaqueSolarBox(Vector3(100.0 + it, 50.0, 0.0), Vector3(1.0, 1.0, 1.0)) }
        val scene = SolarVisibility(source, offAxis + left)
        assertEquals(listOf(left), scene.candidates(observer))
        assertEquals(SolarVisibility(source, listOf(left)).visibility(observer), scene.visibility(observer))
    }

    @Test
    fun `broadphase matches exhaustive ray union for rotated large and grazing boxes`() {
        val random = Random(9234)
        repeat(200) {
            val emitter = source.copy(orientation = Rotation3.axisAngle(Vector3(1.0, 1.0, 0.0), random.nextDouble(-PI, PI)))
            val bodies =
                (1..12).map {
                    OpaqueSolarBox(
                        Vector3(random.nextDouble(-12.0, 12.0), random.nextDouble(-20.0, 130.0), random.nextDouble(-12.0, 12.0)),
                        Vector3(random.nextDouble(0.01, 7.0), random.nextDouble(0.01, 7.0), random.nextDouble(0.01, 7.0)),
                        Rotation3.axisAngle(Vector3(1.0, 2.0, 3.0), random.nextDouble(-PI, PI)),
                    )
                }
            val exhaustive =
                if (bodies.any { it.contains(observer) }) {
                    0.0
                } else {
                    (0 until SolarOcclusion.SAMPLE_COUNT)
                        .count { i ->
                            bodies.none { it.intersects(observer, emitter.sample(observer, i)) }
                        }.toDouble() / SolarOcclusion.SAMPLE_COUNT
                }
            assertEquals(exhaustive, SolarVisibility(emitter, bodies).visibility(observer))
        }
    }

    @Test
    fun `legacy lunar chart uses identical source samples and visibility`() {
        val half = Vector3(1.0, 0.1, 1.0)
        val legacy = SolarOcclusion(source.centre, source.halfEdge, source.orientation, half)
        val scene = SolarVisibility(source, listOf(OpaqueSolarBox(Vector3.ZERO, half)))
        for (point in listOf(Vector3(0.0, -10.0, 0.0), Vector3(1.1, -10.0, 0.0), Vector3(1.3, -10.0, 0.0))) {
            assertEquals(legacy.visibility(point), scene.visibility(point))
            for (i in 0 until SolarOcclusion.SAMPLE_COUNT) assertEquals(legacy.sample(point, i), source.sample(point, i))
        }
    }
}
