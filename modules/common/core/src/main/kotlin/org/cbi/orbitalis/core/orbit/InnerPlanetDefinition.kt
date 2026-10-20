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

import org.cbi.orbitalis.core.planet.BodyFlightEnvelope
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.RectangularGravity
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.cbrt

/**
 * Derived inner-planet parameters, not additional save fields. Widths and surface accelerations
 * scale from Earth; circular coplanar orbits retain the requested periods using the profile Sun mu.
 * The finite terrain is a flat rectangular slab, matching the existing Earth/Moon model.
 */
class InnerPlanetDefinition internal constructor(
    val id: String,
    earthWidth: Double,
    earthSurfaceGravity: Double,
    sunMu: Double,
    widthRatio: Double,
    gravityRatio: Double,
    orbitPeriodDays: Double,
    spinPeriodDays: Double,
    phase: Double,
) {
    val width = earthWidth * widthRatio
    val halfSize = width / 2.0
    val surfaceGravity = earthSurfaceGravity * gravityRatio
    val gravity = RectangularGravity(halfSize, FlatPlanetDefinition.SEA_LEVEL, halfSize, surfaceGravity)
    val orbitPeriodSeconds = orbitPeriodDays * SolarSystemProfile.SOLAR_DAY_SECONDS
    val spinPeriodSeconds = spinPeriodDays * SolarSystemProfile.SOLAR_DAY_SECONDS
    val orbitRadius = cbrt(sunMu * (orbitPeriodSeconds / (2.0 * PI)) * (orbitPeriodSeconds / (2.0 * PI)))

    // Fixed simulation-zero epochs keep derived phases stable through profile retuning/migration.
    val orbit = KeplerOrbit(sunMu, orbitRadius, meanAnomalyAtEpoch = phase)
    val hillRadius = orbitRadius * cbrt(gravity.mu / (3.0 * sunMu))
    val envelope =
        BodyFlightEnvelope(
            Vector3(halfSize, FlatPlanetDefinition.SEA_LEVEL, halfSize),
            maxOf(1800.0, width * 0.3),
            maxOf(1500.0, width * 0.2),
        )

    /**
     * Local surface to nonrotating geocentric root. At simulation zero local top is +Y;
     * positive spin is right-handed about +Z, with Venus rotating retrograde. The caller must
     * supply the catalogue sampled at the same [tick] and [seconds]. FrameKinematics owns point,
     * velocity and acceleration transport, including the spin-induced tangential velocity.
     */
    fun surfaceFrame(
        snapshot: CelestialSnapshot,
        tick: Long,
        seconds: Double = 0.0,
    ): FrameKinematics {
        require(snapshot.tick == tick && tick >= 0 && seconds.isFinite())
        val periodTicks = kotlin.math.round(kotlin.math.abs(spinPeriodSeconds) * 20.0).toLong()
        val angle =
            2.0 * PI * ((tick % periodTicks).toDouble() / 20.0 + seconds % kotlin.math.abs(spinPeriodSeconds)) /
                spinPeriodSeconds
        val axis = Vector3(0.0, 0.0, 1.0)
        return snapshot.frame(id, SolarSystemProfile.EARTH).copy(
            orientation = Rotation3.axisAngle(axis, angle),
            angularVelocity = axis * (2.0 * PI / spinPeriodSeconds),
        )
    }
}
