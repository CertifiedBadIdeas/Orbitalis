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

import kotlin.math.abs
import kotlin.math.sign

/** Conservative compatibility region around the playable top of a finite body, not extra mass. */
class SurfaceGravityLayer(
    val body: RectangularGravity,
    val nativeAcceleration: Double,
    val nearHeight: Double = 256.0,
    val fullHeight: Double = 600.0,
    val edgeWidth: Double = 256.0,
    val lowerDepth: Double = 256.0,
) {
    private val nativePotentialAtSurface: Double

    init {
        require(nativeAcceleration.isFinite() && nativeAcceleration >= body.surfaceAcceleration) {
            "Native gravity must be finite and at least the top-centre body gravity"
        }
        require(nearHeight.isFinite() && fullHeight.isFinite() && nearHeight >= 0.0 && fullHeight > nearHeight)
        require(edgeWidth.isFinite() && lowerDepth.isFinite() && edgeWidth > 0.0 && lowerDepth > 0.0)
        // Match at the upper centre, where the slab potential is most negative on that plane.
        // The potential difference then cannot introduce upward acceleration in the upper blend.
        nativePotentialAtSurface = body.potential(Vector3(0.0, body.halfHeight + fullHeight, 0.0)) - nativeAcceleration * fullHeight
    }

    fun acceleration(position: Vector3): Vector3 = sample(position).acceleration

    fun potential(position: Vector3): Double = sample(position).potential

    fun specificEnergy(state: CartesianState): Double = state.velocity.dot(state.velocity) * 0.5 + potential(state.position)

    fun sample(position: Vector3): RectangularGravity.Sample {
        val x = fade((abs(position.x) - body.halfWidth) / edgeWidth)
        val z = fade((abs(position.z) - body.halfDepth) / edgeWidth)
        val upper = fade((position.y - body.halfHeight - nearHeight) / (fullHeight - nearHeight))
        val lower = fade((-position.y - body.halfHeight) / lowerDepth)
        val vertical = upper.first * lower.first
        val weight = x.first * z.first * vertical
        val native = Vector3(0.0, -nativeAcceleration, 0.0)
        val potential = nativePotentialAtSurface + nativeAcceleration * (position.y - body.halfHeight)
        if (weight == 1.0) return RectangularGravity.Sample(potential, native)
        val finite = body.sample(position)
        if (weight == 0.0) return finite
        val gradient =
            Vector3(
                x.second * sign(position.x) / edgeWidth * z.first * vertical,
                x.first * z.first * (upper.second / (fullHeight - nearHeight) * lower.first - upper.first * lower.second / lowerDepth),
                z.second * sign(position.z) / edgeWidth * x.first * vertical,
            )
        val difference = potential - finite.potential
        return RectangularGravity.Sample(
            finite.potential + weight * difference,
            finite.acceleration * (1.0 - weight) + native * weight - gradient * difference,
        )
    }

    /** Value and derivative of a compact C2 window, from one at t<=0 to zero at t>=1. */
    private fun fade(t: Double): Pair<Double, Double> =
        when {
            t <= 0.0 -> 1.0 to 0.0
            t >= 1.0 -> 0.0 to 0.0
            else -> (1.0 - t * t * t * (10.0 + t * (-15.0 + 6.0 * t))) to (-30.0 * t * t * (1.0 - t) * (1.0 - t))
        }
}
