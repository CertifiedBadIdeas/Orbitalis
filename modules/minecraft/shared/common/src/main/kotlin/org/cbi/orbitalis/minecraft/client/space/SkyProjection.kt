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

import org.joml.Matrix4f

/** A coherent compressed sky chart with a metre-based foreground clipping distance.
 * FOV, lens offsets and the native far plane stay unchanged; affine camera translations follow
 * scene compression. This projection belongs only to
 * the private sky target; native terrain and deferred physical depth retain their own projection.
 */
object SkyProjection {
    /** Distant sky has its own projection. An astronomical native far plane degenerates float frustum planes. */
    fun nativeFar(
        original: Float,
        requested: Double,
    ): Float = maxOf(original, minOf(requested, 65536.0).toFloat())

    /** Native camera startup can expose a zero-FOV projection before its first interpolation tick. */
    fun isReady(native: Matrix4f): Boolean = native.get(FloatArray(16)).all(Float::isFinite)

    fun forScene(
        native: Matrix4f,
        scale: Double,
        nearestSurfaceDistance: Double,
    ): Matrix4f {
        require(scale.isFinite() && scale > 0.0)
        require(nearestSurfaceDistance.isFinite() && nearestSurfaceDistance >= 0.0)
        require(isReady(native)) { "Non-finite native sky projection: $native" }
        val result = Matrix4f(native)
        // Native rendering right-multiplies hurt/view bob and nausea onto the perspective. For
        // P * A with affine A, depth.xyz = k * clipW.xyz and depth.w = k * clipW.w + t.
        // Recover the depth coefficients from complete rows rather than assuming m23 == -1.
        val w = doubleArrayOf(native.m03().toDouble(), native.m13().toDouble(), native.m23().toDouble())
        val depth = doubleArrayOf(native.m02().toDouble(), native.m12().toDouble(), native.m22().toDouble())
        val norm = w.sumOf { it * it }
        if (norm == 0.0) return result // Orthographic, including an affine camera transform.
        val k = w.indices.sumOf { w[it] * depth[it] } / norm
        require(k.isFinite() && k >= 1.0 - 0.00001)
        require(w.indices.all { kotlin.math.abs(depth[it] - k * w[it]) <= 0.000001 * (1.0 + kotlin.math.abs(depth[it])) }) {
            "Native sky projection has an unsupported oblique depth plane"
        }
        val translation = native.m32().toDouble() - k * native.m33()
        val originalNear = -translation / (k + 1.0)
        require(originalNear.isFinite() && originalNear > 0.0)
        val near = minOf(originalNear, scale * maxOf(0.5, nearestSurfaceDistance * 0.02))
        require(near.toFloat().isFinite() && near.toFloat() > 0f)

        // Camera translations are physical metres. Transport them into the compressed chart so
        // sky directions still align with native terrain throughout walking/hurt animation.
        result
            .m30((native.m30() * scale).toFloat())
            .m31((native.m31() * scale).toFloat())
            .m33((native.m33() * scale).toFloat())
        require(isReady(result))
        if (near == originalNear) {
            return result.m32((k * result.m33() + translation).toFloat())
        }
        val newK: Double
        val newTranslation: Double
        if (k <= 1.0) {
            // Infinite far planes (JOML biases their limit slightly), including rounded finite planes.
            newK = 1.0
            newTranslation = -2.0 * near
        } else {
            val far = -translation / (k - 1.0)
            require(far.isFinite() && far > originalNear)
            newK = (far + near) / (far - near)
            newTranslation = -2.0 * far * near / (far - near)
        }
        return result
            .m02((newK * w[0]).toFloat())
            .m12((newK * w[1]).toFloat())
            .m22((newK * w[2]).toFloat())
            .m32((newK * result.m33() + newTranslation).toFloat())
    }
}
