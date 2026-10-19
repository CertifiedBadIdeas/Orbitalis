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

package org.cbi.orbitalis.minecraft.flight.control

import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d

/** Nose alignment has no absolute roll target; transport the current hull frame. */
object RocketAttitude {
    fun target(
        current: Quaterniondc,
        direction: Vector3,
    ): Quaterniond {
        val nose = current.transform(Vector3d(0.0, 1.0, 0.0), Vector3d())
        return Quaterniond().rotationTo(nose, Vector3d(direction.x, direction.y, direction.z)).mul(current).normalize()
    }

    fun advance(
        current: Vector3,
        requested: Vector3,
        maximumAngle: Double,
    ): Vector3 =
        org.cbi.orbitalis.core.flight.RocketAttitudeControl
            .advance(current, requested, maximumAngle)
}
