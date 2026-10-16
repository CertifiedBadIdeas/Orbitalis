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
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class OrbitalGuidanceTest {
    private val zero = Vector3.ZERO
    private val normal = Vector3(0.0, 0.0, -1.0)
    private val home = Vector3(0.0, 1.0, 0.0)
    private val mu = 353160000.0
    private val radius = OrbitalGuidance.RADIUS
    private val speed = sqrt(mu / radius)

    @Test fun circularGuidanceRequiresNoFictitiousThrust() {
        val state = CartesianState(home * radius, Vector3(speed, 0.0, 0.0))
        val requested = OrbitalGuidance.orbit(state, zero, normal, radius, speed)
        assertTrue((requested - home * (-mu / (radius * radius))).length() < 1e-12)
        assertEquals(0.02, OrbitalGuidance.angleStep(PI - 0.01, -PI + 0.01), 1e-12)
    }

    @Test fun insertionThenUnpoweredRevolutionKeepsEnergyAndRadius() {
        var p = home * radius
        var v = zero
        repeat(12000) {
            val acceleration = OrbitalGuidance.orbit(CartesianState(p, v), zero, normal, radius, speed)
            v += acceleration * 0.05
            p += v * 0.05
        }
        // A single 20 Hz symplectic step has a small radial bias; insertion allows 5 m.
        assertEquals(radius, p.length(), 1.0)
        assertEquals(speed, v.length(), 0.1)
        val energy = v.dot(v) / 2 - mu / p.length()
        repeat(17000) {
            v += p * (-mu / p.length().pow(3) * 0.05)
            p += v * 0.05
        }
        assertEquals(radius, p.length(), 6.0)
        assertEquals(energy, v.dot(v) / 2 - mu / p.length(), 0.1)
    }

    @Test fun returnBrakesAlongOrbitToHomeRatherThanCuttingThroughPlanet() {
        var p = Vector3(radius, 0.0, 0.0)
        var v = Vector3(0.0, -speed, 0.0)
        var previous = PI / 2
        var swept = previous
        val target = 2 * PI
        var minRadius = radius
        repeat(20000) {
            val state = CartesianState(p, v)
            val observed = OrbitalGuidance.observe(state, zero, normal)
            val angle = OrbitalGuidance.angle(observed.radial, home, normal)
            swept += OrbitalGuidance.angleStep(previous, angle)
            previous = angle
            val desiredSpeed = OrbitalGuidance.returnSpeed((target - swept) * radius, speed)
            v += OrbitalGuidance.orbit(state, zero, normal, radius, desiredSpeed) * 0.05
            p += v * 0.05
            minRadius = min(minRadius, p.length())
        }
        assertTrue(minRadius > radius - 10)
        assertTrue((p - home * radius).length() < 0.5, "Missed remembered home radial: $p")
        assertTrue(v.length() < 0.1)
    }

    @Test fun descentStopsAtLaunchPoint() {
        var p = Vector3(15.0, 1000.0, -10.0)
        var v = Vector3(0.0, -60.0, 0.0)
        val target = Vector3(15.0, 70.0, -10.0)
        repeat(12000) {
            val acceleration = OrbitalGuidance.point(CartesianState(p, v), target, 80.0)
            v += acceleration * 0.05
            p += v * 0.05
            assertTrue(p.y >= target.y - 0.1, "Descent crossed landing plane")
        }
        assertTrue((p - target).length() < 0.01 && v.length() < 0.01)
    }
}
