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
import kotlin.math.max

/**
 * Conservative gameplay field for a cube, not Newtonian gravity of a homogeneous cuboid.
 * Face interiors have exactly normal gravity; positive edge widths round adjacent directions.
 * Zero edge width selects a sharp cube with symmetric subgradients at exact ties.
 * Height is measured in cube shells, not as shortest Euclidean distance to the solid.
 */
class CubicGravity(
    val halfExtent: Double,
    val surfaceAcceleration: Double,
    val edgeBlendWidth: Double,
    val transitionStartAltitude: Double,
    val transitionEndAltitude: Double,
) {
    init {
        require(halfExtent.isFinite() && halfExtent > 0.0)
        require(surfaceAcceleration.isFinite() && surfaceAcceleration > 0.0)
        require(edgeBlendWidth.isFinite() && edgeBlendWidth >= 0.0 && edgeBlendWidth < halfExtent)
        require(transitionStartAltitude.isFinite() && transitionStartAltitude >= 0.0)
        require(transitionEndAltitude.isFinite() && transitionEndAltitude > transitionStartAltitude)
        require((halfExtent + transitionEndAltitude).isFinite())
        require((halfExtent * surfaceAcceleration).isFinite())
    }

    data class Sample(
        val acceleration: Vector3,
        /** Specific gravitational potential [m²/s²], zero at infinity. */
        val potential: Double,
    )

    /** Signed height above the sharp reference cube [m]; edges share the same shell. */
    fun altitude(position: Vector3): Double = maxOf(abs(position.x), abs(position.y), abs(position.z)) - halfExtent

    fun acceleration(position: Vector3): Vector3 = sample(position).acceleration

    fun sample(position: Vector3): Sample {
        val cube = if (edgeBlendWidth == 0.0) sharpCube(position) else roundedCube(position)
        val height = cube.value - halfExtent
        val radius: Double
        val gradient: Vector3
        if (height <= transitionStartAltitude) {
            radius = cube.value
            gradient = cube.gradient
        } else {
            val sphericalRadius = position.length()
            require(sphericalRadius.isFinite()) { "Position radius is unrepresentable" }
            val normal = position.normalized()
            if (height >= transitionEndAltitude) {
                radius = sphericalRadius
                gradient = normal
            } else {
                val width = transitionEndAltitude - transitionStartAltitude
                val t = (height - transitionStartAltitude) / width
                // Quintic smoothstep: both first and second derivatives vanish at the endpoints.
                val blend = t * t * t * (10.0 + t * (-15.0 + 6.0 * t))
                val derivative = 30.0 * t * t * (1.0 - t) * (1.0 - t) / width
                radius = cube.value + (sphericalRadius - cube.value) * blend
                // Differentiate the effective radius, including the height-dependent blend.
                gradient = cube.gradient * (1.0 - blend + (sphericalRadius - cube.value) * derivative) + normal * blend
            }
        }
        val ratio = radius / halfExtent
        val derivative: Double
        val potential: Double
        if (ratio < 1.0) {
            derivative = surfaceAcceleration * ratio
            potential = surfaceAcceleration * halfExtent * (ratio * ratio - 3.0) / 2.0
        } else {
            derivative = surfaceAcceleration / ratio / ratio
            potential = -surfaceAcceleration * halfExtent / ratio
        }
        return Sample(gradient * -derivative, potential)
    }

    private data class Radius(
        val value: Double,
        val gradient: Vector3,
    )

    private fun sharpCube(position: Vector3): Radius {
        val coordinates = doubleArrayOf(position.x, -position.x, position.y, -position.y, position.z, -position.z)
        val largest = coordinates.max()
        val count = coordinates.count { it == largest }
        val weights = coordinates.map { if (it == largest) 1.0 / count else 0.0 }
        return Radius(largest, Vector3(weights[0] - weights[1], weights[2] - weights[3], weights[4] - weights[5]))
    }

    /**
     * Quadratically regularized max over (+x,-x,+y,-y,+z,-z).
     * Maximize w·a + ε(1-||w||²)/2 on the probability simplex. Its unique weights
     * are its gradient. It equals max|coordinate| outside the compact edge band.
     * Signed coordinates make the centre differentiable without choosing a preferred face.
     */
    private fun roundedCube(position: Vector3): Radius {
        val coordinates = doubleArrayOf(position.x, -position.x, position.y, -position.y, position.z, -position.z)
        val largest = coordinates.max()
        // Work with small deficits to avoid cancellation of large nearly equal coordinates.
        val deficits = coordinates.map { (largest - it) / edgeBlendWidth }
        val active = deficits.filter { it < 1.0 }.sorted()
        var sum = 0.0
        var threshold = 1.0
        for ((index, deficit) in active.withIndex()) {
            if (deficit >= threshold) break
            sum += deficit
            threshold = (1.0 + sum) / (index + 1)
        }
        val weights = deficits.map { max(0.0, threshold - it) }
        val squaredWeights = weights.sumOf { it * it }
        val weightedDeficit = weights.indices.sumOf { if (weights[it] == 0.0) 0.0 else weights[it] * deficits[it] }
        return Radius(
            largest + edgeBlendWidth * ((1.0 - squaredWeights) / 2.0 - weightedDeficit),
            Vector3(weights[0] - weights[1], weights[2] - weights[3], weights[4] - weights[5]),
        )
    }
}
