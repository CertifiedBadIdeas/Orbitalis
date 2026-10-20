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

import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import kotlin.math.PI
import kotlin.math.atan2

/** One ephemeris source for native daylight and all distant-body shaders. */
object SkyLighting {
    fun sunPosition(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double = 0.0,
    ): Vector3 =
        profile.system
            .sample(tick, seconds)
            .frame(SolarSystemProfile.SUN, SolarSystemProfile.EARTH)
            .origin.position

    fun direction(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double,
        point: Vector3,
    ): Vector3 = (sunPosition(profile, tick, seconds) - point).normalized()

    fun earthDirection(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double = 0.0,
    ): Vector3 = profile.earthSurfaceFrame(tick, seconds).orientation.inverse(direction(profile, tick, seconds, Vector3(0.0, 0.0, 0.0)))

    fun daylightAngle(direction: Vector3): Float = ((atan2(-direction.x, direction.y) / (2.0 * PI) + 1.0) % 1.0).toFloat()

    @JvmStatic fun physicalOverworld(level: Level): Boolean =
        level.dimension() == Level.OVERWORLD && CelestialFlight.available(level) &&
            (if (level.isClientSide) SkyLightingData.clientCosmic else SkyLightingData.get(level.server!!).cosmic)

    @JvmStatic fun timeOfDay(
        level: Level,
        partialTick: Float,
    ): Float {
        val sample = if (level.isClientSide) CelestialFlight.sampleClient(partialTick) else CelestialFlight.sample(level)
        return daylightAngle(earthDirection(sample.frames.profile, sample.frames.tick, sample.frames.seconds))
    }
}
