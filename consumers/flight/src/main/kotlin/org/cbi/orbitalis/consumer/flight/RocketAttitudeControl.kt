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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Shared nose controller for real actuator commands and bounded return feasibility prediction. */
internal object RocketAttitudeControl {
    fun rate(
        previous: Vector3?,
        desired: Vector3,
        seconds: Double,
    ): Vector3 = if (previous == null) Vector3.ZERO else OrbitalGuidance.limited(previous.cross(desired) * (1.0 / seconds), 0.8)

    fun acceleration(
        current: Vector3,
        desired: Vector3,
        omega: Vector3,
        targetRate: Vector3,
    ): Vector3 {
        val cross = current.cross(desired)
        val sine = cross.length()
        val dot = current.dot(desired).coerceIn(-1.0, 1.0)
        val error =
            when {
                sine > 1e-8 -> {
                    cross * (atan2(sine, dot) / sine)
                }

                dot >= 0.0 -> {
                    Vector3.ZERO
                }

                else -> {
                    current
                        .cross(if (kotlin.math.abs(current.x) < 0.9) Vector3(1.0, 0.0, 0.0) else Vector3(0.0, 0.0, 1.0))
                        .normalized() * Math.PI
                }
            }
        return OrbitalGuidance.limited(error * 0.8 - (omega - targetRate) * 1.8, 0.45)
    }

    /** Nose-only rollout; roll is damped by the live controller but does not change the thrust cone. */
    class Envelope(
        initial: Vector3,
    ) {
        private var nose = initial.normalized()
        private var omega = Vector3.ZERO
        private var desired = nose
        private var previous: Vector3? = null

        fun step(
            thrust: Vector3,
            seconds: Double,
        ): Boolean {
            val magnitude = thrust.length()
            if (magnitude >= 0.3) desired = thrust * (1.0 / magnitude)
            // Native engines gimbal 30 degrees per axis. Use the conservative inscribed cone,
            // with five degrees reserved for interpolation, tracking error and torque allocation.
            val supported = magnitude <= 1.5 || nose.dot(desired) >= cos(Math.toRadians(25.0))
            val targetRate = if (magnitude > 1.5) rate(previous, desired, seconds) else Vector3.ZERO
            if (magnitude > 1.5) previous = desired
            val angular = acceleration(nose, desired, omega, targetRate)
            val half = omega + angular * (seconds * 0.5)
            val speed = half.length()
            if (speed > 1e-8) {
                val axis = half * (1.0 / speed)
                val angle = speed * seconds
                nose = (nose * cos(angle) + axis.cross(nose) * sin(angle) + axis * (axis.dot(nose) * (1.0 - cos(angle)))).normalized()
            }
            omega += angular * seconds
            return supported
        }
    }
}
