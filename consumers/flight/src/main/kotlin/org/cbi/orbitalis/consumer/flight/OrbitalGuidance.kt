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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Consumer experiment: inertial acceleration requests, with no world or engine ownership. */
internal object OrbitalGuidance {
    const val RADIUS = 18000.0
    const val MAX_ACCELERATION = 3.0

    data class Orbit(
        val radial: Vector3,
        val tangent: Vector3,
        val radius: Double,
        val radialSpeed: Double,
        val tangentialSpeed: Double,
        val planeError: Double,
    )

    fun limited(
        value: Vector3,
        maximum: Double,
    ): Vector3 {
        val length = value.length()
        return if (length > maximum) value * (maximum / length) else value
    }

    fun observe(
        state: CartesianState,
        centre: Vector3,
        normal: Vector3,
    ): Orbit {
        val relative = state.position - centre
        val plane = relative.dot(normal)
        val inPlane = relative - normal * plane
        val radius = inPlane.length()
        require(radius.isFinite() && radius >= 100) { "Outside orbital guidance envelope" }
        val radial = inPlane * (1 / radius)
        val tangent = normal.cross(radial)
        return Orbit(radial, tangent, radius, state.velocity.dot(radial), state.velocity.dot(tangent), plane)
    }

    fun point(
        state: CartesianState,
        target: Vector3,
        maximumSpeed: Double,
    ): Vector3 {
        val error = target - state.position
        // Conservative stopping distance leaves margin for engine fade and discrete sampling.
        val speed = min(maximumSpeed, sqrt(2 * 1.5 * error.length()))
        val desired = limited(error * 0.2, speed)
        return limited(desired - state.velocity, MAX_ACCELERATION)
    }

    fun orbit(
        state: CartesianState,
        centre: Vector3,
        normal: Vector3,
        radius: Double,
        desiredTangentialSpeed: Double,
    ): Vector3 {
        val o = observe(state, centre, normal)
        val radial = ((radius - o.radius) * 0.04 - o.radialSpeed * 0.8).coerceIn(-2.0, 2.0)
        val tangent = ((desiredTangentialSpeed - o.tangentialSpeed) * 0.8).coerceIn(-2.0, 2.0)
        val plane = (-o.planeError * 0.04 - state.velocity.dot(normal) * 0.8).coerceIn(-2.0, 2.0)
        // Geometric feed-forward keeps the path circular even while braking. At circular
        // speed this equals central gravity, so a settled insertion requires no thrust.
        return limited(o.radial * radial + o.tangent * tangent + normal * plane, MAX_ACCELERATION) +
            o.radial * (-o.tangentialSpeed * o.tangentialSpeed / o.radius)
    }

    fun returnSpeed(
        remainingArc: Double,
        circularSpeed: Double,
    ): Double = Math.copySign(min(circularSpeed, min(sqrt(2 * 1.2 * abs(remainingArc)), abs(remainingArc) * 0.3)), remainingArc)

    fun angle(
        radial: Vector3,
        homeRadial: Vector3,
        normal: Vector3,
    ): Double = atan2(normal.dot(homeRadial.cross(radial)), homeRadial.dot(radial))

    fun angleStep(
        previous: Double,
        current: Double,
    ): Double = atan2(sin(current - previous), cos(current - previous))
}
