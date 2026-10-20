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
import kotlin.math.cbrt

/** Versioned prescribed system. Physical periods use simulation time, independently of daylight. */
data class SolarSystemProfile(
    val surfaceGravity: Double,
    val epochTick: Long = 0,
    val earthPhase: Double = 0.0,
    val moonPhase: Double = PI / 2.0,
    val preset: Preset = Preset.CALENDAR,
    val earthSpinEpochTick: Long? = null,
    val selectedEarthWidth: Int? = null,
) {
    init {
        require(earthSpinEpochTick == null || earthSpinEpochTick >= 0)
        require(surfaceGravity.isFinite() && surfaceGravity in 1e-6..1e6)
        require(epochTick >= 0 && earthPhase.isFinite() && moonPhase.isFinite())
        require(earthPhase in -PI..PI && moonPhase in -PI..PI)
    }

    val version get() = if (earthSpinEpochTick == null && selectedEarthWidth == null) preset.version else VERSION

    val geometry =
        org.cbi.orbitalis.core.planet
            .CelestialGeometry(selectedEarthWidth)
    val earthGravity = FlatPlanetDefinition.gravity(surfaceGravity, geometry.earthWidth)
    val moonGravity =
        RectangularGravity(
            geometry.moonHalfSize,
            FlatPlanetDefinition.SEA_LEVEL,
            geometry.moonHalfSize,
            surfaceGravity * geometry.moonGravityRatio,
        )
    val sunMu = earthGravity.body.mu * 10000.0
    val earthOrbitRadius = if (preset == Preset.COMPACT) LEGACY_EARTH_ORBIT_RADIUS else radiusForPeriod(sunMu, YEAR_SECONDS)

    /** Emitting cube half-edge (inscribed radius); face-on width stays near 0.53 degrees at Earth. */
    val sunRadius = earthOrbitRadius * SUN_RADIUS_TO_ORBIT
    val moonOrbitRadius =
        if (preset ==
            Preset.COMPACT
        ) {
            LEGACY_MOON_ORBIT_RADIUS
        } else {
            radiusForPeriod(earthGravity.body.mu, LUNAR_PERIOD_SECONDS)
        }

    /** Circular restricted-three-body Hill approximation; presentation boundary, never a gravity cutoff. */
    val earthHillRadius = earthOrbitRadius * cbrt(earthGravity.body.mu / (3.0 * sunMu))
    val moonHillRadius = moonOrbitRadius * cbrt(moonGravity.mu / (3.0 * earthGravity.body.mu))

    /** Earth-centred operating envelope, including the lunar orbit and 100 km exterior margin. */
    val flightRange = maxOf(100000.0, moonOrbitRadius + 100000.0)
    val earthOrbit = KeplerOrbit(sunMu, earthOrbitRadius, meanAnomalyAtEpoch = earthPhase, epochTick = epochTick)
    val moonOrbit = KeplerOrbit(earthGravity.body.mu, moonOrbitRadius, meanAnomalyAtEpoch = moonPhase, epochTick = epochTick)
    val system =
        CelestialSystem(
            listOf(
                CelestialBody(SUN, null, sunMu),
                CelestialBody(EARTH, SUN, earthGravity.body.mu, earthOrbit),
                CelestialBody(MOON, EARTH, moonGravity.mu, moonOrbit),
            ),
        )

    /** Native Earth rotates in the nonrotating geocentric parent; null preserves frozen legacy axes. */
    fun earthSurfaceFrame(
        tick: Long,
        seconds: Double = 0.0,
    ): FrameKinematics {
        require(tick >= 0 && seconds.isFinite())
        val zero = org.cbi.orbitalis.core.planet.Vector3.ZERO
        val origin =
            org.cbi.orbitalis.core.planet
                .CartesianState(zero, zero)
        val epoch = earthSpinEpochTick ?: return FrameKinematics(origin)
        val omega = 2.0 * PI / SIDEREAL_DAY_SECONDS
        // Integer-period phases retain sub-tick precision even at Long.MAX_VALUE epochs.
        val solar = (tick % SOLAR_DAY_TICKS - epoch % SOLAR_DAY_TICKS).toDouble() / SOLAR_DAY_TICKS
        val annual = (tick % YEAR_TICKS - epoch % YEAR_TICKS).toDouble() / YEAR_TICKS
        val angle = 2.0 * PI * (solar + annual) + omega * (seconds % SIDEREAL_DAY_SECONDS)
        val axis =
            org.cbi.orbitalis.core.planet
                .Vector3(0.0, 0.0, 1.0)
        return FrameKinematics(origin, Rotation3.axisAngle(axis, angle), axis * omega)
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

    /** Preserve phase at retuning/migration; calendar radii follow mass while keeping physical periods. */
    fun retune(
        gravity: Double,
        tick: Long,
        preset: Preset = this.preset,
        selectedEarthWidth: Int? = this.selectedEarthWidth,
    ): SolarSystemProfile {
        require(tick >= epochTick)
        return SolarSystemProfile(
            gravity,
            tick,
            earthOrbit.meanAnomaly(tick),
            moonOrbit.meanAnomaly(tick),
            preset,
            earthSpinEpochTick,
            selectedEarthWidth,
        )
    }

    enum class Preset(
        val version: Int,
    ) {
        COMPACT(1),
        CALENDAR(2),
        ;

        companion object {
            fun fromVersion(version: Int): Preset =
                entries.firstOrNull { it.version == version }
                    ?: throw IllegalArgumentException("Unsupported celestial profile version: $version")
        }
    }

    companion object {
        const val VERSION = 3
        const val SOLAR_DAY_TICKS = 24000L
        const val YEAR_TICKS = 8765760L
        const val SOLAR_DAY_SECONDS = 1200.0
        const val YEAR_DAYS = 365.24
        const val LUNAR_PERIOD_DAYS = 27.32
        const val YEAR_SECONDS = YEAR_DAYS * SOLAR_DAY_SECONDS
        const val LUNAR_PERIOD_SECONDS = LUNAR_PERIOD_DAYS * SOLAR_DAY_SECONDS

        // Prograde spin and circular year in the same plane: mean solar day is exactly 1200 s.
        val SIDEREAL_DAY_SECONDS = 1.0 / (1.0 / SOLAR_DAY_SECONDS + 1.0 / YEAR_SECONDS)
        val SYNODIC_MONTH_SECONDS = 1.0 / (1.0 / LUNAR_PERIOD_SECONDS - 1.0 / YEAR_SECONDS)

        private fun radiusForPeriod(
            mu: Double,
            period: Double,
        ): Double = cbrt(mu * (period / (2.0 * PI)) * (period / (2.0 * PI)))

        const val SUN = "orbitalis:sun"
        const val EARTH = "orbitalis:overworld"
        const val MOON = "orbitalis:moon"
        const val LEGACY_EARTH_ORBIT_RADIUS = 2000000.0
        const val LEGACY_MOON_ORBIT_RADIUS = 20000.0
        val MOON_HALF_SIZE = LunarSurface.HALF_SIZE.toDouble()
        const val SUN_RADIUS_TO_ORBIT = 0.004650401069518716
    }
}
