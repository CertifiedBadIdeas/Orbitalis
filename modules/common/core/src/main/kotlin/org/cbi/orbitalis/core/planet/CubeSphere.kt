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

/** Right-handed chart bases; IDs and ordering are experimental, not persistence contracts. */
enum class CubeFace(
    val normal: Vector3,
    val uAxis: Vector3,
    val vAxis: Vector3,
) {
    POSITIVE_X(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0), Vector3(0.0, 1.0, 0.0)),
    NEGATIVE_X(Vector3(-1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0), Vector3(0.0, 1.0, 0.0)),
    POSITIVE_Y(Vector3(0.0, 1.0, 0.0), Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0)),
    NEGATIVE_Y(Vector3(0.0, -1.0, 0.0), Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0)),
    POSITIVE_Z(Vector3(0.0, 0.0, 1.0), Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
    NEGATIVE_Z(Vector3(0.0, 0.0, -1.0), Vector3(-1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0)),
}

/** u/v are dimensionless chart coordinates in [-1, 1]; altitude is above the reference sphere. */
data class SurfacePoint(
    val face: CubeFace,
    val u: Double,
    val v: Double,
    val altitudeMetres: Double,
) {
    init {
        require(u.isFinite() && u in -1.0..1.0 && v.isFinite() && v in -1.0..1.0) { "Coordinates must be inside a finite face" }
        require(altitudeMetres.isFinite()) { "Altitude must be finite" }
    }
}

/** Chart derivatives [1/s] and outward radial speed [m/s], not three Cartesian speeds. */
data class ChartVelocity(
    val uPerSecond: Double,
    val vPerSecond: Double,
    val radialMetresPerSecond: Double,
) {
    init {
        require(uPerSecond.isFinite() && vPerSecond.isFinite() && radialMetresPerSecond.isFinite()) { "Rates must be finite" }
    }

    companion object {
        val ZERO = ChartVelocity(0.0, 0.0, 0.0)
    }
}

data class SurfaceState(
    val point: SurfacePoint,
    val velocity: ChartVelocity,
)

/** Radial cube projection. It closes the sphere, but does not preserve surface distances or areas. */
class CubeSphere(
    val radiusMetres: Double,
) {
    init {
        require(radiusMetres.isFinite() && radiusMetres > 0.0) { "Radius must be finite and positive" }
    }

    fun toCartesian(
        point: SurfacePoint,
        velocity: ChartVelocity = ChartVelocity.ZERO,
    ): CartesianState {
        val radius = radiusMetres + point.altitudeMetres
        require(radius.isFinite() && radius > 0.0) { "Point must be outside the planet centre" }
        val face = point.face
        val cube = face.normal + face.uAxis * point.u + face.vAxis * point.v
        val cubeLength = cube.length()
        val normal = cube.normalized()
        val uDerivative = (face.uAxis - normal * normal.dot(face.uAxis)) * (radius / cubeLength)
        val vDerivative = (face.vAxis - normal * normal.dot(face.vAxis)) * (radius / cubeLength)
        return CartesianState(
            normal * radius,
            uDerivative * velocity.uPerSecond + vDerivative * velocity.vPerSecond + normal * velocity.radialMetresPerSecond,
        )
    }

    fun fromCartesian(state: CartesianState): SurfaceState {
        val radius = state.position.length()
        require(radius.isFinite() && radius > 0.0) { "Point must be outside the planet centre" }
        val p = state.position
        // Deterministic shared-edge/corner ownership: X, then Y, then Z.
        val face =
            when {
                abs(p.x) >= abs(p.y) && abs(p.x) >= abs(p.z) -> if (p.x >= 0.0) CubeFace.POSITIVE_X else CubeFace.NEGATIVE_X
                abs(p.y) >= abs(p.z) -> if (p.y >= 0.0) CubeFace.POSITIVE_Y else CubeFace.NEGATIVE_Y
                else -> if (p.z >= 0.0) CubeFace.POSITIVE_Z else CubeFace.NEGATIVE_Z
            }
        val denominator = p.dot(face.normal)
        val u = p.dot(face.uAxis) / denominator
        val v = p.dot(face.vAxis) / denominator
        val normalVelocity = state.velocity.dot(face.normal)
        return SurfaceState(
            SurfacePoint(face, u, v, radius - radiusMetres),
            ChartVelocity(
                (state.velocity.dot(face.uAxis) - u * normalVelocity) / denominator,
                (state.velocity.dot(face.vAxis) - v * normalVelocity) / denominator,
                state.velocity.dot(p.normalized()),
            ),
        )
    }
}
