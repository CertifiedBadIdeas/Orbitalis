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

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Finite solar-disc quadrature against an opaque box, all coordinates in the box's local frame.
 * Samples represent equal areas of the apparent disc, not a point light or spherical occluder.
 * Rendering mirrors [SAMPLE_COUNT] and the golden-angle rule without rebuilding terrain meshes.
 */
class SolarOcclusion(
    val sun: Vector3,
    val discU: Vector3,
    val discV: Vector3,
    val halfSize: Vector3,
) {
    init {
        require(halfSize.x > 0.0 && halfSize.y > 0.0 && halfSize.z > 0.0)
    }

    /** Fraction of direct solar light reaching [point]; atmospheric refraction is not modelled. */
    fun visibility(point: Vector3): Double {
        var visible = 0
        repeat(SAMPLE_COUNT) { i ->
            val radius = sqrt((i + 0.5) / SAMPLE_COUNT)
            val angle = i * GOLDEN_ANGLE
            val target = sun + discU * (radius * cos(angle)) + discV * (radius * sin(angle))
            if (!intersects(point, target)) visible++
        }
        return visible.toDouble() / SAMPLE_COUNT
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
        const val GOLDEN_ANGLE = 2.399963229728653

        fun disc(
            sun: Vector3,
            radius: Double,
            observer: Vector3,
            halfSize: Vector3,
        ): SolarOcclusion {
            require(radius.isFinite() && radius > 0.0)
            val direction = (sun - observer).normalized()
            val reference = if (kotlin.math.abs(direction.y) < 0.9) Vector3(0.0, 1.0, 0.0) else Vector3(1.0, 0.0, 0.0)
            val u = direction.cross(reference).normalized() * radius
            val v = direction.cross(u).normalized() * radius
            return SolarOcclusion(sun, u, v, halfSize)
        }
    }
}
