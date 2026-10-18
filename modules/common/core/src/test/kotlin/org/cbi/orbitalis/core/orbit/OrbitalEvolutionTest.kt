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
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.RectangularGravity
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OrbitalEvolutionTest {
    private val initial = CartesianState(Vector3(400.0, 4000.0, 200.0), Vector3(100.0, 2.0, 30.0))

    @Test
    fun `constant force follows analytic motion in physical seconds`() {
        val force = Vector3(0.0, -2.0, 0.0)
        val result = OrbitalEvolution.advance(initial, 200) { force }
        assertTrue((result.position - (initial.position + initial.velocity * 10.0 + force * 50.0)).length() < 1e-8)
        assertTrue((result.velocity - (initial.velocity + force * 10.0)).length() < 1e-10)
    }

    @Test
    fun `partitioning unloaded intervals gives identical end state`() {
        val field = RectangularGravity(1000.0, 64.0, 1000.0, 9.81)
        val whole = OrbitalEvolution.advance(initial, 1200, field::acceleration)
        var segmented = initial
        for (count in listOf(1L, 19L, 400L, 780L)) segmented = OrbitalEvolution.advance(segmented, count, field::acceleration)
        assertEquals(whole, segmented)
        assertTrue(abs(field.specificEnergy(whole) - field.specificEnergy(initial)) < 0.01)
        // A slab is noncentral here; conservation of angular momentum must not be assumed.
        assertTrue((whole.position.cross(whole.velocity) - initial.position.cross(initial.velocity)).length() > 1.0)
    }

    @Test
    fun `ten minute segmented slab orbit retains energy without assuming a Kepler ellipse`() {
        val field = RectangularGravity(1000.0, 64.0, 1000.0, 9.81)
        val start = CartesianState(Vector3(0.0, 4000.0, 0.0), Vector3(kotlin.math.sqrt(field.mu / 4000.0), 0.0, 0.0))
        val energy = field.specificEnergy(start)
        var state = start
        repeat(10) {
            state = OrbitalEvolution.advance(state, 1200, field::acceleration)
            assertTrue(abs(field.specificEnergy(state) - energy) / abs(energy) < 1e-5)
            assertTrue(!field.contains(state.position))
        }
        assertTrue((state.position - start.position).length() > 1000.0)
    }

    @Test
    fun `evolution matches independent coast predictor in the real finite field`() {
        val field = SurfaceGravityLayer(RectangularGravity(1000.0, 64.0, 1000.0, 9.81), 32.0)
        val evolved = OrbitalEvolution.advance(initial, 200, field::acceleration)
        val predicted = CoastPrediction.predict(field, initial, CoastPrediction.Limits(horizon = 10.0))
        assertEquals(CoastPrediction.End.HORIZON, predicted.end)
        assertTrue((predicted.terminal.state.position - evolved.position).length() < 1e-7)
        assertTrue((predicted.terminal.state.velocity - evolved.velocity).length() < 1e-9)
    }

    @Test
    fun `zero ticks uses no field and excessive durations and states are rejected`() {
        assertSame(initial, OrbitalEvolution.advance(initial, 0) { error("Zero ticks must not sample the field") })
        for (ticks in listOf(-1L, 1201L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { OrbitalEvolution.advance(initial, ticks) { Vector3.ZERO } }
        }
        assertFailsWith<IllegalArgumentException> {
            OrbitalEvolution.advance(initial.copy(position = Vector3(1e13, 0.0, 0.0)), 1) { Vector3.ZERO }
        }
        assertFailsWith<IllegalArgumentException> {
            OrbitalEvolution.advance(initial, 1) { Vector3(1e10, 0.0, 0.0) }
        }
    }

    @Test
    fun `interruption preserves thread flag and publishes no partial result`() {
        val thread = Thread.currentThread()
        try {
            thread.interrupt()
            assertFailsWith<InterruptedException> { OrbitalEvolution.advance(initial, 1) { Vector3.ZERO } }
            assertTrue(thread.isInterrupted)
        } finally {
            Thread.interrupted()
        }
        var calls = 0
        try {
            assertFailsWith<InterruptedException> {
                OrbitalEvolution.advance(initial, 100) {
                    if (++calls == 3) thread.interrupt()
                    Vector3.ZERO
                }
            }
            assertTrue(thread.isInterrupted)
            assertEquals(3, calls)
        } finally {
            Thread.interrupted()
        }
    }
}
