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
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs

/** Immutable Earth-relative force sample shared by physics and forecasts; daylight is not its clock. */
class CelestialDynamics(
    val profile: SolarSystemProfile,
    val epochTick: Long,
    earthCompatibility: Double = profile.surfaceGravity,
    moonCompatibility: Double = profile.surfaceGravity / 6.0,
) {
    val earth = SurfaceGravityLayer(profile.earthGravity.body, earthCompatibility, fullHeight = FlatPlanetDefinition.ASCENT_HEIGHT)
    val moon =
        SurfaceGravityLayer(profile.moonGravity, moonCompatibility, nearHeight = 128.0, fullHeight = 600.0, edgeWidth = 128.0)

    init {
        require(epochTick >= 0)
    }

    fun acceleration(
        position: Vector3,
        seconds: Double = 0.0,
    ): Vector3 {
        val earthFrame = profile.earthSurfaceFrame(epochTick, seconds)
        val terrestrial = earthFrame.orientation.inverse(position)
        val frame = profile.lunarSurfaceFrame(epochTick, seconds)
        val local = frame.orientation.inverse(position - frame.origin.position)
        val moonForce = frame.orientation.apply(moon.acceleration(local))
        // Prescribed Earth motion does not react to its satellite: retain its origin by using lunar tides.
        val earthOriginMoonForce = frame.orientation.apply(moon.acceleration(frame.orientation.inverse(frame.origin.position * -1.0)))
        val earthOrbit = profile.earthOrbit.sample(epochTick, seconds)
        val sunOffset = position + earthOrbit.origin.position
        val sunTide = sunOffset * (-profile.sunMu / sunOffset.length().let { it * it * it }) - earthOrbit.originAcceleration
        val terrestrialWindow = window(terrestrial, FlatPlanetDefinition.HALF_SIZE, 256.0, FlatPlanetDefinition.ASCENT_HEIGHT, 256.0)
        val terrestrialForce = earthFrame.orientation.apply(earth.acceleration(terrestrial))
        val supportedEarth = earthFrame.toParentAcceleration(CartesianState(terrestrial, Vector3.ZERO), earth.acceleration(terrestrial))
        val field =
            terrestrialForce + (supportedEarth - terrestrialForce) * terrestrialWindow +
                (moonForce - earthOriginMoonForce + sunTide) * (1.0 - terrestrialWindow)
        val lunarWindow = window(local, SolarSystemProfile.MOON_HALF_SIZE, 128.0, 600.0, 128.0)
        if (lunarWindow == 0.0) return field
        // Surface compatibility includes the acceleration of a fixed ground point in its spinning frame.
        // Dynamic Coriolis terms are still applied by fromParentAcceleration for moving local vessels.
        val supported = frame.toParentAcceleration(CartesianState(local, Vector3.ZERO), moon.acceleration(local))
        return field * (1.0 - lunarWindow) + supported * lunarWindow
    }

    fun earthAcceleration(
        state: CartesianState,
        seconds: Double = 0.0,
    ): Vector3 {
        val frame = profile.earthSurfaceFrame(epochTick, seconds)
        val physical = frame.toParent(state)
        return frame.fromParentAcceleration(physical, acceleration(physical.position, seconds))
    }

    fun lunarAcceleration(
        state: CartesianState,
        seconds: Double = 0.0,
    ): Vector3 {
        val frame = profile.lunarSurfaceFrame(epochTick, seconds)
        val physical = frame.toParent(state)
        return frame.fromParentAcceleration(physical, acceleration(physical.position, seconds))
    }

    private fun window(
        p: Vector3,
        half: Double,
        near: Double,
        far: Double,
        edge: Double,
    ): Double {
        fun fade(t: Double): Double =
            when {
                t <= 0.0 -> 1.0
                t >= 1.0 -> 0.0
                else -> 1.0 - t * t * t * (10.0 + t * (-15.0 + 6.0 * t))
            }
        return fade((abs(p.x) - half) / edge) * fade((abs(p.z) - half) / edge) *
            fade((p.y - 64.0 - near) / (far - near)) * fade((-p.y - 64.0) / 256.0)
    }
}
