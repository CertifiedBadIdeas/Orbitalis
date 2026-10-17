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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketAscentTest {
    private val field = SurfaceGravityLayer(RectangularGravity(1000.0, 64.0, 1000.0, 11.0), 11.0)

    @Test fun connectionPreservesBothMovingEndpointsAndAccelerations() {
        val start = CartesianState(Vector3(200.0, 140.0, -300.0), Vector3(0.0, 5.0, 0.0))
        val end = CartesianState(Vector3(500.0, 800.0, 0.0), Vector3(60.0, -20.0, 0.0))
        val a = Vector3(1.0, 3.0, 0.0)
        val b = Vector3(-2.0, -3.0, 0.0)
        val path = RocketConnection(start, end, a, b, 32.0)
        for ((sample, state, acceleration) in listOf(Triple(path.at(0.0), start, a), Triple(path.at(32.0), end, b))) {
            assertTrue((sample.state.position - state.position).length() < 1e-8)
            assertTrue((sample.state.velocity - state.velocity).length() < 1e-8)
            assertTrue((sample.acceleration - acceleration).length() < 1e-8)
        }
    }

    @Test fun arbitraryPadsAccelerateHorizontallyBeforeReachingOrbitAndJoinItWhileMoving() {
        val orbit = BallisticOrbit.plan(field, 900.0)
        for (pad in listOf(
            Vector3(0.0, 90.0, 0.0),
            Vector3(850.0, 160.0, 500.0),
            Vector3(-850.0, 280.0, -700.0),
            Vector3(300.0, -35.0, -800.0),
        )) {
            val course = RocketCourse(orbit, RocketCourse.launchAxis(pad))
            val ascent = RocketAscent.plan(field, course, CartesianState(pad, Vector3(0.0, 5.0, 0.0)), 60.0)
            val early = ascent.path.at(5.0).state
            assertTrue(early.velocity.dot(course.axis) > 0.5, "No early horizontal acceleration from $pad")
            assertTrue(early.position.y < orbit.initial.position.y - 100.0)
            val end = ascent.path.at(ascent.path.duration)
            val expected = course.stateAt(ascent.orbitPhase)
            assertTrue((end.state.position - expected.position).length() < 1e-7)
            assertTrue((end.state.velocity - expected.velocity).length() < 1e-7 && end.state.velocity.length() > 50.0)
            assertTrue((end.acceleration - field.acceleration(expected.position)).length() < 1e-7)
        }
    }
}
