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
import kotlin.math.cos
import kotlin.math.sin

class RocketAttitudeControlTest {
    private val up = Vector3(0.0, 1.0, 0.0)

    @Test fun controllerDampsRollAndBoundsAngularAccelerationEvenWhenInverted() {
        val damping = RocketAttitudeControl.acceleration(up, up, up * 0.2, Vector3.ZERO)
        assertTrue(damping.dot(up) < 0.0 && damping.length() <= 0.45)
        val inverted = RocketAttitudeControl.acceleration(up, up * -1.0, Vector3.ZERO, Vector3.ZERO)
        assertTrue(inverted.length() <= 0.4500000001 && inverted.length() > 0.4)
        assertTrue(kotlin.math.abs(inverted.dot(up)) < 1e-10)
        val settled = RocketAttitudeControl.acceleration(up, up, Vector3.ZERO, Vector3.ZERO)
        assertTrue(settled.length() < 1e-10)
    }

    @Test fun gimbalsSupportAForceDeflectionWithoutAnInstantHullTurn() {
        fun tilted(degrees: Double): Vector3 {
            val angle = Math.toRadians(degrees)
            return Vector3(sin(angle), cos(angle), 0.0) * 10.0
        }
        assertTrue(RocketAttitudeControl.Envelope(up).step(tilted(20.0), 0.25))
        assertTrue(!RocketAttitudeControl.Envelope(up).step(tilted(40.0), 0.25))
        val envelope = RocketAttitudeControl.Envelope(up)
        for (step in 0..100) assertTrue(envelope.step(tilted(minOf(20.0, step * 0.5)), 0.05))
    }
}
