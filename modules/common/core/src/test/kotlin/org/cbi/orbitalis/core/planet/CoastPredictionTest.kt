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

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CoastPredictionTest {
    private val field = SurfaceGravityLayer(RectangularGravity(1000.0, 64.0, 1000.0, 11.0), 11.0)

    @Test
    fun `native region fall matches analytic impact and hull clearance`() {
        val initial = CartesianState(Vector3(0.0, 200.0, 0.0), Vector3.ZERO)
        val result = CoastPrediction.predict(field, initial, CoastPrediction.Limits(detectEntry = false, hullRadius = 4.0))
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, result.end)
        assertEquals(sqrt(2.0 * (200.0 - 68.0) / 11.0), result.terminal.seconds, 0.001)
        assertEquals(68.0, result.terminal.state.position.y, 1e-8)
    }

    @Test
    fun `entry plane detects descent and lateral arrivals but excludes outward and border tangency`() {
        val falling =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(10.0, 700.0, 20.0), Vector3(1.0, -10.0, 0.0)),
                CoastPrediction.Limits(detectEntry = true),
            )
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, falling.end)
        assertEquals(464.0, falling.terminal.state.position.y, 1e-8)
        val lateral =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(1001.0, 400.0, 0.0), Vector3(-20.0, -1.0, 0.0)),
                CoastPrediction.Limits(detectEntry = true),
            )
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, lateral.end)
        assertTrue(lateral.terminal.seconds > 0.0)
        val rising =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(0.0, 400.0, 0.0), Vector3(0.0, 100.0, 0.0)),
                CoastPrediction.Limits(horizon = 0.1, detectEntry = true),
            )
        assertEquals(CoastPrediction.End.HORIZON, rising.end)
    }

    @Test
    fun `surface forecast arms only after ascent and handles open corners`() {
        val low = CartesianState(Vector3(0.0, 200.0, 0.0), Vector3.ZERO)
        val result = CoastPrediction.predict(field, low, CoastPrediction.Limits(detectEntry = true, entryInitiallyArmed = false))
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, result.end)
        val launched =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(0.0, 400.0, 0.0), Vector3(0.0, 90.0, 0.0)),
                CoastPrediction.Limits(detectEntry = true, entryInitiallyArmed = false),
            )
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, launched.end)
        assertTrue(launched.terminal.seconds > 1.0)
        val corner =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(1001.0, 400.0, 1001.0), Vector3(-20.0, -1.0, -20.0)),
                CoastPrediction.Limits(detectEntry = true),
            )
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, corner.end)
        val tangent =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(1000.0, 300.0, 0.0), Vector3(0.0, -1.0, 0.0)),
                CoastPrediction.Limits(horizon = 0.1, detectEntry = true),
            )
        assertEquals(CoastPrediction.End.HORIZON, tangent.end)
    }

    @Test
    fun `inward motion from exact open border emits one time zero terminal sample`() {
        val result =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(1000.0, 300.0, 0.0), Vector3(-1.0, -1.0, 0.0)),
                CoastPrediction.Limits(detectEntry = true),
            )
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, result.end)
        assertEquals(1, result.points.size)
        assertEquals(0.0, result.terminal.seconds)
    }

    @Test
    fun `fast motion cannot tunnel through finite body between ticks`() {
        val result =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(0.0, 100.0, 0.0), Vector3(0.0, -10000.0, 0.0)),
                CoastPrediction.Limits(detectEntry = false),
            )
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, result.end)
        assertTrue(result.terminal.seconds < 0.05)
        assertEquals(64.0, result.terminal.state.position.y, 1e-8)
    }

    @Test
    fun `free orbit remains bounded conserves energy and converges on finer step`() {
        val initial = BallisticOrbit.plan(field).initial
        val coarse = CoastPrediction.predict(field, initial, CoastPrediction.Limits(horizon = 80.0, detectEntry = false))
        val fine = CoastPrediction.predict(field, initial, CoastPrediction.Limits(horizon = 80.0, step = 0.025, detectEntry = false))
        assertEquals(CoastPrediction.End.HORIZON, coarse.end)
        assertTrue((coarse.terminal.state.position - fine.terminal.state.position).length() < 0.5)
        val energy = field.specificEnergy(initial)
        assertTrue(coarse.points.all { abs((field.specificEnergy(it.state) - energy) / energy) < 0.001 })
        assertTrue(coarse.points.size <= 162)
    }

    @Test
    fun `default coast follows a suborbital arc past the dimension boundary to the surface`() {
        val native = SurfaceGravityLayer(field.body, 11.0, nearHeight = 600.0, fullHeight = 1000.0)
        val initial = CartesianState(Vector3(-150.0, 200.0, 0.0), Vector3(10.0, 80.0, 0.0))
        val result = CoastPrediction.predict(native, initial)
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, result.end)
        assertTrue(result.points.maxOf { it.state.position.y } > 464.0)
        val impactTime = (80.0 + sqrt(80.0 * 80.0 + 2.0 * 11.0 * 136.0)) / 11.0
        assertEquals(impactTime, result.terminal.seconds, 0.001)
        assertEquals(initial.position.x + 10.0 * impactTime, result.terminal.state.position.x, 0.01)
        assertEquals(64.0, result.terminal.state.position.y, 1e-8)
        // Starting again from a later state changes the remaining forecast, not the underlying arc.
        val later = result.points.first { it.seconds > 5.0 }
        val revised = CoastPrediction.predict(native, later.state)
        assertEquals(result.terminal.seconds - later.seconds, revised.terminal.seconds, 0.002)
        assertTrue((result.terminal.state.position - revised.terminal.state.position).length() < 0.02)
        val changed = CoastPrediction.predict(native, later.state.copy(velocity = later.state.velocity + Vector3(5.0, 0.0, 0.0)))
        assertTrue((changed.terminal.state.position - revised.terminal.state.position).length() > 10.0)
    }

    @Test
    fun `default forecast completes a periodic orbit without forcing a geometric ellipse`() {
        val orbit = BallisticOrbit.plan(field, 900.0)
        val result = CoastPrediction.predict(field, orbit.initial, CoastPrediction.Limits(horizon = orbit.period))
        assertEquals(CoastPrediction.End.HORIZON, result.end)
        assertTrue(result.points.any { it.state.position.y < -64.0 })
        assertTrue((result.terminal.state.position - orbit.initial.position).length() < 0.6)
        assertTrue((result.terminal.state.velocity - orbit.initial.velocity).length() < 0.1)
    }

    @Test
    fun `scaled planet forecast completes a full orbit beyond the old half turn horizon`() {
        val scaled = FlatPlanetDefinition.gravity(11.0)
        val orbit = BallisticOrbit.plan(scaled, FlatPlanetDefinition.ORBIT_HEIGHT)
        val result = CoastPrediction.orbit(scaled, orbit.initial)
        assertEquals(CoastPrediction.End.ORBIT, result.end)
        assertTrue(result.terminal.seconds > 120.0)
        assertTrue(abs(result.terminal.seconds - orbit.period) < 1.0)
        assertTrue(result.points.any { it.state.position.y < -64.0 })
        assertTrue((result.terminal.state.position - orbit.initial.position).length() < 2.0)
        assertTrue(result.points.size <= 602)
        val later = result.points[result.points.size / 3].state
        val revised = CoastPrediction.orbit(scaled, later)
        assertEquals(CoastPrediction.End.ORBIT, revised.end)
        assertTrue((revised.terminal.state.position - later.position).length() < 2.0)
    }

    @Test
    fun `complete orbit forecast still stops at impact and leaves escaping paths open`() {
        val falling = CoastPrediction.orbit(field, CartesianState(Vector3(0.0, 700.0, 0.0), Vector3(10.0, -1.0, 0.0)))
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, falling.end)
        val escaping = CoastPrediction.orbit(field, CartesianState(Vector3(0.0, 2000.0, 0.0), Vector3(500.0, 50.0, 0.0)))
        assertTrue(escaping.end in setOf(CoastPrediction.End.RANGE, CoastPrediction.End.HORIZON))
        assertTrue(escaping.points.size <= 602)
    }

    @Test
    fun `precessing turns remain open and long periods respect the budget`() {
        val orbit = BallisticOrbit.plan(field, 900.0)
        val precessing = CoastPrediction.orbit(field, orbit.initial.copy(velocity = orbit.initial.velocity * 1.05))
        assertEquals(CoastPrediction.End.REVOLUTION, precessing.end)
        val scaled = FlatPlanetDefinition.gravity(11.0)
        val distant = CartesianState(Vector3(0.0, 90000.0, 0.0), Vector3(sqrt(scaled.body.mu / 90000.0), 0.0, 0.0))
        val bounded = CoastPrediction.orbit(scaled, distant, CoastPrediction.Limits(range = 100000.0))
        assertEquals(CoastPrediction.End.HORIZON, bounded.end)
        assertEquals(3600.0, bounded.terminal.seconds)
        assertTrue(bounded.points.size <= 602)
    }

    @Test
    fun `range horizon and input budgets bound work without claiming escape`() {
        val result =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(0.0, 1000.0, 0.0), Vector3(0.0, 1000.0, 0.0)),
                CoastPrediction.Limits(range = 1500.0),
            )
        assertEquals(CoastPrediction.End.RANGE, result.end)
        assertFailsWith<IllegalArgumentException> { CoastPrediction.Limits(step = 1e-6) }
        assertFailsWith<IllegalArgumentException> { CoastPrediction.Limits(horizon = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { CoastPrediction.Limits(sampleInterval = 0.001) }
    }
}
