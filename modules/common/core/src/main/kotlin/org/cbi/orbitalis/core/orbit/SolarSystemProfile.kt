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

import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.LunarSurface
import org.cbi.orbitalis.core.planet.RectangularGravity
import kotlin.math.PI

/** Compact preset. Persist orbit and spin epochs with the simulation clock, not daylight. */
data class SolarSystemProfile(
    val surfaceGravity: Double,
    val epochTick: Long = 0,
    val earthPhase: Double = 0.0,
    val moonPhase: Double = PI / 2.0,
    val earthSpinEpochTick: Long? = null,
) {
    init {
        require(earthSpinEpochTick == null || earthSpinEpochTick >= 0)
        require(surfaceGravity.isFinite() && surfaceGravity in 1e-6..1e6)
        require(epochTick >= 0 && earthPhase.isFinite() && moonPhase.isFinite())
        require(earthPhase in -PI..PI && moonPhase in -PI..PI)
    }

    val earthGravity = FlatPlanetDefinition.gravity(surfaceGravity)
    val moonGravity = RectangularGravity(MOON_HALF_SIZE, FlatPlanetDefinition.SEA_LEVEL, MOON_HALF_SIZE, surfaceGravity / 6.0)
    val sunMu = earthGravity.body.mu * 10000.0
    val earthOrbit = KeplerOrbit(sunMu, EARTH_ORBIT_RADIUS, meanAnomalyAtEpoch = earthPhase, epochTick = epochTick)
    val moonOrbit = KeplerOrbit(earthGravity.body.mu, MOON_ORBIT_RADIUS, meanAnomalyAtEpoch = moonPhase, epochTick = epochTick)
    val system =
        CelestialSystem(
            listOf(
                CelestialBody(SUN, null, sunMu),
                CelestialBody(EARTH, SUN, earthGravity.body.mu, earthOrbit),
                CelestialBody(MOON, EARTH, moonGravity.mu, moonOrbit),
            ),
        )

    /** Native Earth axes rotate in the geocentric nonrotating parent; null retains legacy receipts. */
    fun earthSurfaceFrame(
        tick: Long,
        seconds: Double = 0.0,
    ): FrameKinematics {
        require(tick >= 0 && seconds.isFinite())
        val origin =
            org.cbi.orbitalis.core.planet.CartesianState(
                org.cbi.orbitalis.core.planet.Vector3.ZERO,
                org.cbi.orbitalis.core.planet.Vector3.ZERO,
            )
        val epoch = earthSpinEpochTick ?: return FrameKinematics(origin)
        val omega = 2.0 * PI / EARTH_SPIN_PERIOD
        val elapsed = (tick % EARTH_SPIN_TICKS - epoch % EARTH_SPIN_TICKS) * 0.05 + seconds % EARTH_SPIN_PERIOD
        val axis =
            org.cbi.orbitalis.core.planet
                .Vector3(0.0, 0.0, 1.0)
        return FrameKinematics(origin, Rotation3.axisAngle(axis, omega * elapsed), axis * omega)
    }

    /** Top (+Y) faces Earth. Circular orbit: one prograde spin per revolution, in Earth axes. */
    @JvmOverloads
    fun lunarSurfaceFrame(
        tick: Long,
        seconds: Double = 0.0,
    ): FrameKinematics {
        val orbit = moonOrbit.sample(tick, seconds)
        val angle = kotlin.math.atan2(orbit.origin.position.y, orbit.origin.position.x) + PI / 2.0
        val omega =
            orbit.origin.position.cross(orbit.origin.velocity) *
                (1.0 / orbit.origin.position.dot(orbit.origin.position))
        return orbit.copy(
            orientation =
                Rotation3.axisAngle(
                    org.cbi.orbitalis.core.planet
                        .Vector3(0.0, 0.0, 1.0),
                    angle,
                ),
            angularVelocity = omega,
        )
    }

    /** Preserve the current positions when native gravity changes; velocities follow the new mass. */
    fun retune(
        gravity: Double,
        tick: Long,
    ): SolarSystemProfile {
        require(tick >= epochTick)
        return SolarSystemProfile(gravity, tick, earthOrbit.meanAnomaly(tick), moonOrbit.meanAnomaly(tick), earthSpinEpochTick)
    }

    companion object {
        const val VERSION = 2
        const val EARTH_SPIN_TICKS = 24000L
        const val EARTH_SPIN_PERIOD = 1200.0
        const val SUN = "orbitalis:sun"
        const val EARTH = "orbitalis:overworld"
        const val MOON = "orbitalis:moon"
        const val EARTH_ORBIT_RADIUS = 2000000.0
        const val MOON_ORBIT_RADIUS = 20000.0
        val MOON_HALF_SIZE = LunarSurface.HALF_SIZE.toDouble()
        const val SUN_VISUAL_RADIUS = 30000.0
    }
}
