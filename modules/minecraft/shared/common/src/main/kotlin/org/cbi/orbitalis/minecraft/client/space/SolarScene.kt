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

package org.cbi.orbitalis.minecraft.client.space

import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3

/** One camera-relative emitting cube for all solar views; never a native physics position. */
object SolarScene {
    data class Cube(
        val centre: Vector3,
        val halfEdge: Double,
        val phase: Double,
    )

    fun sample(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double,
        observer: Vector3,
    ): Cube {
        val centre =
            profile.system
                .sample(tick, seconds)
                .frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH)
                .origin.position
        // Subtract in double precision before sending solar-radius units to the GPU.
        val relative = (centre - observer) * (1.0 / profile.sunRadius)
        // A bounded, continuous pair of temporal harmonics, including across the wrap.
        val phase =
            org.cbi.orbitalis.core.planet.SolarShape
                .phase(tick, seconds)
        return Cube(relative, profile.sunRadius, phase)
    }
}
