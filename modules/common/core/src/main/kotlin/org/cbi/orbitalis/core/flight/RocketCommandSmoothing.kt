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

package org.cbi.orbitalis.core.flight

import org.cbi.orbitalis.core.planet.Vector3

/** Bound changes to requested thrust acceleration; native engines still own force application. */
class RocketCommandSmoothing(
    private val jerk: Double = 10.0,
) {
    private var value = Vector3.ZERO

    fun next(
        target: Vector3,
        seconds: Double = 0.05,
    ): Vector3 {
        require(seconds.isFinite() && seconds > 0.0 && jerk.isFinite() && jerk > 0.0)
        value += OrbitalGuidance.limited(target - value, jerk * seconds)
        return value
    }

    fun clear() {
        value = Vector3.ZERO
    }
}
