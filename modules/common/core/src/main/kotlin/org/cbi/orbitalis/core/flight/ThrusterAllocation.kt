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

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.max

/** Bounded nonnegative least squares over native engine wrenches in body axes. */
object ThrusterAllocation {
    data class Actuator(
        val force: Vector3,
        val torque: Vector3,
    )

    data class Result(
        val throttle: List<Double>,
        val forceResidual: Vector3,
        val torqueResidual: Vector3,
    )

    private fun row(
        force: Vector3,
        torque: Vector3,
        lever: Double,
    ) = doubleArrayOf(
        force.x,
        force.y,
        force.z,
        torque.x / lever,
        torque.y / lever,
        torque.z / lever,
    )

    fun solve(
        actuators: List<Actuator>,
        force: Vector3,
        torque: Vector3,
        lever: Double,
    ): Result {
        require(actuators.isNotEmpty() && actuators.size <= 64 && lever.isFinite() && lever > 0) {
            "Invalid actuator count or lever length"
        }
        val columns = actuators.map { row(it.force, it.torque, lever) }
        val norms = columns.map { c -> c.sumOf { it * it } }
        require(norms.all { it.isFinite() }) { "Invalid actuator wrench" }
        val regularization = norms.max() * 1e-10
        val residual = row(force, torque, lever)
        val throttle = DoubleArray(actuators.size)
        for (iteration in 0 until 256) {
            var largestChange = 0.0
            for (i in columns.indices) {
                if (norms[i] == 0.0) continue
                val column = columns[i]
                var dot = 0.0
                for (j in 0 until 6) dot += column[j] * residual[j]
                val next =
                    (
                        throttle[i] + (dot - regularization * throttle[i]) /
                            (norms[i] + regularization)
                    ).coerceIn(0.0, 1.0)
                val change = next - throttle[i]
                throttle[i] = next
                for (j in 0 until 6) residual[j] -= column[j] * change
                largestChange = max(largestChange, abs(change))
            }
            if (largestChange < 1e-8) break
        }
        var actualForce = Vector3.ZERO
        var actualTorque = Vector3.ZERO
        for (i in actuators.indices) {
            actualForce += actuators[i].force * throttle[i]
            actualTorque += actuators[i].torque * throttle[i]
        }
        return Result(throttle.toList(), force - actualForce, torque - actualTorque)
    }
}
