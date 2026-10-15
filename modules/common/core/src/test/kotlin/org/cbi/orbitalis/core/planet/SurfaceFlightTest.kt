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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SurfaceFlightTest {
    @Test
    fun `bounded controller completes a flight with an explicit point contact fixture`() {
        for (itinerary in SurfaceFlight.Itinerary.entries) {
            for (dt in listOf(0.05, 0.0125)) {
                val flight = SurfaceFlight(itinerary)
                var state = CartesianState(SurfaceFlight.LAUNCH, Vector3.ZERO)
                val phases = mutableSetOf<SurfaceFlight.Phase>()
                repeat((80.0 / dt).toInt()) {
                    val thrust = flight.command(state, dt)
                    phases.add(flight.phase)
                    assertTrue(thrust.length() <= SurfaceFlight.MAX_THRUST + 1e-10)
                    if (flight.phase in setOf(SurfaceFlight.Phase.COAST, SurfaceFlight.Phase.TOUCHDOWN, SurfaceFlight.Phase.LANDED)) {
                        assertEquals(Vector3.ZERO, thrust)
                    }
                    // A simple contact fixture is domain evidence only, never evidence of Sable collisions.
                    val velocity = state.velocity + (flight.gravity.acceleration(state.position) + thrust) * dt
                    state = CartesianState(state.position + velocity * dt, velocity)
                    if (state.position.y < 9.5 && state.position.y > 8.5 &&
                        state.position.x * state.position.x + state.position.z * state.position.z < 1.0
                    ) {
                        state =
                            state.copy(
                                position = Vector3(state.position.x, 9.5, state.position.z),
                                velocity = Vector3(state.velocity.x, 0.0, state.velocity.z),
                            )
                    }
                    assertTrue(flight.phase != SurfaceFlight.Phase.FAILED, "dt=$dt state=$state phases=$phases")
                }
                val expected =
                    if (itinerary == SurfaceFlight.Itinerary.ORBIT) {
                        SurfaceFlight.Phase.entries.filter { it !in setOf(SurfaceFlight.Phase.FAILED, SurfaceFlight.Phase.HOVER) }
                    } else {
                        listOf(
                            SurfaceFlight.Phase.GROUND,
                            SurfaceFlight.Phase.ASCENT,
                            SurfaceFlight.Phase.HOVER,
                            SurfaceFlight.Phase.DESCENT,
                            SurfaceFlight.Phase.TOUCHDOWN,
                            SurfaceFlight.Phase.LANDED,
                        )
                    }
                assertEquals(expected.toSet(), phases)
                assertEquals(SurfaceFlight.Phase.LANDED, flight.phase)
                assertTrue(if (itinerary == SurfaceFlight.Itinerary.ORBIT) flight.coastAngle >= 2.0 * PI else flight.coastAngle == 0.0)
                assertTrue(flight.maximumCoastEnergyError < 0.01)
            }
        }
    }

    @Test
    fun `missing ground support fails rather than declaring a landing`() {
        val flight = SurfaceFlight()
        flight.command(CartesianState(Vector3(0.0, 9.0, 0.0), Vector3.ZERO), 0.05)
        assertEquals(SurfaceFlight.Phase.FAILED, flight.phase)
        assertEquals(Vector3.ZERO, flight.thrust)
        assertEquals(Vector3.ZERO, flight.command(CartesianState(Vector3.ZERO, Vector3.ZERO), 0.05))
        assertFailsWith<IllegalArgumentException> { flight.command(CartesianState(SurfaceFlight.LAUNCH, Vector3.ZERO), 0.0) }
    }
}
