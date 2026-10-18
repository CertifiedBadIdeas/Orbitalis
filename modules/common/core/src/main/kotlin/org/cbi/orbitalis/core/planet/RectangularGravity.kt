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
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Homogeneous rectangular near field with a conservative point-mass far field; metres, local Y up. */
class RectangularGravity(
    val halfWidth: Double,
    val halfHeight: Double,
    val halfDepth: Double,
    val surfaceAcceleration: Double,
) {
    data class Sample(
        val potential: Double,
        val acceleration: Vector3,
    )

    private val scale = max(halfWidth, max(halfHeight, halfDepth))
    private val half: Vector3
    private val strength: Double

    /** Total G*M, shared by the rectangular near field and exact central far field. */
    val mu: Double

    init {
        for (extent in listOf(halfWidth, halfHeight, halfDepth, surfaceAcceleration)) {
            require(extent.isFinite() && extent > 0.0) { "Body dimensions and gravity must be finite and positive" }
        }
        half = Vector3(halfWidth / scale, halfHeight / scale, halfDepth / scale)
        require(minOf(half.x, half.y, half.z) >= 1e-6) { "Body aspect ratio exceeds the supported numerical range" }
        strength = surfaceAcceleration / -corners(Vector3(0.0, half.y, 0.0)).acceleration.y
        mu = strength * scale * scale * (8.0 * half.x * half.y * half.z)
        require(strength.isFinite() && mu.isFinite() && mu > 0.0) { "Body parameters exceed the numerical range" }
    }

    fun acceleration(position: Vector3): Vector3 = sample(position).acceleration

    /** Specific gravitational potential [J/kg], zero at infinity, including the body's interior. */
    fun potential(position: Vector3): Double = sample(position).potential

    fun specificEnergy(state: CartesianState): Double = state.velocity.dot(state.velocity) * 0.5 + potential(state.position)

    fun contains(position: Vector3): Boolean = abs(position.x) <= halfWidth && abs(position.y) <= halfHeight && abs(position.z) <= halfDepth

    fun sample(position: Vector3): Sample {
        val p = Vector3(position.x / scale, position.y / scale, position.z / scale)
        val radius = p.length()
        val field =
            when {
                radius <= 8.0 -> {
                    corners(p)
                }

                radius >= 12.0 -> {
                    pointMass(p)
                }

                else -> {
                    // Blend potentials, including the derivative of the blend. Blending just forces
                    // would introduce a nonconservative shell and invalidate engines-off energy checks.
                    val near = corners(p)
                    val far = pointMass(p)
                    val t = (radius - 8.0) / 4.0
                    val weight = t * t * t * (10.0 + t * (-15.0 + 6.0 * t))
                    val derivative = 30.0 * t * t * (1.0 - t) * (1.0 - t) / 4.0
                    Sample(
                        near.potential + (far.potential - near.potential) * weight,
                        near.acceleration * (1.0 - weight) + far.acceleration * weight -
                            p.normalized() * ((far.potential - near.potential) * derivative),
                    )
                }
            }
        return Sample(field.potential * strength * scale, field.acceleration * strength)
    }

    // Direct evaluation of the rectangular volume's Newton integral via its eight corners.
    // Formulas and sign convention are documented in docs/contributors/flat-gravity.md.
    private fun corners(p: Vector3): Sample {
        var potential = 0.0
        var gx = 0.0
        var gy = 0.0
        var gz = 0.0
        for (sx in listOf(-1.0, 1.0)) {
            for (sy in listOf(-1.0, 1.0)) {
                for (sz in listOf(-1.0, 1.0)) {
                    val x = sx * half.x - p.x
                    val y = sy * half.y - p.y
                    val z = sz * half.z - p.z
                    val r = sqrt(x * x + y * y + z * z)
                    val lx = log(x, y, z, r)
                    val ly = log(y, z, x, r)
                    val lz = log(z, x, y, r)
                    val ax = angle(y * z, x * r)
                    val ay = angle(z * x, y * r)
                    val az = angle(x * y, z * r)
                    val sign = sx * sy * sz
                    potential -= sign * (x * y * lz + y * z * lx + z * x * ly - (x * x * ax + y * y * ay + z * z * az) * 0.5)
                    gx -= sign * (y * lz + z * ly - x * ax)
                    gy -= sign * (z * lx + x * lz - y * ay)
                    gz -= sign * (x * ly + y * lx - z * az)
                }
            }
        }
        return Sample(potential, if (p == Vector3.ZERO) Vector3.ZERO else Vector3(gx, gy, gz))
    }

    // Dimensionless monopole with exactly the same total mass. C2 potential blending above
    // retains conservative acceleration while discarding distant shape multipoles deliberately.
    private fun pointMass(p: Vector3): Sample {
        val radius = p.length()
        val mass = 8.0 * half.x * half.y * half.z
        return Sample(-mass / radius, p.normalized() * (-mass / radius / radius))
    }

    private fun log(
        axis: Double,
        other: Double,
        remaining: Double,
        radius: Double,
    ): Double =
        when {
            radius == 0.0 -> 0.0
            axis >= 0.0 -> ln(radius + axis)
            other == 0.0 && remaining == 0.0 -> -ln(-2.0 * axis)
            else -> ln((other * other + remaining * remaining) / (radius - axis))
        }

    // Principal arctangent, not atan2: the other branch introduces fictitious interior forces.
    private fun angle(
        numerator: Double,
        denominator: Double,
    ): Double =
        when {
            denominator != 0.0 -> atan(numerator / denominator)
            numerator > 0.0 -> PI / 2.0
            numerator < 0.0 -> -PI / 2.0
            else -> 0.0
        }
}
