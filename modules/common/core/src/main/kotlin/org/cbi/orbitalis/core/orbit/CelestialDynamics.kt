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
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs

/** Immutable Earth-relative force sample shared by physics and forecasts; daylight is not its clock. */
class CelestialDynamics(
    val profile: SolarSystemProfile,
    val epochTick: Long,
    earthCompatibility: Double = profile.surfaceGravity,
    moonCompatibility: Double = profile.surfaceGravity * profile.geometry.moonGravityRatio,
) {
    val earth =
        SurfaceGravityLayer(profile.earthGravity.body, earthCompatibility, fullHeight = profile.geometry.earthEnvelope.ascentDistance)
    val moon =
        SurfaceGravityLayer(profile.moonGravity, moonCompatibility, nearHeight = 128.0, fullHeight = 600.0, edgeWidth = 128.0)

    init {
        require(epochTick >= 0)
    }

    /** Immutable epoch sample, reusable across a bounded search without resolving ephemerides per point. */
    fun at(seconds: Double = 0.0): Sample = Sample(seconds)

    fun acceleration(
        position: Vector3,
        seconds: Double = 0.0,
    ): Vector3 = at(seconds).acceleration(position)

    fun earthAcceleration(
        state: CartesianState,
        seconds: Double = 0.0,
    ): Vector3 = at(seconds).earthAcceleration(state)

    fun lunarAcceleration(
        state: CartesianState,
        seconds: Double = 0.0,
    ): Vector3 = at(seconds).lunarAcceleration(state)

    inner class Sample internal constructor(
        seconds: Double,
    ) {
        val earthFrame = profile.earthSurfaceFrame(epochTick, seconds)
        val moonFrame = profile.lunarSurfaceFrame(epochTick, seconds)
        private val earthOrbit = profile.earthOrbit.sample(epochTick, seconds)
        private val earthOriginMoonForce =
            moonFrame.orientation.apply(
                moon.acceleration(
                    moonFrame.orientation.inverse(moonFrame.origin.position * -1.0),
                ),
            )

        fun acceleration(position: Vector3): Vector3 {
            val terrestrial = earthFrame.orientation.inverse(position)
            val local = moonFrame.orientation.inverse(position - moonFrame.origin.position)
            val moonForce = moonFrame.orientation.apply(moon.acceleration(local))
            val sunOffset = position + earthOrbit.origin.position
            val sunTide = sunOffset * (-profile.sunMu / sunOffset.length().let { it * it * it }) - earthOrbit.originAcceleration
            val terrestrialWindow =
                window(terrestrial, profile.geometry.earthHalfSize, 256.0, profile.geometry.earthEnvelope.ascentDistance, 256.0)
            val nativeForce = earth.acceleration(terrestrial)
            val terrestrialForce = earthFrame.orientation.apply(nativeForce)
            val supportedEarth = earthFrame.toParentAcceleration(CartesianState(terrestrial, Vector3.ZERO), nativeForce)
            val field =
                terrestrialForce + (supportedEarth - terrestrialForce) * terrestrialWindow +
                    (moonForce - earthOriginMoonForce + sunTide) * (1.0 - terrestrialWindow)
            val lunarWindow = window(local, profile.geometry.moonHalfSize, 128.0, 600.0, 128.0)
            if (lunarWindow == 0.0) return field
            val supported = moonFrame.toParentAcceleration(CartesianState(local, Vector3.ZERO), moon.acceleration(local))
            return field * (1.0 - lunarWindow) + supported * lunarWindow
        }

        fun earthAcceleration(state: CartesianState): Vector3 {
            val physical = earthFrame.toParent(state)
            return earthFrame.fromParentAcceleration(physical, acceleration(physical.position))
        }

        fun lunarAcceleration(state: CartesianState): Vector3 {
            val physical = moonFrame.toParent(state)
            return moonFrame.fromParentAcceleration(physical, acceleration(physical.position))
        }
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
