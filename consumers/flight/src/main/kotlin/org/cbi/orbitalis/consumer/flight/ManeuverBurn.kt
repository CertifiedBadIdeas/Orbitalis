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

/** Integrates observed non-gravitational delta-v, rather than assuming throttle equals force. */
internal class ManeuverBurn(
    val target: Vector3,
) {
    var delivered = Vector3.ZERO
        private set
    val remaining get() = target - delivered

    fun observe(
        previousVelocity: Vector3,
        velocity: Vector3,
        previousGravity: Vector3,
        gravity: Vector3,
        seconds: Double,
    ) {
        require(seconds.isFinite() && seconds > 0.0 && seconds <= 0.25)
        delivered += velocity - previousVelocity - (previousGravity + gravity) * (seconds * 0.5)
    }

    fun thrust(maximum: Double = 3.0): Vector3 {
        require(maximum.isFinite() && maximum > 0.0)
        return OrbitalGuidance.limited(remaining * 0.5, maximum)
    }
}
