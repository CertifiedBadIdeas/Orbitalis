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

import kotlin.math.hypot

/** Finite Cartesian vector. Physical units belong to the containing model. */
data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Vector components must be finite" }
    }

    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Double): Vector3 {
        require(scale.isFinite()) { "Scale must be finite" }
        return Vector3(x * scale, y * scale, z * scale)
    }

    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3) = Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)

    fun length() = hypot(hypot(x, y), z)

    fun normalized(): Vector3 {
        val length = length()
        require(length.isFinite() && length > 0.0) { "Cannot normalize a zero or unrepresentable vector" }
        return Vector3(x / length, y / length, z / length)
    }

    companion object {
        val ZERO = Vector3(0.0, 0.0, 0.0)
    }
}

/** Position [m] and velocity [m/s] relative to the same inertial origin and axes. */
data class CartesianState(
    val position: Vector3,
    val velocity: Vector3,
)
