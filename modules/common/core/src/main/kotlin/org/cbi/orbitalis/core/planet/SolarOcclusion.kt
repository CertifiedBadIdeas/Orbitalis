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

/** Projected-area quadrature of a luminous cube against an opaque box in the box's local chart.
 * The visible source faces are weighted by projected area; this is not a point light.
 * The distant-source approximation ignores irradiance differences across one stellar face.
 */
class SolarOcclusion(
    val sun: Vector3,
    val halfEdge: Double,
    val orientation: Rotation3,
    val halfSize: Vector3,
) {
    init {
        require(halfEdge.isFinite() && halfEdge > 0.0)
        require(halfSize.x > 0.0 && halfSize.y > 0.0 && halfSize.z > 0.0)
    }

    /** Fraction of direct solar light reaching [point]; atmospheric refraction is not modelled. */
    fun visibility(point: Vector3): Double =
        (0 until SAMPLE_COUNT).count { !intersects(point, sample(point, it)) }.toDouble() / SAMPLE_COUNT

    /** Equal projected-area samples on the visible source faces, mirrored by the surface shader. */
    fun sample(
        point: Vector3,
        index: Int,
    ): Vector3 {
        require(index in 0 until SAMPLE_COUNT)
        val toward = orientation.inverse(point - sun)
        val x = abs(toward.x)
        val y = abs(toward.y)
        val z = abs(toward.z)
        val total = x + y + z
        require(total > 0.0)
        val cursor = (index + 0.5) / SAMPLE_COUNT * total
        val v = 2.0 * ((index + 0.5) * SAMPLE_STRIDE - floor((index + 0.5) * SAMPLE_STRIDE)) - 1.0
        val face =
            if (cursor < x) {
                Vector3(sign(toward.x), 2.0 * cursor / x - 1.0, v)
            } else if (cursor < x + y) {
                Vector3(v, sign(toward.y), 2.0 * (cursor - x) / y - 1.0)
            } else {
                Vector3(2.0 * (cursor - x - y) / z - 1.0, v, sign(toward.z))
            }
        return sun + orientation.apply(face * halfEdge)
    }

    private fun intersects(
        start: Vector3,
        end: Vector3,
    ): Boolean {
        var near = 0.0
        var far = 1.0
        val delta = end - start
        for ((origin, direction, half) in listOf(
            Triple(start.x, delta.x, halfSize.x),
            Triple(start.y, delta.y, halfSize.y),
            Triple(start.z, delta.z, halfSize.z),
        )) {
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

    companion object {
        const val SAMPLE_COUNT = 32
        const val SAMPLE_STRIDE = 0.6180339887498949
    }
}
