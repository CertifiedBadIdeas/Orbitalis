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

package org.cbi.orbitalis.minecraft.client.map

import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapCameraTest {
    @Test fun projectionMatchesActualSceneAtDifferentAspectsAndRotations() {
        val camera = MapCamera()
        for ((w, h) in listOf(144 to 148, 600 to 400, 1000 to 300)) {
            repeat(64) {
                camera.rotate(42.0, -14.0)
                val d = camera.direction
                val up = camera.up
                val matrix =
                    Matrix4f()
                        .ortho(-w.toFloat() / h, w.toFloat() / h, -1f, 1f, -100f, 100f)
                        .mul(
                            Matrix4f().lookAt(
                                d.x.toFloat(),
                                d.y.toFloat(),
                                d.z.toFloat(),
                                0f,
                                0f,
                                0f,
                                up.x.toFloat(),
                                up.y.toFloat(),
                                up.z.toFloat(),
                            ),
                        ).scale((1.0 / camera.range).toFloat())
                val p = Vector3(800.0, 300.0, -900.0)
                val actual = assertNotNull(camera.project(p, w, h))
                val clip = matrix.transform(Vector4f(p.x.toFloat(), p.y.toFloat(), p.z.toFloat(), 1f))
                assertEquals((clip.x.toDouble() + 1) * w / 2, actual.x, 0.001)
                assertEquals((1 - clip.y.toDouble()) * h / 2, actual.y, 0.001)
            }
        }
        camera.focus = Vector3(1e7, 5e6, -2e7)
        val centre = assertNotNull(camera.project(camera.focus, 600, 400))
        assertEquals(300.0, centre.x)
        assertEquals(200.0, centre.y)
        assertNull(camera.project(camera.focus + camera.right * 1e9, 600, 400))
        assertNull(camera.project(Vector3.ZERO, 0, 400))
    }

    @Test fun trajectoryEndpointsOutsideViewportKeepTheirProjection() {
        val camera = MapCamera()
        val inside = camera.focus + camera.right * 500.0
        assertEquals(camera.project(inside, 600, 400), camera.projectRaw(inside, 600, 400))
        val outside = camera.focus + camera.right * 50000.0
        assertNull(camera.project(outside, 600, 400))
        assertTrue(camera.projectRaw(outside, 600, 400).x > 600)
        assertEquals(200.0, camera.projectRaw(outside, 600, 400).y, 0.001)
    }

    @Test fun zoomStaysBoundedAndResetRestoresOverview() {
        val camera = MapCamera()
        repeat(100) {
            camera.zoom(20.0)
            camera.rotate(0.0, 10000.0)
        }
        assertEquals(128.0, camera.range)
        assertTrue(kotlin.math.abs(camera.pitch) <= Math.PI * 2 && camera.up.length() > 0.999)
        repeat(100) { camera.zoom(-20.0) }
        assertEquals(1e8, camera.range)
        camera.reset()
        assertEquals(8500.0, camera.range)
        assertEquals(Vector3.ZERO, camera.focus)
    }

    @Test fun panMovesMarkersByTheRequestedScreenDistanceAtAnyZoomAndOrientation() {
        for ((width, height) in listOf(144 to 148, 600 to 400, 1000 to 300)) {
            val camera = MapCamera()
            repeat(64) {
                camera.rotate(32.0, -18.0)
                camera.zoom(3.0)
                val anchor = camera.focus
                val before = assertNotNull(camera.project(anchor, width, height))
                val direction = camera.direction
                val up = camera.up
                val range = camera.range
                camera.pan(17.0, -11.0, height)
                val after = assertNotNull(camera.project(anchor, width, height))
                assertEquals(before.x + 17.0, after.x, 1e-8)
                assertEquals(before.y - 11.0, after.y, 1e-8)
                assertEquals(direction, camera.direction)
                assertEquals(range, camera.range)
                val matrix =
                    Matrix4f()
                        .ortho(-width.toFloat() / height, width.toFloat() / height, -1f, 1f, -100f, 100f)
                        .mul(
                            Matrix4f().lookAt(
                                direction.x.toFloat(),
                                direction.y.toFloat(),
                                direction.z.toFloat(),
                                0f,
                                0f,
                                0f,
                                up.x.toFloat(),
                                up.y.toFloat(),
                                up.z.toFloat(),
                            ),
                        ).scale((1.0 / camera.range).toFloat())
                        .translate(-camera.focus.x.toFloat(), -camera.focus.y.toFloat(), -camera.focus.z.toFloat())
                val clip = matrix.transform(Vector4f(anchor.x.toFloat(), anchor.y.toFloat(), anchor.z.toFloat(), 1f))
                assertEquals((clip.x.toDouble() + 1) * width / 2, after.x, 0.001)
                assertEquals((1 - clip.y.toDouble()) * height / 2, after.y, 0.001)
            }
        }
    }

    @Test fun aCornerCanBeCentredThenZoomedWithoutChangingTheViewAngle() {
        val camera = MapCamera()
        val corner = Vector3(5000.0, 64.0, 5000.0)
        val point = camera.projectRaw(corner, 600, 400)
        val direction = camera.direction
        camera.pan(300.0 - point.x, 200.0 - point.y, 400)
        camera.zoom(15.0)
        val centred = assertNotNull(camera.project(corner, 600, 400))
        assertEquals(300.0, centred.x, 1e-8)
        assertEquals(200.0, centred.y, 1e-8)
        assertEquals(direction, camera.direction)
        camera.reset()
        assertEquals(Vector3.ZERO, camera.focus)
    }

    @Test fun exactPolesAndInvertedViewKeepTheRenderedCameraBasis() {
        val camera = MapCamera()
        for (pitch in listOf(-Math.PI * 1.5, -Math.PI, -Math.PI / 2, 0.0, Math.PI / 2, Math.PI, Math.PI * 1.5)) {
            camera.rotate(0.0, (pitch - camera.pitch) / 0.008)
            repeat(4) {
                camera.rotate(Math.PI / 2 / 0.008, 0.0)
                val direction = camera.direction
                val up = camera.up
                val view =
                    Matrix4f().lookAt(
                        direction.x.toFloat(),
                        direction.y.toFloat(),
                        direction.z.toFloat(),
                        0f,
                        0f,
                        0f,
                        up.x.toFloat(),
                        up.y.toFloat(),
                        up.z.toFloat(),
                    )
                val horizontal = view.transform(Vector4f(camera.right.x.toFloat(), camera.right.y.toFloat(), camera.right.z.toFloat(), 0f))
                val vertical = view.transform(Vector4f(up.x.toFloat(), up.y.toFloat(), up.z.toFloat(), 0f))
                assertEquals(1.0, horizontal.x.toDouble(), 1e-6)
                assertEquals(0.0, horizontal.y.toDouble(), 1e-6)
                assertEquals(0.0, vertical.x.toDouble(), 1e-6)
                assertEquals(1.0, vertical.y.toDouble(), 1e-6)
                assertEquals(1.0, view.determinant().toDouble(), 1e-6)
            }
        }
    }

    @Test fun completeVerticalOrbitsPassBothPolesAndReturnToTheStartingBasis() {
        for (sign in listOf(-1.0, 1.0)) {
            val camera = MapCamera()
            val direction = camera.direction
            val right = camera.right
            val up = camera.up
            var previousDirection = direction
            var previousUp = up
            val step = sign * Math.PI / 180.0
            repeat(720) {
                camera.rotate(0.0, step / 0.008)
                assertTrue(camera.direction.dot(previousDirection) > 0.999)
                assertTrue(camera.up.dot(previousUp) > 0.999)
                assertEquals(1.0, camera.direction.length(), 1e-10)
                assertEquals(1.0, camera.right.length(), 1e-10)
                assertEquals(1.0, camera.up.length(), 1e-10)
                assertEquals(0.0, camera.direction.dot(camera.up), 1e-10)
                previousDirection = camera.direction
                previousUp = camera.up
            }
            assertTrue((direction - camera.direction).length() < 1e-10)
            assertTrue((right - camera.right).length() < 1e-10)
            assertTrue((up - camera.up).length() < 1e-10)
        }
    }

    @Test fun unavailableViewportOrInvalidMouseDeltasDoNotMoveTheCamera() {
        val camera = MapCamera()
        val direction = camera.direction
        camera.rotate(Double.NaN, 10.0)
        camera.rotate(10.0, Double.POSITIVE_INFINITY)
        camera.pan(10.0, 10.0, 0)
        camera.pan(Double.NaN, 10.0, 400)
        camera.pan(10.0, Double.POSITIVE_INFINITY, 400)
        assertEquals(Vector3.ZERO, camera.focus)
        assertEquals(direction, camera.direction)
    }
}
