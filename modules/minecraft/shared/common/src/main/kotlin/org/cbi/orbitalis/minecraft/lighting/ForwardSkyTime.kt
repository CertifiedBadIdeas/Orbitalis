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

package org.cbi.orbitalis.minecraft.lighting

import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import kotlin.math.ceil

/** Next solar time in a forward-only 24,000-tick mean solar day, never a clock assignment. */
object ForwardSkyTime {
    fun ticksUntil(
        profile: SolarSystemProfile,
        tick: Long,
        daytime: Int,
    ): Int {
        require(daytime in 0..23999)
        val angle = SkyLighting.daylightAngle(SkyLighting.earthDirection(profile, tick)).toDouble()
        // Positive physical Earth spin makes the native solar hour angle decrease.
        val target = ((0.25 - daytime / 24000.0) % 1.0 + 1.0) % 1.0
        val fraction = ((angle - target) % 1.0 + 1.0) % 1.0
        val remaining = if (fraction < 1.0 / 24000.0) 1.0 else fraction
        return ceil(remaining * SolarSystemProfile.SOLAR_DAY_TICKS).toInt().coerceIn(1, 24000)
    }
}
