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

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** One 20-Hz game-style glider step in local up coordinates, velocities in blocks/tick. */
object GliderFlight {
    fun step(
        velocity: Vector3,
        yawDegrees: Double,
        pitchDegrees: Double,
        gravity: Vector3,
    ): Vector3 {
        require(yawDegrees.isFinite() && pitchDegrees.isFinite() && pitchDegrees in -90.0..90.0)
        val pitch = Math.toRadians(pitchDegrees)
        val yaw = Math.toRadians(yawDegrees)
        val forwardX = -sin(yaw)
        val forwardZ = cos(yaw)
        val horizontal = hypot(velocity.x, velocity.z)
        val lift = cos(pitch) * cos(pitch)
        val ballistic = velocity + gravity
        var next = ballistic + Vector3(0.0, max(0.0, -gravity.y) * 0.75 * lift, 0.0)
        if (next.y < 0.0) {
            val recovery = -next.y * 0.1 * lift
            next += Vector3(forwardX * recovery, recovery, forwardZ * recovery)
        }
        if (pitch < 0.0) {
            val climb = horizontal * -sin(pitch) * 0.04
            next += Vector3(-forwardX * climb, climb * 3.2, -forwardZ * climb)
        }
        next += Vector3((forwardX * horizontal - next.x) * 0.1, 0.0, (forwardZ * horizontal - next.z) * 0.1)
        // Unpowered lift/steering can redirect motion, but cannot add kinetic energy.
        // Local vertical speed can already be positive as the gravity frame turns in flight.
        val availableSpeed = ballistic.length()
        val steeredSpeed = next.length()
        if (steeredSpeed > availableSpeed) next *= availableSpeed / steeredSpeed
        return Vector3(next.x * 0.99, next.y * 0.98, next.z * 0.99)
    }
}
