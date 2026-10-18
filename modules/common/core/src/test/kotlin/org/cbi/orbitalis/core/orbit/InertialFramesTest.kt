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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InertialFramesTest {
    private val zero = CartesianState(Vector3.ZERO, Vector3.ZERO)
    private val root = InertialFrame("test:root", null, zero)

    @Test
    fun `hierarchical origins and velocities transform at their own epochs and round trip`() {
        val frames =
            InertialFrames(
                listOf(
                    root,
                    InertialFrame("test:planet", root.id, CartesianState(Vector3(100.0, 0.0, 0.0), Vector3(2.0, 0.0, 0.0)), 20),
                    InertialFrame("test:moon", "test:planet", CartesianState(Vector3(0.0, 50.0, 0.0), Vector3(0.0, 3.0, 0.0)), 40),
                    InertialFrame("test:other", root.id, CartesianState(Vector3(-50.0, 0.0, 0.0), Vector3(-1.0, 0.0, 0.0))),
                ),
            )
        val local = CartesianState(Vector3(1.0, 2.0, 3.0), Vector3(4.0, 5.0, 6.0))
        val absolute = frames.transform(local, "test:moon", root.id, 100)
        assertEquals(CartesianState(Vector3(109.0, 61.0, 3.0), Vector3(6.0, 8.0, 6.0)), absolute)
        val other = frames.transform(local, "test:moon", "test:other", 100)
        assertEquals(CartesianState(Vector3(164.0, 61.0, 3.0), Vector3(7.0, 8.0, 6.0)), other)
        assertEquals(local, frames.transform(other, "test:other", "test:moon", 100))
        val earlier = frames.transform(zero, "test:moon", root.id, 0)
        assertEquals(Vector3(98.0, 44.0, 0.0), earlier.position)
    }

    @Test
    fun `common distant ancestor cancels without losing local precision`() {
        val frames =
            InertialFrames(
                listOf(
                    root,
                    InertialFrame("test:distant", root.id, CartesianState(Vector3(1e18, 0.0, 0.0), Vector3.ZERO)),
                    InertialFrame("test:a", "test:distant", CartesianState(Vector3(1.0, 0.0, 0.0), Vector3.ZERO)),
                    InertialFrame("test:b", "test:distant", CartesianState(Vector3(2.0, 0.0, 0.0), Vector3.ZERO)),
                ),
            )
        assertEquals(Vector3(-1.0, 0.0, 0.0), frames.transform(zero, "test:a", "test:b", 1).position)
        assertEquals(zero, frames.transform(zero, "test:distant", "test:distant", Long.MAX_VALUE))
    }

    @Test
    fun `invalid identifiers missing parents duplicate roots and cycles fail before use`() {
        val invalid =
            listOf(
                emptyList(),
                listOf(root, root),
                listOf(root.copy(id = "Bad frame")),
                listOf(root, root.copy(id = "test:second")),
                listOf(root.copy(origin = CartesianState(Vector3.ZERO, Vector3(1.0, 0.0, 0.0)))),
                listOf(root.copy(origin = CartesianState(Vector3(1.0, 0.0, 0.0), Vector3.ZERO))),
                listOf(root.copy(epochTick = -1)),
                listOf(root, InertialFrame("test:a", "test:missing", zero)),
                listOf(root, InertialFrame("test:a", "test:a", zero)),
                listOf(root, InertialFrame("test:a", "test:b", zero), InertialFrame("test:b", "test:a", zero)),
            )
        for (frames in invalid) assertFailsWith<IllegalArgumentException> { InertialFrames(frames) }
        val frames = InertialFrames(listOf(root))
        assertFailsWith<IllegalArgumentException> { frames.transform(zero, "test:missing", root.id, 0) }
        assertFailsWith<IllegalArgumentException> { frames.transform(zero, root.id, "test:missing", 0) }
        assertFailsWith<IllegalArgumentException> { frames.transform(zero, root.id, root.id, -1) }
    }

    @Test
    fun `input list mutation cannot alter an existing hierarchy`() {
        val input = mutableListOf(root, InertialFrame("test:a", root.id, zero))
        val frames = InertialFrames(input)
        input.clear()
        assertTrue(frames.transform(zero, "test:a", root.id, 1) == zero)
    }
}
