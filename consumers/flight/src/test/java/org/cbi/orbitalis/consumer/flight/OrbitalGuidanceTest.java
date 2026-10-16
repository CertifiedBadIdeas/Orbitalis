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

class OrbitalGuidanceTest {
    private static final Vector3 ZERO = Vector3.Companion.getZERO();
    private static final Vector3 NORMAL = new Vector3(0, 0, -1);
    private static final Vector3 HOME = new Vector3(0, 1, 0);
    private static final double MU = 353160000;
    private static final double R = OrbitalGuidance.RADIUS;
    private static final double SPEED = Math.sqrt(MU / R);

    @Test void circularGuidanceRequiresNoFictitiousThrust() {
        var state = new CartesianState(HOME.times(R), new Vector3(SPEED, 0, 0));
        var requested = OrbitalGuidance.orbit(state, ZERO, NORMAL, R, SPEED);
        assertTrue(requested.minus(HOME.times(-MU / (R * R))).length() < 1e-12);
        assertEquals(0.02, OrbitalGuidance.angleStep(Math.PI - 0.01, -Math.PI + 0.01), 1e-12);
    }

    @Test void insertionThenUnpoweredRevolutionKeepsEnergyAndRadius() {
        Vector3 p = HOME.times(R), v = ZERO;
        for (int i = 0; i < 12000; i++) {
            var acceleration = OrbitalGuidance.orbit(new CartesianState(p, v), ZERO, NORMAL, R, SPEED);
            v = v.plus(acceleration.times(0.05)); p = p.plus(v.times(0.05));
        }
        // A single 20 Hz symplectic step has a small radial bias; insertion allows 5 m.
        assertEquals(R, p.length(), 1.0);
        assertEquals(SPEED, v.length(), 0.1);
        double energy = v.dot(v) / 2 - MU / p.length();
        for (int i = 0; i < 17000; i++) {
            v = v.plus(p.times(-MU / Math.pow(p.length(), 3) * 0.05)); p = p.plus(v.times(0.05));
        }
        assertEquals(R, p.length(), 6);
        assertEquals(energy, v.dot(v) / 2 - MU / p.length(), 0.1);
    }

    @Test void returnBrakesAlongOrbitToHomeRatherThanCuttingThroughPlanet() {
        Vector3 p = new Vector3(R, 0, 0), v = new Vector3(0, -SPEED, 0);
        double previous = Math.PI / 2, swept = previous, target = 2 * Math.PI;
        double minRadius = R;
        for (int i = 0; i < 20000; i++) {
            var state = new CartesianState(p, v);
            var observed = OrbitalGuidance.observe(state, ZERO, NORMAL);
            double angle = OrbitalGuidance.angle(observed.radial(), HOME, NORMAL);
            swept += OrbitalGuidance.angleStep(previous, angle); previous = angle;
            double desiredSpeed = OrbitalGuidance.returnSpeed((target - swept) * R, SPEED);
            v = v.plus(OrbitalGuidance.orbit(state, ZERO, NORMAL, R, desiredSpeed).times(0.05));
            p = p.plus(v.times(0.05)); minRadius = Math.min(minRadius, p.length());
        }
        assertTrue(minRadius > R - 10);
        assertTrue(p.minus(HOME.times(R)).length() < 0.5, "Missed remembered home radial: " + p);
        assertTrue(v.length() < 0.1);
    }

    @Test void descentStopsAtLaunchPoint() {
        Vector3 p = new Vector3(15, 1000, -10), v = new Vector3(0, -60, 0), target = new Vector3(15, 70, -10);
        for (int i = 0; i < 12000; i++) {
            var acceleration = OrbitalGuidance.point(new CartesianState(p, v), target, 80);
            v = v.plus(acceleration.times(0.05)); p = p.plus(v.times(0.05));
            assertTrue(p.getY() >= target.getY() - 0.1, "Descent crossed landing plane");
        }
        assertTrue(p.minus(target).length() < 0.01 && v.length() < 0.01);
    }
}
