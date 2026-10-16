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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GliderFlightTest {
    @Test
    fun liftSlowsDescentAndNoseUpTradesSpeedForHeight() {
        val velocity = Vector3(0.0, -0.1, 0.6)
        val gravity = Vector3(0.0, -9.81 / 400, 0.0)
        val level = GliderFlight.step(velocity, 0.0, 0.0, gravity)
        val climb = GliderFlight.step(velocity, 0.0, -30.0, gravity)
        assertTrue(level.y > velocity.y + gravity.y && level.z > 0.0)
        assertTrue(climb.y > level.y && climb.z < level.z)
        val turn = GliderFlight.step(velocity, 90.0, 0.0, gravity)
        assertTrue(turn.x < 0.0 && turn.z > 0.0) // Steering changes velocity progressively.
        val coast = GliderFlight.step(Vector3(0.0, 0.0, 0.6), 0.0, 0.0, Vector3.ZERO)
        assertTrue(coast.y == 0.0 && coast.z in 0.0..0.6)
    }

    @Test
    fun verticalAndInvalidViewsAreBounded() {
        for (pitch in listOf(-90.0, 90.0)) {
            val step = GliderFlight.step(Vector3(0.0, -1.0, 0.0), 0.0, pitch, Vector3(0.0, -0.02, 0.0))
            assertTrue(step.length() < 2.0)
        }
        assertFailsWith<IllegalArgumentException> { GliderFlight.step(Vector3.ZERO, 0.0, 91.0, Vector3.ZERO) }
    }
}
