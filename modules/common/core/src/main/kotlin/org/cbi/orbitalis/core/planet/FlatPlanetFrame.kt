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

import org.cbi.orbitalis.core.orbit.InertialFrame
import org.cbi.orbitalis.core.orbit.InertialFrames

/** Current native worlds are leaves of a reusable inertial frame graph. */
object FlatPlanetFrame {
    val origin = Vector3(0.0, -4096.0, 0.0)

    const val BODY = "orbitalis:overworld_body"
    const val SPACE = "orbitalis:space"
    val frames =
        InertialFrames(
            listOf(
                InertialFrame(BODY, null, CartesianState(Vector3.ZERO, Vector3.ZERO)),
                InertialFrame(SPACE, BODY, CartesianState(origin, Vector3.ZERO)),
            ),
        )

    fun toSpace(position: Vector3): Vector3 = frames.transform(CartesianState(position, Vector3.ZERO), BODY, SPACE, 0).position

    fun toSurface(position: Vector3): Vector3 = frames.transform(CartesianState(position, Vector3.ZERO), SPACE, BODY, 0).position
}
