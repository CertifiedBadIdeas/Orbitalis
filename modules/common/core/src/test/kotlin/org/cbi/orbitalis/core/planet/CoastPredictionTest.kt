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
        val falling = CoastPrediction.predict(field, CartesianState(Vector3(10.0, 700.0, 20.0), Vector3(1.0, -10.0, 0.0)))
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, falling.end)
        assertEquals(464.0, falling.terminal.state.position.y, 1e-8)
        val lateral = CoastPrediction.predict(field, CartesianState(Vector3(1001.0, 400.0, 0.0), Vector3(-20.0, -1.0, 0.0)))
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, lateral.end)
        assertTrue(lateral.terminal.seconds > 0.0)
        val rising =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(0.0, 400.0, 0.0), Vector3(0.0, 100.0, 0.0)),
                CoastPrediction.Limits(horizon = 0.1),
            )
        assertEquals(CoastPrediction.End.HORIZON, rising.end)
    }

    @Test
    fun `surface forecast arms only after ascent and handles open corners`() {
        val low = CartesianState(Vector3(0.0, 200.0, 0.0), Vector3.ZERO)
        val result = CoastPrediction.predict(field, low, CoastPrediction.Limits(entryInitiallyArmed = false))
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, result.end)
        val launched =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(0.0, 400.0, 0.0), Vector3(0.0, 90.0, 0.0)),
                CoastPrediction.Limits(entryInitiallyArmed = false),
            )
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, launched.end)
        assertTrue(launched.terminal.seconds > 1.0)
        val corner = CoastPrediction.predict(field, CartesianState(Vector3(1001.0, 400.0, 1001.0), Vector3(-20.0, -1.0, -20.0)))
        assertEquals(CoastPrediction.End.SURFACE_ENTRY, corner.end)
        val tangent =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(1000.0, 300.0, 0.0), Vector3(0.0, -1.0, 0.0)),
                CoastPrediction.Limits(horizon = 0.1),
            )
        assertEquals(CoastPrediction.End.HORIZON, tangent.end)
    }

    @Test
    fun `inward motion from exact open border emits one time zero terminal sample`() {
        val result =
            CoastPrediction.predict(
                field,
                CartesianState(Vector3(1000.0, 300.0, 0.0), Vector3(-1.0, -1.0, 0.0)),
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
