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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.cos
import kotlin.math.sin

/** Proper orthonormal rotation; columns are the local basis vectors expressed in parent axes. */
data class Rotation3 private constructor(
    val xAxis: Vector3,
    val yAxis: Vector3,
    val zAxis: Vector3,
) {
    fun apply(vector: Vector3): Vector3 = xAxis * vector.x + yAxis * vector.y + zAxis * vector.z

    fun inverse(vector: Vector3): Vector3 = Vector3(xAxis.dot(vector), yAxis.dot(vector), zAxis.dot(vector))

    /** Composition applies [other] first, then this rotation. */
    operator fun times(other: Rotation3): Rotation3 = Rotation3(apply(other.xAxis), apply(other.yAxis), apply(other.zAxis))

    companion object {
        val IDENTITY = Rotation3(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0))

        /** Validated wire/integration boundary; reject reflections, shear and nonfinite bases. */
        fun fromAxes(
            x: Vector3,
            y: Vector3,
            z: Vector3,
        ): Rotation3 {
            val axes = listOf(x, y, z)
            require(axes.all { it.length().isFinite() && kotlin.math.abs(it.dot(it) - 1.0) < 1e-8 })
            require(kotlin.math.abs(x.dot(y)) < 1e-8 && kotlin.math.abs(x.dot(z)) < 1e-8 && kotlin.math.abs(y.dot(z)) < 1e-8)
            require(x.cross(y).dot(z) > 1.0 - 1e-8)
            return Rotation3(x, y, z)
        }

        /** Right-handed active rotation by [radians] about [axis]; no game/client math dependency. */
        fun axisAngle(
            axis: Vector3,
            radians: Double,
        ): Rotation3 {
            require(radians.isFinite()) { "Rotation angle must be finite" }
            val normal = axis.normalized()
            val c = cos(radians)
            val s = sin(radians)

            fun rotate(v: Vector3) = v * c + normal.cross(v) * s + normal * (normal.dot(v) * (1.0 - c))
            return Rotation3(rotate(IDENTITY.xAxis), rotate(IDENTITY.yAxis), rotate(IDENTITY.zAxis))
        }
    }
}
