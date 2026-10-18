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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3

/**
 * Local-to-parent rigid frame sampled at one instant. Origin derivatives and angular derivatives
 * are expressed in parent axes, in metres, seconds and radians. Local velocities are derivatives
 * measured by the rotating observer, not merely rotated inertial velocity vectors.
 *
 * The caller supplies an ephemeris/spin at the same instant as the state. This value neither advances
 * time nor changes the existing native-world frame identifiers or saved motion contracts.
 */
data class FrameKinematics(
    val origin: CartesianState,
    val orientation: Rotation3 = Rotation3.IDENTITY,
    val angularVelocity: Vector3 = Vector3.ZERO,
    val originAcceleration: Vector3 = Vector3.ZERO,
    val angularAcceleration: Vector3 = Vector3.ZERO,
) {
    fun toParent(local: CartesianState): CartesianState {
        val offset = orientation.apply(local.position)
        return CartesianState(
            origin.position + offset,
            origin.velocity + orientation.apply(local.velocity) + angularVelocity.cross(offset),
        )
    }

    fun fromParent(parent: CartesianState): CartesianState {
        val offset = parent.position - origin.position
        return CartesianState(
            orientation.inverse(offset),
            orientation.inverse(parent.velocity - origin.velocity - angularVelocity.cross(offset)),
        )
    }

    fun toParentAcceleration(
        local: CartesianState,
        localAcceleration: Vector3,
    ): Vector3 = orientation.apply(localAcceleration) + transportAcceleration(local)

    /** Subtracts origin, Coriolis, Euler and centripetal acceleration before rotating into local axes. */
    fun fromParentAcceleration(
        parent: CartesianState,
        parentAcceleration: Vector3,
    ): Vector3 = orientation.inverse(parentAcceleration - transportAcceleration(fromParent(parent)))

    fun toParentAngularVelocity(local: Vector3): Vector3 = angularVelocity + orientation.apply(local)

    fun fromParentAngularVelocity(parent: Vector3): Vector3 = orientation.inverse(parent - angularVelocity)

    private fun transportAcceleration(local: CartesianState): Vector3 {
        val offset = orientation.apply(local.position)
        val relativeVelocity = orientation.apply(local.velocity)
        return originAcceleration + angularVelocity.cross(relativeVelocity) * 2.0 +
            angularAcceleration.cross(offset) + angularVelocity.cross(angularVelocity.cross(offset))
    }
}
