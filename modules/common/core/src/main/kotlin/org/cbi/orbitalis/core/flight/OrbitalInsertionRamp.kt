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

package org.cbi.orbitalis.core.flight

/** Quintic trajectory time scaling with a bounded speed-ramp acceleration budget. */
class OrbitalInsertionRamp(
    maximumReferenceSpeed: Double,
) {
    val accelerationBudget = OrbitalGuidance.MAX_ACCELERATION * 0.5
    val duration: Double

    init {
        require(maximumReferenceSpeed.isFinite() && maximumReferenceSpeed >= 0.0)
        // The maximum derivative of quintic smoothstep is exactly 15/8.
        duration = maxOf(40.0, 1.875 * maximumReferenceSpeed / accelerationBudget)
    }

    data class Sample(
        val rate: Double,
        val derivative: Double,
        val elapsed: Double,
    )

    fun sample(seconds: Double): Sample {
        require(seconds.isFinite() && seconds >= 0.0)
        val u = (seconds / duration).coerceIn(0.0, 1.0)
        val rate = u * u * u * (10.0 + u * (-15.0 + 6.0 * u))
        val derivative = if (seconds < duration) 30.0 * u * u * (1.0 - u) * (1.0 - u) / duration else 0.0
        val elapsed = duration * (2.5 * u * u * u * u - 3.0 * u * u * u * u * u + u * u * u * u * u * u) + maxOf(0.0, seconds - duration)
        return Sample(rate, derivative, elapsed)
    }
}
