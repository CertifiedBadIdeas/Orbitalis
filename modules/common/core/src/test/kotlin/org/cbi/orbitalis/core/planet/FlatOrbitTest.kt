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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class FlatOrbitTest {
    private val body = RectangularGravity(1000.0, 64.0, 1000.0, 11.0)
    private val field = SurfaceGravityLayer(body, 11.0)

    // Numerically fitted seed, not sqrt(mu/r): the near field is not central.
    private val launch by lazy { BallisticOrbit.plan(field).initial }

    private fun step(
        state: CartesianState,
        dt: Double,
    ): CartesianState {
        val half = state.velocity + field.acceleration(state.position) * (dt * 0.5)
        val p = state.position + half * dt
        return CartesianState(p, half + field.acceleration(p) * (dt * 0.5))
    }

    private data class Result(
        val energyError: Double,
        val turns: Double,
        val clearance: Double,
        val maxCrossPlane: Double,
    )

    private fun coast(dt: Double): Result {
        var state = launch.copy(position = launch.position + Vector3(0.0, 0.0, 0.1))
        val reference = field.specificEnergy(state)
        assertTrue(reference < 0.0)
        var energyError = 0.0
        var clearance = Double.POSITIVE_INFINITY
        var angle = 0.0
        var previous = atan2(state.position.x, state.position.y)
        var crossPlane = 0.0
        repeat((500.0 / dt).toInt()) {
            state = step(state, dt)
            assertTrue(!body.contains(state.position), "Ballistic trajectory collided with the body")
            val p = state.position
            val dx = max(0.0, abs(p.x) - body.halfWidth)
            val dy = max(0.0, abs(p.y) - body.halfHeight)
            val dz = max(0.0, abs(p.z) - body.halfDepth)
            clearance = minOf(clearance, sqrt(dx * dx + dy * dy + dz * dz))
            val next = atan2(p.x, p.y)
            var advance = next - previous
            if (advance < -PI) advance += 2 * PI
            if (advance > PI) advance -= 2 * PI
            angle += advance
            previous = next
            energyError = max(energyError, abs((field.specificEnergy(state) - reference) / reference))
            crossPlane = max(crossPlane, abs(p.z))
        }
        return Result(energyError, angle / (2 * PI), clearance, crossPlane)
    }

    @Test
    fun `unpowered low orbit survives six revolutions with clearance and bounded energy error`() {
        val coarse = coast(0.02)
        val fine = coast(0.01)
        assertTrue(fine.turns > 6.0)
        assertTrue(fine.clearance > 50.0)
        assertTrue(fine.maxCrossPlane < 0.11)
        assertTrue(fine.energyError < 4e-5)
        assertTrue(coarse.energyError / fine.energyError in 3.5..4.5, "Energy error must converge with the second order timestep")
        println("ORBITALIS_FLAT_CORE turns=${fine.turns} clearance=${fine.clearance} energyError=${fine.energyError}")
    }

    @Test
    fun `numerical planner fits both axial and diagonal low meridians`() {
        for (diagonal in listOf(false, true)) {
            val orbit = BallisticOrbit.plan(field, diagonal = diagonal)
            assertTrue(orbit.period in 60.0..120.0)
            assertTrue(orbit.minimumClearance > 40.0)
            assertTrue((orbit.stateAt(orbit.period).position - orbit.initial.position).length() < 1e-9)
            val middle = orbit.stateAt(orbit.period * 0.5)
            assertTrue(middle.position.y < -800.0 && middle.position.x < 0.1)
            assertTrue(abs(middle.velocity.y) < 0.1)
            println(
                "ORBITALIS_FLAT_PLAN diagonal=$diagonal speed=${orbit.initial.velocity.length()} period=${orbit.period} clearance=${orbit.minimumClearance}",
            )
        }
    }

    @Test
    fun `negative energy alone does not mean a safe orbit`() {
        var state = launch.copy(velocity = Vector3(50.0, 0.0, 0.0))
        assertTrue(field.specificEnergy(state) < 0.0)
        var collision = false
        repeat(4000) {
            if (!collision) {
                state = step(state, 0.01)
                collision = body.contains(state.position)
            }
        }
        assertTrue(collision, "An unsafe bound trajectory must not be advertised as an orbit")
    }
}
