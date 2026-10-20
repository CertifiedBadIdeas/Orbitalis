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
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Numerical contract for the analytic Sun shader's homogeneous inverse-projection rays. */
class SolarRayProjectionTest {
    private fun endpoints(
        projection: Matrix4f,
        x: Float,
        y: Float,
    ): Pair<Vector4f, Vector4f> {
        val inverse = Matrix4f(projection).invert()
        return inverse.transform(Vector4f(x, y, -1f, 1f)) to inverse.transform(Vector4f(x, y, 1f, 1f))
    }

    private fun homogeneousRay(
        a: Vector4f,
        b: Vector4f,
    ): Vector3f = Vector3f(b.x * a.w - a.x * b.w, b.y * a.w - a.y * b.w, b.z * a.w - a.z * b.w).normalize()

    @Test fun `finite perspective rays retain their direction and asymmetric lens offsets`() {
        val projection = Matrix4f().perspective(1.2f, 1.6f, 0.05f, 1024f).m20(0.12f).m21(-0.08f)
        for (x in listOf(-0.9f, 0f, 0.9f)) {
            for (y in listOf(-0.8f, 0f, 0.8f)) {
                val (a, b) = endpoints(projection, x, y)
                val old = Vector3f(b.x / b.w - a.x / a.w, b.y / b.w - a.y / a.w, b.z / b.w - a.z / a.w).normalize()
                assertTrue(old.distance(homogeneousRay(a, b)) < 1e-6f)
            }
        }
    }

    @Test fun `orthographic map rays stay parallel while their origins differ`() {
        val projection = Matrix4f().ortho(-2f, 2f, -1f, 1f, -100f, 100f)
        var firstOrigin: Vector3f? = null
        for (x in listOf(-0.9f, 0f, 0.9f)) {
            val (a, b) = endpoints(projection, x, 0.3f)
            val origin = Vector3f(a.x / a.w, a.y / a.w, a.z / a.w)
            firstOrigin?.let { assertTrue(it.distance(origin) > 1f) }
            firstOrigin = origin
            assertTrue(homogeneousRay(a, b).distance(Vector3f(0f, 0f, -1f)) < 1e-6f)
        }
    }

    @Test fun `Mercury surface adaptive projection produces finite rays when far point w is zero`() {
        val scale = 60.0 / (20000000.0 + 4096.0)
        val native = Matrix4f().perspective(1.2f, 1.6f, 0.05f, SkyProjection.nativeFar(384f, 40000000.0))
        val projection = SkyProjection.forScene(native, scale, 1.0)
        assertEquals(-1f, projection.m22())
        for (x in listOf(-0.8f, 0f, 0.8f)) {
            val (a, b) = endpoints(projection, x, 0.2f)
            assertEquals(0f, b.w)
            assertTrue(a.w.isFinite() && a.w > 0f)
            assertTrue(!(b.z / b.w).isFinite(), "The former far-point division must reproduce the defect")
            val ray = homogeneousRay(a, b)
            assertTrue(ray.x.isFinite() && ray.y.isFinite() && ray.z.isFinite())
            assertEquals(1f, ray.length(), 1e-6f)
            assertTrue(ray.z < 0f)
            // The same lens under a normal finite near plane must see the same direction.
            val (finiteA, finiteB) = endpoints(native, x, 0.2f)
            assertTrue(ray.distance(homogeneousRay(finiteA, finiteB)) < 1e-6f)
        }
    }
}
