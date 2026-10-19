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

import org.cbi.orbitalis.core.planet.BallisticOrbit
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.acos

/** Only square-body symmetries are used: arbitrary rotations would change near-field gravity. */
internal class RocketCourse(
    val orbit: BallisticOrbit,
    val axis: Vector3,
    private val alreadyOriented: Boolean = false,
) {
    init {
        require(axis in listOf(Vector3(1.0, 0.0, 0.0), Vector3(-1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0), Vector3(0.0, 0.0, -1.0)))
    }

    fun stateAt(time: Double): CartesianState {
        val state = orbit.stateAt(time)
        if (alreadyOriented) return state
        val across = axis.cross(Vector3(0.0, 1.0, 0.0))

        fun rotate(v: Vector3) = axis * v.x + Vector3(0.0, v.y, 0.0) + across * v.z
        return CartesianState(rotate(state.position), rotate(state.velocity))
    }

    companion object {
        fun launchAxis(position: Vector3): Vector3 =
            if (abs(position.x) >= abs(position.z)) {
                Vector3(if (position.x > 0.0) -1.0 else 1.0, 0.0, 0.0)
            } else {
                Vector3(0.0, 0.0, if (position.z > 0.0) -1.0 else 1.0)
            }
    }
}

/** Smooth feed-forward reference only. Native engines must supply every requested acceleration. */
internal class RocketConnection(
    initial: CartesianState,
    final: CartesianState,
    initialAcceleration: Vector3,
    finalAcceleration: Vector3,
    val duration: Double,
) {
    data class Sample(
        val state: CartesianState,
        val acceleration: Vector3,
    )

    private val coefficients: List<Vector3>

    init {
        require(duration.isFinite() && duration > 0.0)
        val t = duration
        val p = final.position - initial.position - initial.velocity * t - initialAcceleration * (t * t * 0.5)
        val v = (final.velocity - initial.velocity - initialAcceleration * t) * t
        val a = (finalAcceleration - initialAcceleration) * (t * t)
        coefficients =
            listOf(
                initial.position,
                initial.velocity * t,
                initialAcceleration * (t * t * 0.5),
                p * 10.0 - v * 4.0 + a * 0.5,
                p * -15.0 + v * 7.0 - a,
                p * 6.0 - v * 3.0 + a * 0.5,
            )
    }

    fun at(time: Double): Sample {
        val u = (time / duration).coerceIn(0.0, 1.0)
        var p = Vector3.ZERO
        var v = Vector3.ZERO
        var a = Vector3.ZERO
        for (i in coefficients.indices.reversed()) p = p * u + coefficients[i]
        for (i in (1..5).reversed()) v = v * u + coefficients[i] * i.toDouble()
        for (i in (2..5).reversed()) a = a * u + coefficients[i] * (i * (i - 1)).toDouble()
        return Sample(CartesianState(p, v * (1.0 / duration)), a * (1.0 / (duration * duration)))
    }
}

internal data class RocketAscent(
    val path: RocketConnection,
    val orbitPhase: Double,
) {
    companion object {
        fun initialAcceleration(course: RocketCourse): Vector3 = course.axis + Vector3(0.0, 3.0, 0.0)

        /** Call on a worker with immutable native state/field copies. */
        fun plan(
            field: SurfaceGravityLayer,
            course: RocketCourse,
            initial: CartesianState,
            maximumThrust: Double,
            acceleration: (CartesianState, Double) -> Vector3 = { state, _ -> field.acceleration(state.position) },
            safeOrbit: (CartesianState, Double) -> Boolean = { _, _ -> true },
        ): RocketAscent {
            require(maximumThrust.isFinite() && maximumThrust >= 25.0)
            for (duration in 24..(96.0 * kotlin.math.sqrt(field.body.halfWidth / 1000.0)).toInt() step 4) {
                for (phaseFraction in listOf(0.0, 0.02, 0.04, 0.06, 0.08, 0.10, 0.12, 0.14, 0.16, 0.18, 0.20)) {
                    val phase = course.orbit.period * phaseFraction
                    val target = course.stateAt(phase)
                    val crown = course.orbit.initial.position.y - field.body.halfHeight
                    // Join in the weak exterior field, not on a steep low-altitude descending arc.
                    if (target.position.y < field.body.halfHeight + maxOf(field.fullHeight + 100.0, crown * 0.8)) continue
                    val path =
                        RocketConnection(
                            initial,
                            target,
                            initialAcceleration(course),
                            acceleration(target, duration.toDouble()),
                            duration.toDouble(),
                        )
                    var safe = true
                    var space = initial.position.y >= field.body.halfHeight + field.fullHeight
                    var previous: Vector3? = null
                    var t = 0.0
                    while (t <= duration && safe) {
                        val sample = path.at(t)
                        val p = sample.state.position
                        val thrust = sample.acceleration - acceleration(sample.state, t)
                        if (p.y >= field.body.halfHeight + field.fullHeight) space = true
                        safe = p.y >= initial.position.y - 0.1 && p.y <= course.orbit.initial.position.y + 150.0 &&
                            sample.state.velocity.length() < 180.0 * kotlin.math.sqrt(field.body.halfWidth / 1000.0) &&
                            thrust.length() <= minOf(maximumThrust * 0.6, 25.0) &&
                            (space || (abs(p.x) < field.body.halfWidth - 20.0 && abs(p.z) < field.body.halfDepth - 20.0))
                        val old = previous
                        if (old != null && old.length() > 1.0 && thrust.length() > 1.0) {
                            safe = safe && acos(old.normalized().dot(thrust.normalized()).coerceIn(-1.0, 1.0)) / 0.25 < 0.12
                        }
                        if (thrust.length() > 1.0) previous = thrust else previous = null
                        t += 0.25
                    }
                    if (safe && safeOrbit(target, duration.toDouble()) && path
                            .at(5.0)
                            .state.velocity
                            .dot(course.axis) > 0.5
                    ) {
                        return RocketAscent(path, phase)
                    }
                }
            }
            error("No bounded direct-ascent trajectory for launch ${initial.position} and engine authority $maximumThrust")
        }
    }
}
