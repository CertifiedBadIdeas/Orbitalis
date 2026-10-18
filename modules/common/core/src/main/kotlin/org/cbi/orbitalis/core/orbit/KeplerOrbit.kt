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
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Prescribed bound orbit about a fixed parent focus. Orbital XY axes are mapped into the parent's
 * parallel inertial axes by [plane]; this does not rotate the child's surface/body frame.
 * Distances are metres, mu is m^3/s^2 and angles radians. Time uses simulation ticks, never daylight.
 */
class KeplerOrbit(
    val mu: Double,
    val semiMajorAxis: Double,
    val eccentricity: Double = 0.0,
    val plane: Rotation3 = Rotation3.IDENTITY,
    meanAnomalyAtEpoch: Double = 0.0,
    val epochTick: Long = 0,
) {
    val meanAnomalyAtEpoch: Double
    val period: Double
    private val speed: Double
    private val minorRatio: Double

    init {
        require(mu.isFinite() && mu > 0.0) { "Parent gravitational parameter must be finite and positive" }
        require(semiMajorAxis.isFinite() && semiMajorAxis > 0.0) { "Semi-major axis must be finite and positive" }
        require(eccentricity.isFinite() && eccentricity >= 0.0 && eccentricity < 1.0) { "Expected a bound elliptic orbit" }
        require(meanAnomalyAtEpoch.isFinite() && epochTick >= 0) { "Invalid orbital epoch" }
        this.meanAnomalyAtEpoch = wrap(meanAnomalyAtEpoch)
        speed = sqrt(mu / semiMajorAxis)
        period = (semiMajorAxis / speed) * (2.0 * PI)
        minorRatio = sqrt((1.0 - eccentricity) * (1.0 + eccentricity))
        val periapsis = semiMajorAxis * (1.0 - eccentricity)
        require(speed.isFinite() && speed > 0.0 && period.isFinite() && period > 0.0) { "Unrepresentable orbital period" }
        require(periapsis > 0.0 && (mu / periapsis / periapsis).isFinite()) { "Unrepresentable periapsis acceleration" }
        require((speed * minorRatio / (1.0 - eccentricity)).isFinite()) { "Unrepresentable periapsis velocity" }
        require((semiMajorAxis * (1.0 + eccentricity)).isFinite()) { "Unrepresentable apoapsis" }
    }

    /** All components are relative to the parent focus at the same [tick]. No incremental state. */
    fun sample(tick: Long): FrameKinematics {
        require(tick >= 0) { "Simulation tick must be nonnegative" }
        val anomaly = eccentricAnomaly(meanAnomaly(tick))
        val c = cos(anomaly)
        val s = sin(anomaly)
        val denominator = 1.0 - eccentricity * c
        val position = plane.apply(Vector3(semiMajorAxis * (c - eccentricity), semiMajorAxis * minorRatio * s, 0.0))
        val velocity = plane.apply(Vector3(-speed * s / denominator, speed * minorRatio * c / denominator, 0.0))
        val radius = position.length()
        val acceleration = position.normalized() * (-mu / radius / radius)
        return FrameKinematics(CartesianState(position, velocity), originAcceleration = acceleration)
    }

    /** Phase at an absolute tick, also used when rebasing a prescribed orbit after a mass change. */
    fun meanAnomaly(tick: Long): Double {
        require(tick >= 0) { "Simulation tick must be nonnegative" }
        val elapsed = (tick - epochTick).toDouble() / 20.0
        return wrap(meanAnomalyAtEpoch + (elapsed % period) / period * (2.0 * PI))
    }

    /** A closed parent-relative geometric ellipse; callers join the last point to the first. */
    fun curve(samples: Int = 128): List<Vector3> {
        require(samples in 16..256) { "Expected 16..256 orbit samples" }
        return List(samples) { index ->
            val angle = 2.0 * PI * index / samples
            plane.apply(Vector3(semiMajorAxis * (cos(angle) - eccentricity), semiMajorAxis * minorRatio * sin(angle), 0.0))
        }
    }

    // Newton within a monotonic bracket; bisection makes iteration bounded even near e=1.
    private fun eccentricAnomaly(mean: Double): Double {
        if (mean == 0.0 || eccentricity == 0.0) return mean
        var lower = -PI
        var upper = PI
        var estimate = mean
        repeat(80) {
            val residual = estimate - eccentricity * sin(estimate) - mean
            if (residual == 0.0) return estimate
            if (residual < 0.0) lower = estimate else upper = estimate
            val newton = estimate - residual / (1.0 - eccentricity * cos(estimate))
            val next = if (newton > lower && newton < upper) newton else lower + (upper - lower) * 0.5
            if (next == estimate) return estimate
            estimate = next
        }
        return estimate
    }

    private fun wrap(angle: Double): Double {
        val wrapped = angle % (2.0 * PI)
        return when {
            wrapped > PI -> wrapped - 2.0 * PI
            wrapped < -PI -> wrapped + 2.0 * PI
            else -> wrapped
        }
    }
}
