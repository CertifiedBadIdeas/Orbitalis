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

package org.cbi.orbitalis.core.planet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/** Bounded laboratory autopilot. Outputs thrust acceleration; never integrates or corrects a body. */
class SurfaceFlight(
    val itinerary: Itinerary = Itinerary.ORBIT,
) {
    enum class Itinerary { HOP, ORBIT }

    enum class Phase { GROUND, ASCENT, HOVER, INSERTION, COAST, RETURN, DESCENT, TOUCHDOWN, LANDED, FAILED }

    val gravity = TwoBodyGravity(1024.0)
    var phase = Phase.GROUND
        private set
    var seconds = 0.0
        private set
    var failure: String? = null
        private set
    var maximumRadius = 0.0
        private set
    var maximumThrust = 0.0
        private set
    var coastAngle = 0.0
        private set
    var maximumCoastEnergyError = 0.0
        private set
    var thrust = Vector3.ZERO
        private set
    private var stableSeconds = 0.0
    private var coastEnergy = 0.0
    private var previousCoastPosition = Vector3.ZERO

    /** Measured inertial state, relative to the attraction centre, and actual physics timestep. */
    fun command(
        state: CartesianState,
        dt: Double,
    ): Vector3 {
        require(dt.isFinite() && dt > 0.0 && dt <= 0.1)
        seconds += dt
        val radius = state.position.length()
        maximumRadius = max(maximumRadius, radius)
        if (radius !in 9.1..64.0 || state.velocity.length() > 30.0 || (seconds > 90.0 && phase != Phase.LANDED)) {
            failure = "Flight exceeded its time, radius or speed bounds"
            phase = Phase.FAILED
        }
        if (phase == Phase.FAILED) {
            thrust = Vector3.ZERO
            return thrust
        }
        val gravityAcceleration = gravity.acceleration(state.position)
        val heightError = (state.position - LAUNCH).length()
        val slow = state.velocity.length() < 0.15
        var desired = Vector3.ZERO
        when (phase) {
            Phase.GROUND -> {
                if (settled(heightError < 0.12 && slow, dt, 1.0)) transition(Phase.ASCENT)
            }

            Phase.ASCENT -> {
                desired = seek(state, ABOVE_PAD, 3.0) - gravityAcceleration
                if (settled((state.position - ABOVE_PAD).length() < 0.06 && slow, dt, 0.25)) {
                    transition(if (itinerary == Itinerary.HOP) Phase.HOVER else Phase.INSERTION)
                }
            }

            Phase.HOVER -> {
                desired = seek(state, ABOVE_PAD, 3.0) - gravityAcceleration
                if (settled((state.position - ABOVE_PAD).length() < 0.12 && slow, dt, 2.0)) transition(Phase.DESCENT)
            }

            Phase.INSERTION -> {
                val radial = state.position.normalized()
                val tangent = Vector3(radial.y, -radial.x, 0.0).normalized()
                val vr = state.velocity.dot(radial)
                val vt = state.velocity.dot(tangent)
                desired = radial * (6.0 * (16.0 - radius) - 5.0 * vr - vt * vt / radius) +
                    tangent * (4.0 * (8.0 - vt) + vr * vt / radius) +
                    Vector3(0.0, 0.0, -4.0 * state.position.z - 4.0 * state.velocity.z) - gravityAcceleration
                if (settled(abs(radius - 16.0) < 0.06 && abs(vr) < 0.06 && abs(vt - 8.0) < 0.06, dt, 0.25)) {
                    transition(Phase.COAST)
                    coastEnergy = gravity.specificEnergy(state)
                    previousCoastPosition = state.position
                    desired = Vector3.ZERO
                }
            }

            Phase.COAST -> {
                val p = previousCoastPosition
                val q = state.position
                coastAngle += atan2(p.y * q.x - p.x * q.y, p.x * q.x + p.y * q.y)
                previousCoastPosition = q
                maximumCoastEnergyError = max(maximumCoastEnergyError, abs((gravity.specificEnergy(state) - coastEnergy) / coastEnergy))
                if (coastAngle >= 2.0 * PI) transition(Phase.RETURN)
            }

            Phase.RETURN -> {
                desired = seek(state, ABOVE_PAD, 4.0) - gravityAcceleration
                if (settled((state.position - ABOVE_PAD).length() < 0.06 && slow, dt, 0.25)) transition(Phase.DESCENT)
            }

            Phase.DESCENT -> {
                desired = seek(state, LAUNCH + Vector3(0.0, -0.2, 0.0), if (radius > 11.0) 2.0 else 0.5) - gravityAcceleration
                if (heightError < 0.12 && slow) {
                    transition(Phase.TOUCHDOWN)
                    desired = Vector3.ZERO
                }
            }

            Phase.TOUCHDOWN -> {
                if (settled(heightError < 0.12 && slow, dt, 1.5)) transition(Phase.LANDED)
            }

            Phase.LANDED, Phase.FAILED -> {
                Unit
            }
        }
        thrust = limited(desired, MAX_THRUST)
        maximumThrust = max(maximumThrust, thrust.length())
        return thrust
    }

    private fun seek(
        state: CartesianState,
        target: Vector3,
        speed: Double,
    ): Vector3 = (limited((target - state.position) * 2.0, speed) - state.velocity) * 4.0

    private fun settled(
        condition: Boolean,
        dt: Double,
        duration: Double,
    ): Boolean {
        stableSeconds = if (condition) stableSeconds + dt else 0.0
        return stableSeconds >= duration
    }

    private fun transition(next: Phase) {
        phase = next
        stableSeconds = 0.0
    }

    private fun limited(
        vector: Vector3,
        maximum: Double,
    ): Vector3 {
        val length = vector.length()
        return if (length > maximum) vector * (maximum / length) else vector
    }

    companion object {
        const val MAX_THRUST = 40.0
        val LAUNCH = Vector3(0.0, 9.5, 0.0)
        val ABOVE_PAD = Vector3(0.0, 16.0, 0.0)
    }
}
