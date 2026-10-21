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
    @Test fun `astronomical sky does not degenerate native culling planes`() {
        val far = SkyProjection.nativeFar(384f, 40000000.0)
        val projection = Matrix4f().perspective(1.2f, 1.6f, 0.05f, far)
        // Vanilla normalizes row3-row2 for its far plane. Zero length makes the camera-cube loop endless.
        assertTrue(projection.m23() - projection.m22() > 0f)
        val culling = org.joml.FrustumIntersection(projection)
        assertEquals(org.joml.FrustumIntersection.INSIDE, culling.intersectAab(-4f, -4f, -20f, 4f, 4f, -12f))
        assertEquals(65536f, far)
        assertEquals(384f, SkyProjection.nativeFar(384f, 100.0))
        assertEquals(100000f, SkyProjection.nativeFar(100000f, 100.0))
    }

    @Test fun `native zero FOV startup frame defers sky until a finite projection arrives`() {
        val startup = Matrix4f().perspective(0f, 1.7f, 0.05f, 20000000f)
        kotlin.test.assertFalse(SkyProjection.isReady(startup))
        kotlin.test.assertTrue(SkyProjection.isReady(native()))
        kotlin.test.assertTrue(SkyProjection.isReady(Matrix4f().ortho(-1f, 1f, -1f, 1f, 0.05f, 100f)))
    }

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

    @Test fun `landing hurt and walking bob keep sky aligned through compressed projection`() {
        // GameRenderer.renderLevel right-multiplies hurt, walk bob and nausea onto the perspective.
        val hurt = Matrix4f().rotateY(-0.7f).rotateZ(-0.15f).rotateY(0.7f)
        val walking = Matrix4f(hurt).translate(0.08f, -0.1f, 0f).rotateZ(0.017f).rotateX(0.031f)
        val nauseaAxis = org.joml.Vector3f(0f, 0.70710677f, 0.70710677f)
        val nausea = Matrix4f(walking).rotate(0.8f, nauseaAxis).scale(1.08f, 1f, 1f).rotate(-0.8f, nauseaAxis)
        for (far in listOf(1024f, Float.POSITIVE_INFINITY)) {
            for (effects in listOf(hurt, walking, nausea)) {
                val base = Matrix4f().perspective(1.2f, 1.6f, 0.05f, far).m20(0.1f).m21(-0.2f)
                val projection = Matrix4f(base).mul(effects)
                assertTrue(projection.m23() != -1f)
                val scale = 0.000003
                val scene = SkyProjection.forScene(projection, scale, 3000.0)
                assertTrue(scene.get(FloatArray(16)).all(Float::isFinite))
                val physical = Vector4f(100f, 200f, -3000f, 1f)
                val clip = projection.transform(Vector4f(physical))
                val compressed = Vector4f(physical).mul(scale.toFloat(), scale.toFloat(), scale.toFloat(), 1f)
                val skyClip = scene.transform(Vector4f(compressed))
                assertEquals(clip.x / clip.w, skyClip.x / skyClip.w, 1e-6f)
                assertEquals(clip.y / clip.w, skyClip.y / skyClip.w, 1e-6f)
                assertTrue(skyClip.z in -skyClip.w..skyClip.w)
                val recovered = Matrix4f(scene).invert().transform(Vector4f(skyClip).div(skyClip.w))
                recovered.div(recovered.w)
                recovered.mul((1.0 / scale).toFloat(), (1.0 / scale).toFloat(), (1.0 / scale).toFloat(), 1f)
                assertEquals(physical.x, recovered.x, 0.05f)
                assertEquals(physical.y, recovered.y, 0.05f)
                assertEquals(physical.z, recovered.z, 0.2f)
                val nativeDepth = projection.transform(recovered)
                assertEquals(clip.z / clip.w, nativeDepth.z / nativeDepth.w, 1e-6f)
            }
        }
    }

    @Test fun `local near plane retains physical bob translation when no clipping adjustment is needed`() {
        val native = native().translate(0.08f, -0.1f, 0.06f).rotateX(0.03f)
        val physical = Vector4f(10f, 20f, -100f, 1f)
        val clip = native.transform(Vector4f(physical))
        for (scale in listOf(0.01, 1.0)) {
            val scene = SkyProjection.forScene(native, scale, 3000.0)
            val compressed = Vector4f(physical).mul(scale.toFloat(), scale.toFloat(), scale.toFloat(), 1f)
            val actual = scene.transform(compressed)
            assertEquals(clip.x / clip.w, actual.x / actual.w, 1e-6f)
            assertEquals(clip.y / clip.w, actual.y / actual.w, 1e-6f)
        }
    }

    @Test fun `translated camera near and far planes survive full depth-row replacement`() {
        val effects = Matrix4f().translate(0.1f, -0.2f, 0.08f).rotateX(0.13f).rotateZ(-0.1f)
        val native = native().mul(effects)
        assertTrue(native.m33() != 0f)
        val scale = 0.000003
        val scene = SkyProjection.forScene(native, scale, 3000.0)
        val scaledEffects =
            Matrix4f(effects)
                .m30(effects.m30() * scale.toFloat())
                .m31(effects.m31() * scale.toFloat())
                .m32(effects.m32() * scale.toFloat())
        val adaptedPerspective = Matrix4f(scene).mul(Matrix4f(scaledEffects).invert())
        val near = 0.00018f
        val nearClip = adaptedPerspective.transform(Vector4f(0f, 0f, -near, 1f))
        assertEquals(-1f, nearClip.z / nearClip.w, 1e-5f)
        val farClip = adaptedPerspective.transform(Vector4f(0f, 0f, -1024f, 1f))
        assertEquals(1f, farClip.z / farClip.w, 1e-5f)
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
