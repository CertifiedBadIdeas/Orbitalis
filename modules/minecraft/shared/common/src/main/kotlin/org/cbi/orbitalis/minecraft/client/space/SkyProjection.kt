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
 * FOV, lens offsets and the native far plane stay unchanged. This projection belongs only to
 * the private sky target; native terrain and deferred physical depth retain their own projection.
 */
object SkyProjection {
    fun forScene(
        native: Matrix4f,
        scale: Double,
        nearestSurfaceDistance: Double,
    ): Matrix4f {
        require(scale.isFinite() && scale > 0.0)
        require(nearestSurfaceDistance.isFinite() && nearestSurfaceDistance >= 0.0)
        require(native.get(FloatArray(16)).all(Float::isFinite))
        val result = Matrix4f(native)
        if (native.m33() != 0f) return result
        require(native.m23() == -1f)
        val z = native.m22().toDouble()
        val translation = native.m32().toDouble()
        val originalNear = translation / (z - 1.0)
        require(originalNear.isFinite() && originalNear > 0.0)
        val near = minOf(originalNear, scale * maxOf(0.5, nearestSurfaceDistance * 0.02))
        require(near.toFloat().isFinite() && near.toFloat() > 0f)
        if (near == originalNear) return result
        if (z >= -1.0) {
            require(z <= -1.0 + 0.00001)
            // Infinite far planes (JOML biases their limit slightly), including rounded finite planes.
            return result.m22(-1f).m32((-2.0 * near).toFloat())
        }
        val far = translation / (z + 1.0)
        require(far.isFinite() && far > originalNear)
        return result.m22((-(far + near) / (far - near)).toFloat()).m32((-2.0 * far * near / (far - near)).toFloat())
    }
}
