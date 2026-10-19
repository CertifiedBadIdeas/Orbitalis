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

package org.cbi.orbitalis.core.flight

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sqrt

class OrbitalGuidanceTest {
    @Test fun orbitProgressUnwrapsAcrossTheAngleBoundary() {
        assertEquals(0.02, OrbitalGuidance.angleStep(PI - 0.01, -PI + 0.01), 1e-12)
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

    @Test fun descentKeepsItsCruiseAndBrakesForThePadAcrossTheHandoffPlane() {
        val launch = Vector3(15.0, 70.0, -10.0)
        val far = CartesianState(launch + Vector3(0.0, 4000.0, 0.0), Vector3(0.0, -80.0, 0.0))
        assertTrue(OrbitalGuidance.descent(far, launch).length() < 1e-12)
        val crossing = CartesianState(Vector3(launch.x, 1000.0, launch.z), Vector3(0.0, -sqrt(3 * (930.0 + 0.08)), 0.0))
        val before = OrbitalGuidance.descent(crossing, launch)
        val after = OrbitalGuidance.descent(crossing.copy(position = crossing.position + Vector3(0.0, -0.01, 0.0)), launch)
        assertTrue(before.length() < 1e-10, "Boundary substituted a lower speed target")
        assertTrue((before - after).length() < 0.01, "Guidance jumps at the handoff altitude")
        val near = CartesianState(launch + Vector3(0.0, 30.0, 0.0), Vector3(0.0, -10.0, 0.0))
        assertTrue(OrbitalGuidance.descent(near, launch).y > 0, "Landing failed to brake near the pad")
    }
}
