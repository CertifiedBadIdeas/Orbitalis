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
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RocketAttitudeTest {
    @Test fun noseAlignmentPreservesRollAndCrossesTheDownwardPoleContinuously() {
        var frame = Quaterniond().rotateY(0.7)
        var previous = Quaterniond(frame)
        for (step in 0..400) {
            val angle = step * 2.0 * PI / 400.0
            val direction = Vector3(sin(angle), cos(angle), 0.0)
            frame = RocketAttitude.target(frame, direction)
            val nose = frame.transform(Vector3d(0.0, 1.0, 0.0))
            assertTrue(nose.distance(Vector3d(direction.x, direction.y, direction.z)) < 1e-10)
            assertTrue(Quaterniond(previous).conjugate().mul(frame).angle() < 0.02, "Heading introduced a roll discontinuity")
            previous = Quaterniond(frame)
        }
        val rolled = Quaterniond().rotateXYZ(0.5, 1.2, -0.7)
        val nose = rolled.transform(Vector3d(0.0, 1.0, 0.0))
        val target = RocketAttitude.target(rolled, Vector3(nose.x, nose.y, nose.z))
        assertTrue(Quaterniond(rolled).conjugate().mul(target).angle() < 1e-7)
    }

    @Test fun poweredNoseRequestsHaveBoundedSlewEvenWhenThrustReverses() {
        var nose = Vector3(0.0, 1.0, 0.0)
        val opposite = Vector3(0.0, -1.0, 0.0)
        repeat(400) {
            val next = RocketAttitude.advance(nose, opposite, 0.0125)
            assertTrue(kotlin.math.acos(nose.dot(next).coerceIn(-1.0, 1.0)) <= 0.01250001)
            nose = next
        }
        assertTrue((nose - opposite).length() < 1e-8)
    }
}
