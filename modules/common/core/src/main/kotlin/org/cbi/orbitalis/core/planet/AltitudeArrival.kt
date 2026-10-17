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

package org.cbi.orbitalis.core.planet

import kotlin.math.abs
import kotlin.math.sqrt

/** Earliest directed arrival at a height band under a bounded constant-net-acceleration forecast. */
object AltitudeArrival {
    fun seconds(
        height: Double,
        velocity: Double,
        acceleration: Double,
        boundary: Double,
        ascending: Boolean,
        horizon: Double,
    ): Double? {
        require(listOf(height, velocity, acceleration, boundary, horizon).all { it.isFinite() } && horizon >= 0.0)
        val direction = if (ascending) 1.0 else -1.0

        fun inside(t: Double): Boolean = (height + velocity * t + acceleration * t * t * 0.5 - boundary) * direction >= -1e-8
        if (inside(0.0) && velocity * direction >= 0.0) return 0.0
        val candidates = mutableListOf<Double>()
        if (abs(acceleration) < 1e-8) {
            if (velocity * direction > 0.0) candidates.add((boundary - height) / velocity)
        } else {
            val discriminant = velocity * velocity - 2.0 * acceleration * (height - boundary)
            if (discriminant >= 0.0) {
                val root = sqrt(discriminant)
                // Stable quadratic roots, including a launch at zero velocity.
                val q = -0.5 * (velocity + Math.copySign(root, velocity))
                candidates.add(q / (acceleration * 0.5))
                if (q != 0.0) candidates.add((height - boundary) / q)
            }
            candidates.add(-velocity / acceleration)
        }
        return candidates
            .filter {
                it.isFinite() && it in 0.0..horizon && inside(it) &&
                    (velocity + acceleration * it) * direction >= -1e-8
            }.minOrNull()
    }
}
