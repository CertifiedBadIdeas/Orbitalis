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

package org.cbi.orbitalis.consumer.flight

import org.cbi.orbitalis.core.orbit.CelestialDynamics
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.max

/** Accept a real low ballistic revolution instead of chasing a time-parametrized reference point. */
internal object RocketOrbitCapture {
    fun safe(
        field: SurfaceGravityLayer,
        state: CartesianState,
        course: RocketCourse,
        sources: CelestialDynamics? = null,
    ): Boolean {
        val root = sources?.profile?.earthSurfaceFrame(sources.epochTick)?.toParent(state) ?: state
        if (root.velocity.dot(root.velocity) * 0.5 + field.potential(state.position) >= 0.0) return false
        val prediction =
            CoastPrediction.orbit(
                field,
                root,
                CoastPrediction.Limits(range = max(10000.0, field.body.halfWidth * 4.0), hullRadius = 32.0, detectEntry = false),
                sources,
            )
        if (prediction.end !in setOf(CoastPrediction.End.ORBIT, CoastPrediction.End.REVOLUTION)) return false
        var referenceRadius = 0.0
        var t = 0.0
        while (t < course.orbit.period) {
            referenceRadius = max(referenceRadius, course.stateAt(t).position.length())
            t += 1.0
        }
        return prediction.points.all { point ->
            val p =
                sources
                    ?.profile
                    ?.earthSurfaceFrame(sources.epochTick, point.seconds)
                    ?.orientation
                    ?.inverse(point.state.position)
                    ?: point.state.position
            val clearance =
                Vector3(
                    max(0.0, abs(p.x) - field.body.halfWidth),
                    max(0.0, abs(p.y) - field.body.halfHeight),
                    max(
                        0.0,
                        abs(p.z) - field.body.halfDepth,
                    ),
                ).length()
            clearance >= 32.0 && p.length() <= max(referenceRadius * 1.15, field.body.halfWidth * 3.5)
        }
    }
}
