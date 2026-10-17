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
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.acos

internal object RocketReturn {
    class Path(
        val braking: RocketConnection,
        val landing: RocketConnection,
    ) {
        val duration get() = braking.duration + landing.duration

        fun at(time: Double) = if (time <= braking.duration) braking.at(time) else landing.at(time - braking.duration)
    }

    fun plan(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        home: Vector3,
        authority: Double,
    ): Path {
        // Join the vertical approach while still moving. Both curves share position, velocity and
        // acceleration; there is no waypoint corner or stop above the landing pad.
        val approach =
            CartesianState(
                Vector3(home.x, maxOf(field.body.halfHeight + field.fullHeight + 100.0, home.y + 160.0), home.z),
                Vector3(0.0, -10.0, 0.0),
            )
        // Slower vertical descent can keep the changing near-field force within the slew budget.
        for (landingStretch in listOf(1.0, 1.5)) {
            val landing =
                RocketConnection(
                    approach,
                    CartesianState(home + Vector3(0.0, 0.6, 0.0), Vector3(0.0, -0.2, 0.0)),
                    Vector3.ZERO,
                    Vector3.ZERO,
                    maxOf(28.0, (approach.position.y - home.y) / 15.0) * landingStretch,
                )
            // Try stronger initial braking if a gentle retrograde burn needs an untrackable turn.
            // Prefer the gentler curve; fallback still reserves 40% of the powered 0.25 rad/s rate.
            for (maximumSlew in listOf(0.12, 0.15)) {
                for (retrograde in listOf(2.0, 4.0, 8.0, 16.0)) {
                    for (duration in 28..(100.0 * kotlin.math.sqrt(field.body.halfWidth / 1000.0)).toInt() step 4) {
                        val braking =
                            RocketConnection(
                                initial,
                                approach,
                                field.acceleration(initial.position) - initial.velocity.normalized() * retrograde,
                                Vector3.ZERO,
                                duration.toDouble(),
                            )
                        val path = Path(braking, landing)
                        var previous: Vector3? = null
                        var safe = true
                        var t = 0.0
                        while (t <= path.duration && safe) {
                            val sample = path.at(t)
                            val p = sample.state.position
                            val thrust = sample.acceleration - field.acceleration(p)
                            val horizontal = Vector3(p.x - home.x, 0.0, p.z - home.z).length()
                            safe = p.y >= home.y && p.y < maxOf(field.body.halfHeight + field.body.halfWidth, initial.position.y) + 200.0 &&
                                sample.state.velocity.length() < 180.0 * kotlin.math.sqrt(field.body.halfWidth / 1000.0) &&
                                thrust.length() <= minOf(authority * 0.6, 25.0) &&
                                (
                                    p.y >= field.body.halfHeight + field.fullHeight ||
                                        (abs(p.x) < field.body.halfWidth - 20.0 && abs(p.z) < field.body.halfDepth - 20.0)
                                ) &&
                                (p.y >= home.y + 120.0 || horizontal < 30.0)
                            val old = previous
                            if (old != null && old.length() > 1.0 && thrust.length() > 1.0) {
                                safe = safe && acos(old.normalized().dot(thrust.normalized()).coerceIn(-1.0, 1.0)) / 0.25 < maximumSlew
                            }
                            previous = if (thrust.length() > 1.0) thrust else null
                            t += 0.25
                        }
                        if (safe &&
                            (path.at(2.0).acceleration - field.acceleration(path.at(2.0).state.position)).dot(initial.velocity) < 0.0
                        ) {
                            return path
                        }
                    }
                }
            }
        }
        error("No bounded retrograde return to $home from ${initial.position}")
    }
}
