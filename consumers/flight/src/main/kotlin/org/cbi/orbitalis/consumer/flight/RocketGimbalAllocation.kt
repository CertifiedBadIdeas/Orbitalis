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

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.max
import kotlin.math.sqrt

/** Physical force requests for bottom-mounted UP-facing vector engines; no live body mutation. */
internal object RocketGimbalAllocation {
    private const val TANGENT = 0.5773502691896257 // Native Propulsion 1.1.5: 30 degrees per axis.
    private val directions =
        listOf(
            Vector3(0.0, 1.0, 0.0),
            Vector3(-TANGENT, 1.0, -TANGENT).normalized(),
            Vector3(-TANGENT, 1.0, TANGENT).normalized(),
            Vector3(TANGENT, 1.0, -TANGENT).normalized(),
            Vector3(TANGENT, 1.0, TANGENT).normalized(),
        )

    data class Engine(
        val arm: Vector3,
        val capacity: Double,
    )

    data class Input(
        val throttle: Double,
        val vectorX: Double,
        val vectorY: Double,
        val force: Vector3,
    )

    data class Result(
        val inputs: List<Input>,
        val forceResidual: Vector3,
        val torqueResidual: Vector3,
    )

    fun solve(
        engines: List<Engine>,
        force: Vector3,
        torque: Vector3,
    ): Result {
        require(engines.isNotEmpty() && engines.size <= 16)
        require(engines.all { it.capacity.isFinite() && it.capacity > 0.0 })
        val lever = max(1.0, engines.maxOf { it.arm.length() })
        val scale = engines.maxOf { it.capacity }

        fun row(
            f: Vector3,
            t: Vector3,
        ) = doubleArrayOf(f.x, f.y, f.z, t.x / lever, t.y / lever, t.z / lever)
        val columns =
            engines.flatMap { engine ->
                directions.map { direction ->
                    val f = direction * (engine.capacity / scale)
                    row(f, engine.arm.cross(f))
                }
            }
        val target = row(force * (1.0 / scale), torque * (1.0 / scale))
        val lipschitz = columns.sumOf { column -> column.sumOf { it * it } }
        var weights = DoubleArray(columns.size)
        var extrapolated = weights.copyOf()
        var momentum = 1.0
        repeat(400) {
            val residual = target.copyOf()
            for (i in columns.indices) for (j in 0..5) residual[j] -= columns[i][j] * extrapolated[i]
            val next =
                DoubleArray(weights.size) { i ->
                    extrapolated[i] + (0..5).sumOf { columns[i][it] * residual[it] } / lipschitz
                }
            // Each engine shares one throttle budget across its cone rays. Treating the rays as
            // independent engines would invent force beyond native maximum thrust.
            for (engine in engines.indices) projectBudget(next, engine * directions.size, directions.size)
            val nextMomentum = (1.0 + sqrt(1.0 + 4.0 * momentum * momentum)) * 0.5
            extrapolated = DoubleArray(weights.size) { i -> next[i] + (momentum - 1.0) / nextMomentum * (next[i] - weights[i]) }
            weights = next
            momentum = nextMomentum
        }
        val inputs =
            engines.mapIndexed { i, engine ->
                var f = Vector3.ZERO
                for (j in directions.indices) f += directions[j] * (weights[i * directions.size + j] * engine.capacity)
                val throttle = (f.length() / engine.capacity).coerceIn(0.0, 1.0)
                // Native UP-facing engine: right=-X and up=-Z in its exhaust basis; force opposes exhaust.
                val x = if (f.y > 1e-10) (f.x / (f.y * TANGENT)).coerceIn(-1.0, 1.0) else 0.0
                val y = if (f.y > 1e-10) (f.z / (f.y * TANGENT)).coerceIn(-1.0, 1.0) else 0.0
                Input(throttle, x, y, f)
            }
        var actualForce = Vector3.ZERO
        var actualTorque = Vector3.ZERO
        inputs.forEachIndexed { i, input ->
            actualForce += input.force
            actualTorque += engines[i].arm.cross(input.force)
        }
        return Result(inputs, force - actualForce, torque - actualTorque)
    }

    private fun projectBudget(
        values: DoubleArray,
        start: Int,
        count: Int,
    ) {
        val positive = (start until start + count).map { max(0.0, values[it]) }
        if (positive.sum() <= 1.0) {
            positive.forEachIndexed { i, value -> values[start + i] = value }
            return
        }
        val sorted = positive.sortedDescending()
        var sum = 0.0
        var threshold = 0.0
        for (i in sorted.indices) {
            sum += sorted[i]
            val candidate = (sum - 1.0) / (i + 1)
            if (sorted[i] > candidate) threshold = candidate
        }
        for (i in 0 until count) values[start + i] = max(0.0, positive[i] - threshold)
    }
}
