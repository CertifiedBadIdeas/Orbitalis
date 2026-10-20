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

import org.cbi.orbitalis.core.orbit.Rotation3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** Finite emitting cube in the same Cartesian chart as the observer and opaque bodies.
 * Orientation should use [SolarShape.orientation] transformed into that chart.
 * Distant-source quadrature ignores irradiance differences across one stellar face.
 */
data class SolarEmitterCube(
    val centre: Vector3,
    val halfEdge: Double,
    val orientation: Rotation3 = Rotation3.IDENTITY,
) {
    init {
        require(halfEdge.isFinite() && halfEdge > 0.0)
    }

    /** Equal projected-area samples on the visible source faces, mirrored by the surface shader. */
    fun sample(
        point: Vector3,
        index: Int,
    ): Vector3 {
        require(index in 0 until SolarOcclusion.SAMPLE_COUNT)
        val toward = orientation.inverse(point - centre)
        val x = abs(toward.x)
        val y = abs(toward.y)
        val z = abs(toward.z)
        val total = x + y + z
        require(total > 0.0)
        val cursor = (index + 0.5) / SolarOcclusion.SAMPLE_COUNT * total
        val v = 2.0 * ((index + 0.5) * SolarOcclusion.SAMPLE_STRIDE - floor((index + 0.5) * SolarOcclusion.SAMPLE_STRIDE)) - 1.0
        val face =
            if (cursor < x) {
                Vector3(sign(toward.x), 2.0 * cursor / x - 1.0, v)
            } else if (cursor < x + y) {
                Vector3(v, sign(toward.y), 2.0 * (cursor - x) / y - 1.0)
            } else {
                Vector3(2.0 * (cursor - x - y) / z - 1.0, v, sign(toward.z))
            }
        return centre + orientation.apply(face * halfEdge)
    }
}

/** Oriented solid box; dimensions are half extents in its local axes. */
data class OpaqueSolarBox(
    val centre: Vector3,
    val halfSize: Vector3,
    val orientation: Rotation3 = Rotation3.IDENTITY,
) {
    init {
        require(halfSize.x > 0.0 && halfSize.y > 0.0 && halfSize.z > 0.0)
    }

    internal val radius = halfSize.length()

    internal fun contains(point: Vector3): Boolean {
        val local = orientation.inverse(point - centre)
        return abs(local.x) <= halfSize.x && abs(local.y) <= halfSize.y && abs(local.z) <= halfSize.z
    }

    internal fun intersects(
        start: Vector3,
        end: Vector3,
    ): Boolean {
        val local = orientation.inverse(start - centre)
        val delta = orientation.inverse(end - start)
        var near = 0.0
        var far = 1.0
        for (axis in 0..2) {
            val origin =
                when (axis) {
                    0 -> local.x
                    1 -> local.y
                    else -> local.z
                }
            val direction =
                when (axis) {
                    0 -> delta.x
                    1 -> delta.y
                    else -> delta.z
                }
            val half =
                when (axis) {
                    0 -> halfSize.x
                    1 -> halfSize.y
                    else -> halfSize.z
                }
            if (direction == 0.0) {
                if (origin < -half || origin > half) return false
            } else {
                val a = (-half - origin) / direction
                val b = (half - origin) / direction
                near = max(near, min(a, b))
                far = min(far, max(a, b))
                if (far < near) return false
            }
        }
        return far > 0.0 && near < 1.0
    }
}

/** Union of opaque silhouettes, with a fixed 32-ray budget per observer and candidate body.
 * The body list is copied on construction; callers can reuse this immutable scene across observers.
 * Atmospheric scattering, block geometry and multiple scattering are outside this model.
 */
class SolarVisibility(
    val emitter: SolarEmitterCube,
    occluders: List<OpaqueSolarBox>,
) {
    private val bodies = occluders.toList()

    fun visibility(point: Vector3): Double {
        val candidates = candidates(point)
        if (candidates.isEmpty()) return 1.0
        if (candidates.any { it.contains(point) }) return 0.0
        var visible = 0
        for (index in 0 until SolarOcclusion.SAMPLE_COUNT) {
            val sample = emitter.sample(point, index)
            if (candidates.none { it.intersects(point, sample) }) visible++
        }
        return visible.toDouble() / SolarOcclusion.SAMPLE_COUNT
    }

    /** Conservative enclosing spheres bound source depth and its tangent cone. No angular
     * approximation may discard an actual source ray, including a grazing large opaque body.
     */
    internal fun candidates(point: Vector3): List<OpaqueSolarBox> {
        val toward = emitter.centre - point
        val distance = toward.length()
        val sourceRadius = emitter.halfEdge * sqrt(3.0)
        if (distance <= sourceRadius) return bodies
        val axis = toward * (1.0 / distance)
        val slope = sourceRadius / sqrt((distance - sourceRadius) * (distance + sourceRadius))
        return bodies.filter { body ->
            val offset = body.centre - point
            val depth = offset.dot(axis)
            if (depth < -body.radius || depth > distance + sourceRadius + body.radius) {
                false
            } else {
                val perpendicular = (offset - axis * depth).length()
                perpendicular <= body.radius + max(0.0, depth + body.radius) * slope
            }
        }
    }
}
