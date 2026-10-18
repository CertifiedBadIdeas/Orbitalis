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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ManeuverPlanTest {
    private val state = CartesianState(Vector3(0.0, 8000.0, 0.0), Vector3(150.0, 0.0, 0.0))
    private val field = FlatPlanetDefinition.gravity(11.0)

    @Test fun `orbital axes preserve sign magnitude and orthogonality`() {
        assertEquals(Vector3(10.0, 30.0, -20.0), ManeuverNode(10.0, 10.0, 20.0, 30.0).deltaVelocity(state))
        val tilted = CartesianState(state.position, Vector3(150.0, 30.0, 0.0))
        val delta = ManeuverNode(10.0, 10.0, 20.0, 30.0).deltaVelocity(tilted)
        assertEquals(kotlin.math.sqrt(1400.0), delta.length(), 1e-9)
        assertFailsWith<IllegalArgumentException> {
            ManeuverNode(1.0, 1.0, 0.0, 0.0).deltaVelocity(CartesianState(state.position, Vector3(0.0, 10.0, 0.0)))
        }
    }

    @Test fun `preview applies nodes at exact epochs in sequence and preserves continuity`() {
        val plan = ManeuverPlan(100, listOf(ManeuverNode(10.0, 15.0, 0.0, 0.0), ManeuverNode(20.0, 0.0, 3.0, 0.0)))
        val result = plan.predict(field, state, 120)
        assertEquals(listOf(9.0, 19.0), result.burns.map { it.seconds })
        assertEquals(3, result.segments.size)
        for ((i, burn) in result.burns.withIndex()) {
            assertEquals(
                burn.state,
                result.segments[i]
                    .coast.terminal.state,
            )
            val after =
                result.segments[i + 1]
                    .coast.points
                    .first()
                    .state
            assertEquals(burn.state.position, after.position)
            assertTrue((burn.deltaVelocity - (after.velocity - burn.state.velocity)).length() < 1e-10)
        }
        assertTrue(
            (
                result.segments
                    .last()
                    .coast.terminal.state.position -
                    ManeuverPlan(100, emptyList())
                        .predict(field, state, 120)
                        .segments
                        .single()
                        .coast.terminal.state.position
            ).length() >
                10.0,
        )
    }

    @Test fun `past nodes are not reapplied and impact prevents later impulses`() {
        assertTrue(ManeuverPlan(0, listOf(ManeuverNode(1.0, 1.0, 0.0, 0.0))).predict(field, state, 40).burns.isEmpty())
        val fall = CartesianState(Vector3(0.0, 70.0, 0.0), Vector3(1.0, -10.0, 0.0))
        val result = ManeuverPlan(0, listOf(ManeuverNode(30.0, 10.0, 0.0, 0.0))).predict(field, fall, 0)
        assertTrue(result.burns.isEmpty())
        assertEquals(
            CoastPrediction.End.BODY_INTERSECTION,
            result.segments
                .last()
                .coast.end,
        )
    }

    @Test fun `plans bound work and reject malformed nodes`() {
        assertFailsWith<IllegalArgumentException> { ManeuverNode(Double.NaN, 0.0, 0.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { ManeuverNode(1.0, 400.0, 400.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { ManeuverPlan(0, List(9) { ManeuverNode(it + 1.0, 0.0, 0.0, 0.0) }) }
        assertFailsWith<IllegalArgumentException> {
            ManeuverPlan(
                0,
                listOf(ManeuverNode(2.0, 0.0, 0.0, 0.0), ManeuverNode(1.0, 0.0, 0.0, 0.0)),
            )
        }
    }

    @Test fun `sub tick nodes retain swept collision and initial contact rejects immediate burn`() {
        val plan = ManeuverPlan(0, listOf(ManeuverNode(0.06, 100.0, 0.0, 0.0)))
        val impact = plan.predict(field, CartesianState(Vector3(0.0, 64.05, 0.0), Vector3(1.0, -20.0, 0.0)), 1)
        assertTrue(impact.burns.isEmpty())
        assertEquals(
            CoastPrediction.End.BODY_INTERSECTION,
            impact.segments
                .single()
                .coast.end,
        )
        val immediate = plan.copy(nodes = listOf(ManeuverNode(0.05, 100.0, 0.0, 0.0)))
        val inside = immediate.predict(field, CartesianState(Vector3.ZERO, Vector3(1.0, 0.0, 0.0)), 1)
        assertTrue(inside.burns.isEmpty())
    }
}
