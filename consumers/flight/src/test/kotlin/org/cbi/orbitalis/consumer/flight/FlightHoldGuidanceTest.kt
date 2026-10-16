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

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlightHoldGuidanceTest {
    private val zero = Vector3.ZERO

    @Test fun gravityCompensationAndBoundedBraking() {
        val gravity = Vector3(-1.0, -9.0, 0.0)
        val p = Vector3(3.0, 6000.0, 2.0)
        val hold = FlightHoldGuidance.command(CartesianState(p, zero), p, gravity, zero, zero)
        assertTrue((hold.thrustAcceleration + gravity).length() < 1e-12)
        val fast =
            FlightHoldGuidance.command(
                CartesianState(p, Vector3(100.0, 20.0, 0.0)),
                p,
                gravity,
                Vector3(3.0, 0.0, 0.0),
                Vector3(10.0, 0.0, 0.0),
            )
        assertEquals(4.0, (fast.thrustAcceleration + gravity).length(), 1e-12)
        assertTrue(fast.angularAcceleration.x < 0 && fast.angularAcceleration.length() <= 0.8)
    }

    @Test fun reachesElevatedTargetUnderGravity() {
        var p = Vector3(1200.0, 200.0, 0.0)
        var v = Vector3(4.0, -1.0, 2.0)
        val target = Vector3(1200.0, 210.0, 0.0)
        val gravity = Vector3(0.0, -9.4, 0.0)
        repeat(2400) {
            val demand = FlightHoldGuidance.command(CartesianState(p, v), target, gravity, zero, zero)
            v += (demand.thrustAcceleration + gravity) * 0.01
            p += v * 0.01
        }
        assertTrue((p - target).length() < 0.01 && v.length() < 0.01)
    }

    @Test fun balancesOffCentreEnginesAndReportsUnavailableAuthority() {
        val up = Vector3(0.0, 100.0, 0.0)
        val engines =
            listOf(Vector3(-2.0, 0.0, 0.0), Vector3(2.0, 0.0, 0.0))
                .map { arm -> ThrusterAllocation.Actuator(up, arm.cross(up)) }
        val hover = ThrusterAllocation.solve(engines, up, zero, 2.0)
        assertTrue(hover.forceResidual.length() < 1e-5 && hover.torqueResidual.length() < 1e-5)
        hover.throttle.forEach { assertEquals(0.5, it, 1e-6) }
        val overloaded = ThrusterAllocation.solve(engines, Vector3(0.0, 300.0, 0.0), zero, 2.0)
        assertEquals(listOf(1.0, 1.0), overloaded.throttle)
        assertEquals(100.0, overloaded.forceResidual.y, 1e-8)
        val unavailable = ThrusterAllocation.solve(engines, Vector3(100.0, 0.0, 0.0), zero, 2.0)
        assertEquals(100.0, unavailable.forceResidual.x)
        assertTrue(unavailable.throttle.all { it in 0.0..1.0 })
    }

    @Test fun torqueCoupleDoesNotIntroduceTranslation() {
        val engines =
            listOf(
                ThrusterAllocation.Actuator(Vector3(0.0, 100.0, 0.0), Vector3(0.0, 0.0, 200.0)),
                ThrusterAllocation.Actuator(Vector3(0.0, -100.0, 0.0), Vector3(0.0, 0.0, 200.0)),
            )
        val result = ThrusterAllocation.solve(engines, zero, Vector3(0.0, 0.0, 200.0), 2.0)
        assertTrue(result.forceResidual.length() < 1e-5 && result.torqueResidual.length() < 1e-5)
        result.throttle.forEach { assertEquals(0.5, it, 1e-6) }
    }
}
