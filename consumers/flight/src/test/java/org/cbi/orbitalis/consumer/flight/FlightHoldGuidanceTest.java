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

package org.cbi.orbitalis.consumer.flight;

import org.cbi.orbitalis.core.planet.CartesianState;
import org.cbi.orbitalis.core.planet.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class FlightHoldGuidanceTest {
    private static final Vector3 ZERO = Vector3.Companion.getZERO();

    @Test void gravityCompensationAndBoundedBraking() {
        var gravity = new Vector3(-1, -9, 0);
        var p = new Vector3(3, 6000, 2);
        var hold = FlightHoldGuidance.command(new CartesianState(p, ZERO), p, gravity, ZERO, ZERO);
        assertTrue(hold.thrustAcceleration().plus(gravity).length() < 1e-12);
        var fast = FlightHoldGuidance.command(new CartesianState(p, new Vector3(100, 20, 0)), p,
            gravity, new Vector3(3, 0, 0), new Vector3(10, 0, 0));
        assertEquals(4, fast.thrustAcceleration().plus(gravity).length(), 1e-12);
        assertTrue(fast.angularAcceleration().getX() < 0 && fast.angularAcceleration().length() <= 0.8);
    }

    @Test void reachesElevatedTargetUnderGravity() {
        var p = new Vector3(1200, 200, 0);
        var v = new Vector3(4, -1, 2);
        var target = new Vector3(1200, 210, 0);
        var gravity = new Vector3(0, -9.4, 0);
        for (int i = 0; i < 2400; i++) {
            var demand = FlightHoldGuidance.command(new CartesianState(p, v), target, gravity, ZERO, ZERO);
            v = v.plus(demand.thrustAcceleration().plus(gravity).times(0.01));
            p = p.plus(v.times(0.01));
        }
        assertTrue(p.minus(target).length() < 0.01 && v.length() < 0.01);
    }

    @Test void balancesOffCentreEnginesAndReportsUnavailableAuthority() {
        var up = new Vector3(0, 100, 0);
        var engines = List.of(new Vector3(-2, 0, 0), new Vector3(2, 0, 0)).stream()
            .map(arm -> new ThrusterAllocation.Actuator(up, arm.cross(up))).toList();
        var hover = ThrusterAllocation.solve(engines, new Vector3(0, 100, 0), ZERO, 2);
        assertTrue(hover.forceResidual().length() < 1e-5 && hover.torqueResidual().length() < 1e-5);
        hover.throttle().forEach(t -> assertEquals(0.5, t, 1e-6));
        var overloaded = ThrusterAllocation.solve(engines, new Vector3(0, 300, 0), ZERO, 2);
        assertEquals(List.of(1.0, 1.0), overloaded.throttle());
        assertEquals(100, overloaded.forceResidual().getY(), 1e-8);
        var unavailable = ThrusterAllocation.solve(engines, new Vector3(100, 0, 0), ZERO, 2);
        assertEquals(100, unavailable.forceResidual().getX());
        assertTrue(unavailable.throttle().stream().allMatch(t -> t >= 0 && t <= 1));
    }

    @Test void torqueCoupleDoesNotIntroduceTranslation() {
        var engines = List.of(
            new ThrusterAllocation.Actuator(new Vector3(0, 100, 0), new Vector3(0, 0, 200)),
            new ThrusterAllocation.Actuator(new Vector3(0, -100, 0), new Vector3(0, 0, 200)));
        var result = ThrusterAllocation.solve(engines, ZERO, new Vector3(0, 0, 200), 2);
        assertTrue(result.forceResidual().length() < 1e-5 && result.torqueResidual().length() < 1e-5);
        result.throttle().forEach(t -> assertEquals(0.5, t, 1e-6));
    }
}
