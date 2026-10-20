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

package org.cbi.orbitalis.minecraft.client.space

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1

/** Optical presentation units: normalized vertical columns stay comparable across planet presets. */
object AtmosphereOptics {
    data class Parameters(
        val rayleighPerMetre: Vector3,
        val miePerMetre: Double,
        val rayleighHeight: Double,
        val mieHeight: Double,
    )

    fun parameters(
        bottom: Double,
        top: Double,
    ): Parameters {
        require(bottom.isFinite() && top.isFinite() && top > bottom)
        val thickness = top - bottom
        val rayleighHeight = thickness * 0.25
        val mieHeight = thickness * 0.10
        val rayleighColumn = rayleighHeight * -expm1(-thickness / rayleighHeight)
        val mieColumn = mieHeight * -expm1(-thickness / mieHeight)
        return Parameters(Vector3(0.10, 0.23, 0.55) * (1.0 / rayleighColumn), 0.035 / mieColumn, rayleighHeight, mieHeight)
    }

    /** Finite exponential column integral, including exactly horizontal rays without division by zero. */
    internal fun column(
        altitude: Double,
        verticalDirection: Double,
        distance: Double,
        scaleHeight: Double,
    ): Double {
        require(altitude.isFinite() && verticalDirection.isFinite() && distance.isFinite() && distance >= 0.0 && scaleHeight > 0.0)
        val slope = verticalDirection * distance / scaleHeight
        val average = if (abs(slope) < 1e-5) 1.0 - slope * 0.5 + slope * slope / 6.0 else -expm1(-slope) / slope
        return exp(-altitude / scaleHeight) * distance * average
    }

    /** Single-scattering Sun attenuation; point and direction are in the Earth's native surface chart. */
    fun sunTransmission(
        point: Vector3,
        direction: Vector3,
        halfSize: Double,
        bottom: Double,
        top: Double,
        feather: Double = 0.0,
    ): Vector3 {
        require(halfSize.isFinite() && halfSize > 0.0 && feather.isFinite() && feather >= 0.0)
        val optics = parameters(bottom, top)
        val ray = direction.normalized()
        val hit = interval(point, ray, Vector3(-halfSize, -bottom, -halfSize), Vector3(halfSize, bottom, halfSize))
        if (!(point.y >= bottom && ray.y >= 0.0) && hit != null && hit.second > 1e-7 && hit.first <= hit.second) return Vector3.ZERO
        val extent = halfSize + feather
        val atmosphere =
            interval(point, ray, Vector3(-extent, bottom, -extent), Vector3(extent, top, extent)) ?: return Vector3(1.0, 1.0, 1.0)
        val start = maxOf(0.0, atmosphere.first)
        val distance = atmosphere.second - start
        if (distance <= 0.0) return Vector3(1.0, 1.0, 1.0)
        val altitude = point.y + ray.y * start - bottom
        val rayleigh = column(altitude, ray.y, distance, optics.rayleighHeight)
        val mie = column(altitude, ray.y, distance, optics.mieHeight) * optics.miePerMetre
        val tau = optics.rayleighPerMetre * rayleigh
        return Vector3(exp(-tau.x - mie), exp(-tau.y - mie), exp(-tau.z - mie))
    }

    private fun interval(
        origin: Vector3,
        ray: Vector3,
        minimum: Vector3,
        maximum: Vector3,
    ): Pair<Double, Double>? {
        var entry = Double.NEGATIVE_INFINITY
        var exit = Double.POSITIVE_INFINITY
        for ((position, direction, lower, upper) in listOf(
            listOf(origin.x, ray.x, minimum.x, maximum.x),
            listOf(origin.y, ray.y, minimum.y, maximum.y),
            listOf(origin.z, ray.z, minimum.z, maximum.z),
        )) {
            if (abs(direction) < 1e-12) {
                if (position < lower || position > upper) return null
            } else {
                val a = (lower - position) / direction
                val b = (upper - position) / direction
                entry = maxOf(entry, minOf(a, b))
                exit = minOf(exit, maxOf(a, b))
                if (entry > exit) return null
            }
        }
        return entry to exit
    }
}
