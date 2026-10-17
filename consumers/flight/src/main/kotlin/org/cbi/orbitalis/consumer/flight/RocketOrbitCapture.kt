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

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/** Accept a real low ballistic revolution instead of chasing a time-parametrized reference point. */
internal object RocketOrbitCapture {
    fun safe(
        field: SurfaceGravityLayer,
        state: CartesianState,
        course: RocketCourse,
    ): Boolean {
        if (field.specificEnergy(state) >= 0.0) return false
        val horizon = (course.orbit.period * 1.25).coerceAtMost(300.0)
        val prediction =
            CoastPrediction.predict(
                field,
                state,
                CoastPrediction.Limits(
                    horizon = horizon,
                    sampleInterval = max(0.5, horizon / 599.0),
                    range = max(10000.0, field.body.halfWidth * 4.0),
                    hullRadius = 12.0,
                ),
            )
        if (prediction.end != CoastPrediction.End.HORIZON) return false
        var referenceRadius = 0.0
        var t = 0.0
        while (t < course.orbit.period) {
            referenceRadius = max(referenceRadius, course.stateAt(t).position.length())
            t += 1.0
        }
        var previous = atan2(state.position.dot(course.axis), state.position.y)
        var sweep = 0.0
        for (point in prediction.points) {
            val p = point.state.position
            val clearance =
                Vector3(
                    max(0.0, abs(p.x) - field.body.halfWidth),
                    max(0.0, abs(p.y) - field.body.halfHeight),
                    max(0.0, abs(p.z) - field.body.halfDepth),
                ).length()
            if (clearance < 64.0 || p.length() > referenceRadius * 1.15) return false
            val angle = atan2(p.dot(course.axis), p.y)
            var delta = angle - previous
            if (delta < -PI) delta += 2.0 * PI
            if (delta > PI) delta -= 2.0 * PI
            sweep += delta
            previous = angle
        }
        return sweep >= 2.0 * PI
    }
}
