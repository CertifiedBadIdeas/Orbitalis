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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Bounded forecast of a construction with all non-gravitational forces omitted. */
object CoastPrediction {
    enum class End { HORIZON, SURFACE_ENTRY, BODY_INTERSECTION, RANGE, ORBIT, REVOLUTION }

    data class Point(
        val seconds: Double,
        val state: CartesianState,
    )

    data class Result(
        val points: List<Point>,
        val end: End,
    ) {
        val terminal: Point get() = points.last()
    }

    data class Limits(
        val horizon: Double = 120.0,
        val step: Double = 0.05,
        val sampleInterval: Double = 0.5,
        val range: Double = 30000.0,
        val hullRadius: Double = 0.0,
        val entryHeight: Double = 464.0,
        val footprintHalfSize: Double = 1000.0,
        val detectEntry: Boolean = false,
        val entryInitiallyArmed: Boolean = true,
        val entryArmHeight: Double = 664.0,
        val stopAfterRevolution: Boolean = false,
    ) {
        init {
            require(horizon.isFinite() && horizon > 0.0 && horizon <= 3600.0)
            require(step.isFinite() && step in 0.005..0.5 && ceil(horizon / step) <= 12000)
            require(sampleInterval.isFinite() && sampleInterval >= step && ceil(horizon / sampleInterval) <= 600)
            require(range.isFinite() && range in 100.0..100000.0)
            require(hullRadius.isFinite() && hullRadius in 0.0..128.0)
            require(entryArmHeight.isFinite() && entryArmHeight >= entryHeight)
            require(entryHeight.isFinite() && footprintHalfSize.isFinite() && footprintHalfSize > 0.0)
        }
    }

    /** Follow the real field for a full turn, with a bounded far-field period estimate for budgeting. */
    fun orbit(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        limits: Limits = Limits(),
    ): Result {
        val energy = field.specificEnergy(initial)
        val period = if (energy < 0.0) 2 * PI * field.body.mu / (-2 * energy).pow(1.5) else 120.0
        val horizon = max(120.0, period * 1.5).coerceAtMost(3600.0)
        return predict(
            field,
            initial,
            limits.copy(
                horizon = horizon,
                step = max(limits.step, horizon / 11999.0),
                sampleInterval = max(limits.sampleInterval, horizon / 599.0),
                stopAfterRevolution = true,
            ),
        )
    }

    /** No game objects, background thread safe. Interruption cancels without publishing a partial path. */
    fun predict(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        limits: Limits = Limits(),
    ): Result {
        require(limits.entryHeight >= field.body.halfHeight)
        require(initial.position.length() <= 100000.0 && initial.velocity.length() <= 10000.0)
        val points = mutableListOf(Point(0.0, initial))

        fun append(point: Point) {
            if (points.last().seconds == point.seconds) points[points.lastIndex] = point else points.add(point)
        }
        val extent =
            Vector3(
                field.body.halfWidth + limits.hullRadius,
                field.body.halfHeight + limits.hullRadius,
                field.body.halfDepth + limits.hullRadius,
            )

        fun inside(p: Vector3) = abs(p.x) <= extent.x && abs(p.y) <= extent.y && abs(p.z) <= extent.z
        if (inside(initial.position)) return Result(points, End.BODY_INTERSECTION)
        if (initial.position.length() >= limits.range) return Result(points, End.RANGE)
        var entryArmed = limits.entryInitiallyArmed || initial.position.y >= limits.entryArmHeight
        if (limits.detectEntry && entryArmed && entry(initial.position, initial.velocity, field.body.halfHeight, limits)) {
            return Result(points, End.SURFACE_ENTRY)
        }
        val momentum = initial.position.cross(initial.velocity)
        val axis = if (limits.stopAfterRevolution && momentum.length() > 1e-6) momentum.normalized() else null
        var winding = 0.0
        var state = initial
        var time = 0.0
        var nextSample = limits.sampleInterval
        var acceleration = field.acceleration(state.position)
        while (time < limits.horizon) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Coast prediction cancelled")
            val dt = min(limits.step, limits.horizon - time)
            val half = state.velocity + acceleration * (dt * 0.5)
            val position = state.position + half * dt
            val nextAcceleration = field.acceleration(position)
            val next = CartesianState(position, half + nextAcceleration * (dt * 0.5))
            entryArmed = entryArmed || position.y >= limits.entryArmHeight
            val bodyHit = intersection(state.position, next.position, extent)
            val entryHit = if (limits.detectEntry && entryArmed) entryFraction(state, next, field.body.halfHeight, limits) else null
            val event =
                when {
                    bodyHit != null && (entryHit == null || bodyHit <= entryHit) -> bodyHit to End.BODY_INTERSECTION
                    entryHit != null -> entryHit to End.SURFACE_ENTRY
                    else -> null
                }
            if (event != null) {
                val fraction = event.first
                append(
                    Point(
                        time + dt * fraction,
                        CartesianState(
                            state.position + (next.position - state.position) * fraction,
                            state.velocity + (next.velocity - state.velocity) * fraction,
                        ),
                    ),
                )
                return Result(points.toList(), event.second)
            }
            if (axis != null) {
                val a = state.position - axis * state.position.dot(axis)
                val b = next.position - axis * next.position.dot(axis)
                val turn = atan2(axis.dot(a.cross(b)), a.dot(b))
                if (turn > 0 && winding + turn >= 2 * PI) {
                    val fraction = ((2 * PI - winding) / turn).coerceIn(0.0, 1.0)
                    val final =
                        CartesianState(
                            state.position + (next.position - state.position) * fraction,
                            state.velocity + (next.velocity - state.velocity) * fraction,
                        )
                    append(Point(time + dt * fraction, final))
                    val closed =
                        (final.position - initial.position).length() <= max(2.0, initial.position.length() * 0.005) &&
                            (final.velocity - initial.velocity).length() <= max(0.1, initial.velocity.length() * 0.01)
                    return Result(points.toList(), if (closed) End.ORBIT else End.REVOLUTION)
                }
                winding += turn
            }
            time = min(limits.horizon, time + dt)
            state = next
            acceleration = nextAcceleration
            if (position.length() >= limits.range) {
                append(Point(time, state))
                return Result(points.toList(), End.RANGE)
            }
            if (time >= nextSample || time >= limits.horizon) {
                append(Point(time, state))
                nextSample += limits.sampleInterval
            }
        }
        return Result(points.toList(), End.HORIZON)
    }

    private fun entry(
        p: Vector3,
        v: Vector3,
        bottom: Double,
        limits: Limits,
    ): Boolean =
        p.y in bottom..limits.entryHeight && v.y <= 0.0 &&
            abs(p.x) < limits.footprintHalfSize && abs(p.z) < limits.footprintHalfSize

    /** Includes lateral entry into the footprint while descending below its top plane. */
    private fun entryFraction(
        a: CartesianState,
        b: CartesianState,
        bottom: Double,
        limits: Limits,
    ): Double? {
        if (b.velocity.y > 0.0 || b.position.y > a.position.y) return null
        val offset = Vector3(0.0, (bottom + limits.entryHeight) * 0.5, 0.0)
        val extent = Vector3(limits.footprintHalfSize, (limits.entryHeight - bottom) * 0.5, limits.footprintHalfSize)
        val fraction = intersection(a.position - offset, b.position - offset, extent) ?: return null
        // The native footprint is open at its outer border. Ignore tangent-only contact.
        val beyond = a.position + (b.position - a.position) * min(1.0, fraction + 1e-7)
        if (abs(beyond.x) >= limits.footprintHalfSize || abs(beyond.z) >= limits.footprintHalfSize) return null
        return fraction
    }

    /** Slab clipping detects a complete traversal between integration samples, including diagonal edges. */
    private fun intersection(
        a: Vector3,
        b: Vector3,
        extent: Vector3,
    ): Double? {
        var enter = 0.0
        var leave = 1.0
        for ((start, delta, half) in listOf(
            Triple(a.x, b.x - a.x, extent.x),
            Triple(a.y, b.y - a.y, extent.y),
            Triple(a.z, b.z - a.z, extent.z),
        )) {
            if (delta == 0.0) {
                if (abs(start) > half) return null
            } else {
                val first = (-half - start) / delta
                val second = (half - start) / delta
                enter = max(enter, min(first, second))
                leave = min(leave, max(first, second))
                if (enter > leave) return null
            }
        }
        return enter
    }
}
