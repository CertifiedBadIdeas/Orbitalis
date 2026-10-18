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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimedOrbitalEvolutionTest {
    private val initial = CartesianState(Vector3(100.0, 20.0, -30.0), Vector3(4.0, -2.0, 1.0))

    @Test
    fun `absolute endpoint times give exact velocity for linearly changing uniform acceleration`() {
        val epoch = 800L
        val ticks = 200L
        val start = epoch / 20.0
        val duration = ticks / 20.0
        val rate = Vector3(0.1, -0.2, 0.3)
        val base = Vector3(1.0, 2.0, -1.0)
        val sampled = mutableListOf<Long>()
        val result =
            OrbitalEvolution.advanceTimed(initial, epoch, ticks) { _, tick ->
                sampled.add(tick)
                base + rate * (tick / 20.0)
            }
        val startForce = base + rate * start
        val expectedVelocity = initial.velocity + startForce * duration + rate * (duration * duration * 0.5)
        assertVector(expectedVelocity, result.velocity, 1e-10)
        // Velocity Verlet omits rate * dt^3 / 6 in each drift for an affine time force.
        val expectedPosition =
            initial.position + initial.velocity * duration + startForce * (duration * duration * 0.5) +
                rate * ((duration * duration * duration - duration * 0.05 * 0.05) / 6.0)
        assertVector(expectedPosition, result.position, 1e-9)
        assertEquals((epoch..epoch + ticks).toList(), sampled)
    }

    @Test
    fun `moving field agrees with independently solved translated harmonic motion`() {
        val epoch = 12000L
        val duration = 20.0
        val frequency = 0.2
        val centre = Vector3(1000.0, -200.0, 300.0)
        val drift = Vector3(20.0, 3.0, -5.0)
        val relativePosition = Vector3(40.0, 10.0, -20.0)
        val relativeVelocity = Vector3(1.0, -2.0, 3.0)
        val start = CartesianState(centre + relativePosition, drift + relativeVelocity)
        val result =
            OrbitalEvolution.advanceTimed(start, epoch, 400) { position, tick ->
                val source = centre + drift * ((tick - epoch) / 20.0)
                (source - position) * (frequency * frequency)
            }
        val phase = frequency * duration
        val expectedPosition = centre + drift * duration + relativePosition * cos(phase) + relativeVelocity * (sin(phase) / frequency)
        val expectedVelocity = drift - relativePosition * (frequency * sin(phase)) + relativeVelocity * cos(phase)
        assertVector(expectedPosition, result.position, 0.002)
        assertVector(expectedVelocity, result.velocity, 0.0003)
    }

    @Test
    fun `segmented moving field evolution preserves absolute source phase`() {
        val epoch = 6000L
        val field: (Vector3, Long) -> Vector3 = { position, tick ->
            val phase = tick / 200.0
            val centre = Vector3(1000.0 * cos(phase), 0.0, 1000.0 * sin(phase))
            (centre - position) * 0.001
        }
        val whole = OrbitalEvolution.advanceTimed(initial, epoch, 1200, field)
        var segmented = initial
        var nextTick = epoch
        for (count in listOf(1L, 19L, 400L, 780L)) {
            segmented = OrbitalEvolution.advanceTimed(segmented, nextTick, count, field)
            nextTick += count
        }
        assertEquals(whole, segmented)
    }

    @Test
    fun `time bounds reject overflow and zero duration samples no sources`() {
        assertSame(initial, OrbitalEvolution.advanceTimed(initial, Long.MAX_VALUE, 0) { _, _ -> error("No sampling") })
        assertSame(initial, OrbitalEvolution.advanceTimed(initial, 0, 0) { _, _ -> error("No sampling") })
        for ((epoch, ticks) in listOf(-1L to 1L, 0L to -1L, 0L to 1201L, Long.MAX_VALUE to 1L, Long.MAX_VALUE - 1 to 2L)) {
            assertFailsWith<IllegalArgumentException> {
                OrbitalEvolution.advanceTimed(initial, epoch, ticks) { _, _ -> error("Invalid time must fail first") }
            }
        }
        val result = OrbitalEvolution.advanceTimed(initial, Long.MAX_VALUE - 1, 1) { _, _ -> Vector3.ZERO }
        assertVector(initial.position + initial.velocity * 0.05, result.position, 1e-12)
    }

    @Test
    fun `moving field interruption does not publish a result or clear cancellation`() {
        val thread = Thread.currentThread()
        try {
            assertFailsWith<InterruptedException> {
                OrbitalEvolution.advanceTimed(initial, 500, 100) { _, tick ->
                    if (tick == 502L) thread.interrupt()
                    Vector3.ZERO
                }
            }
            assertTrue(thread.isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private fun assertVector(
        expected: Vector3,
        actual: Vector3,
        tolerance: Double,
    ) {
        assertTrue((expected - actual).length() < tolerance, "Expected $expected, got $actual")
    }
}
