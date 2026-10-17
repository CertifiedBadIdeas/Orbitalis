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

import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.RectangularGravity
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketOrbitCaptureTest {
    private val field = SurfaceGravityLayer(RectangularGravity(1000.0, 64.0, 1000.0, 11.0), 11.0)

    @Test fun acceptsAnActualNearbyOrbitWithoutChasingTheReferencePoint() {
        val orbit = BallisticOrbit.plan(field, 1000.0)
        val course = RocketCourse(orbit, Vector3(1.0, 0.0, 0.0))
        val actual = course.stateAt(orbit.period * 0.1)
        assertTrue(RocketOrbitCapture.safe(field, actual, course))
        assertTrue(RocketOrbitCapture.safe(field, CartesianState(actual.position + Vector3(2.0, 2.0, 0.0), actual.velocity), course))
    }

    @Test fun rejectsAFallingOrEscapingInsertion() {
        val orbit = BallisticOrbit.plan(field, 1000.0)
        val course = RocketCourse(orbit, Vector3(1.0, 0.0, 0.0))
        assertFalse(RocketOrbitCapture.safe(field, CartesianState(orbit.initial.position, Vector3.ZERO), course))
        assertFalse(RocketOrbitCapture.safe(field, CartesianState(orbit.initial.position, orbit.initial.velocity * 3.0), course))
    }
}
