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
import org.joml.Vector4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkyProjectionTest {
    private fun native() = Matrix4f().perspective(1.2f, 1.6f, 0.05f, 1024f)

    @Test
    fun `Mercury foreground remains inside sky clipping volume far from Earth`() {
        val scale = 60.0 / (20000000.0 + 4096.0)
        val projection = SkyProjection.forScene(native(), scale, 3000.0)
        val point = Vector4f(0f, 0f, (-3000.0 * scale).toFloat(), 1f)
        val old = native().transform(Vector4f(point))
        val adjusted = projection.transform(point)
        assertTrue(old.z < -old.w)
        assertTrue(adjusted.z in -adjusted.w..adjusted.w)
        assertEquals(native().m00(), projection.m00())
        assertEquals(native().m11(), projection.m11())
    }

    @Test
    fun `scene depth unprojects to metres then native depth independently`() {
        val native = native()
        val scale = 0.000003
        val scene = SkyProjection.forScene(native, scale, 3000.0)
        val physical = Vector4f(100f, 200f, -3000f, 1f)
        val compressed = Vector4f(physical).mul(scale.toFloat(), scale.toFloat(), scale.toFloat(), 1f)
        val clip = scene.transform(compressed)
        val ndc = Vector4f(clip).div(clip.w)
        val recovered = Matrix4f(scene).invert().transform(ndc)
        recovered.div(recovered.w)
        recovered.mul((1.0 / scale).toFloat(), (1.0 / scale).toFloat(), (1.0 / scale).toFloat(), 1f)
        assertEquals(physical.x, recovered.x, 0.02f)
        assertEquals(physical.y, recovered.y, 0.02f)
        assertEquals(physical.z, recovered.z, 0.1f)
        val expectedDepth = native.transform(Vector4f(physical))
        val actualDepth = native.transform(recovered)
        assertEquals(expectedDepth.z / expectedDepth.w, actualDepth.z / actualDepth.w, 1e-6f)
    }

    @Test
    fun `orthographic map is copied without changes`() {
        val native = Matrix4f().ortho(-2f, 2f, -1f, 1f, -100f, 100f)
        val scene = SkyProjection.forScene(native, 0.000001, 1.0)
        assertEquals(native, scene)
        scene.m00(99f)
        assertEquals(0.5f, native.m00())
    }

    @Test
    fun `local Earth view retains native projection and asymmetric lens offsets`() {
        val native = native().m20(0.1f).m21(-0.2f)
        assertEquals(native, SkyProjection.forScene(native, 0.01, 3000.0))
        val adjusted = SkyProjection.forScene(native, 0.000003, 3000.0)
        assertEquals(native.m20(), adjusted.m20())
        assertEquals(native.m21(), adjusted.m21())
    }

    @Test
    fun `infinite far projection does not produce invalid matrix coefficients`() {
        val native = Matrix4f().perspective(1.2f, 1.6f, 0.05f, Float.POSITIVE_INFINITY)
        val adjusted = SkyProjection.forScene(native, 0.000003, 0.0)
        assertTrue(adjusted.get(FloatArray(16)).all(Float::isFinite))
        assertEquals(-1f, adjusted.m22())
        assertEquals(-0.000003f, adjusted.m32())
    }

    @Test
    fun `malformed scale distance and perspective matrices are rejected`() {
        for (scale in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { SkyProjection.forScene(native(), scale, 0.0) }
        }
        for (distance in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { SkyProjection.forScene(native(), 1.0, distance) }
        }
        assertFailsWith<IllegalArgumentException> { SkyProjection.forScene(native().m22(Float.NaN), 1.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { SkyProjection.forScene(native().m32(1f), 1.0, 0.0) }
    }
}
