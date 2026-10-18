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

package org.cbi.orbitalis.consumer.flight

import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManeuverBurnTest {
    @Test fun gravityOnlyCoastDoesNotSpendPlannedDeltaV() {
        val burn = ManeuverBurn(Vector3(10.0, 5.0, -2.0))
        val a = Vector3(0.0, -3.0, 0.0)
        burn.observe(Vector3(20.0, 2.0, 0.0), Vector3(20.0, 1.85, 0.0), a, a, 0.05)
        assertTrue(burn.delivered.length() < 1e-10)
    }

    @Test fun measuredForceFeedbackConvergesUnderThrustLagAndClampsAuthority() {
        val target = Vector3(12.0, -5.0, 3.0)
        val burn = ManeuverBurn(target)
        var velocity = Vector3(100.0, 0.0, 0.0)
        var force = Vector3.ZERO
        val gravity = Vector3(0.0, -2.0, 0.0)
        repeat(600) {
            val requested = burn.thrust()
            assertTrue(requested.length() <= 3.000000001)
            force += (requested - force) * 0.15
            val next = velocity + (force + gravity) * 0.05
            burn.observe(velocity, next, gravity, gravity, 0.05)
            velocity = next
        }
        assertTrue(burn.remaining.length() < 0.001)
        assertTrue((burn.delivered - target).length() < 0.001)
    }
}
