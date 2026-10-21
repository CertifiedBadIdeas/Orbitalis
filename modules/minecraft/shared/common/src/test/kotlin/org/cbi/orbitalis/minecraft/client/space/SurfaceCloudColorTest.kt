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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceCloudColorTest {
    private val white = Vector3(0.8, 0.8, 0.8)
    private val point = Vector3(0.0, 192.0, 0.0)

    private fun color(
        direction: Vector3,
        native: Vector3 = white,
        cloudPoint: Vector3 = point,
    ): Vector3 {
        val ray = direction.normalized()
        val transmission = AtmosphereOptics.sunTransmission(cloudPoint, ray, 5000.0, 63.0, 216.0, 160.0)
        return SurfaceCloudColor.tint(native, transmission, ray.y)
    }

    @Test fun sunriseAndSunsetUseTheSameWarmFiniteCloudColumn() {
        val rising = color(Vector3(1.0, 0.01, 0.0))
        val setting = color(Vector3(-1.0, 0.01, 0.0))
        assertEquals(rising, setting)
        assertTrue(rising.x > rising.y && rising.y > rising.z)
        assertEquals(white.x, rising.x)
        assertTrue(rising.z < white.z * 0.8)
    }

    @Test fun noonNightAndOccludedDirectSunDoNotReceiveAnAlwaysOnOrangeWash() {
        assertEquals(white, color(Vector3(0.0, 1.0, 0.0)))
        assertEquals(white, color(Vector3(1.0, -0.3, 0.0), cloudPoint = Vector3(4999.0, 192.0, 0.0)))
        assertEquals(white, color(Vector3(0.0, -1.0, 0.0)))
        assertEquals(white, SurfaceCloudColor.tint(white, Vector3.ZERO, 0.0))
        assertEquals(white, SurfaceCloudColor.tint(white, Vector3(1e-100, 1e-101, 1e-102), 0.0))
    }

    @Test fun nativeRainAndThunderBrightnessAreNotReplacedOrAmplified() {
        val clear = color(Vector3(1.0, 0.01, 0.0))
        val weather = color(Vector3(1.0, 0.01, 0.0), white * 0.25)
        assertEquals(clear.x * 0.25, weather.x, 1e-12)
        assertEquals(clear.y * 0.25, weather.y, 1e-12)
        assertEquals(clear.z * 0.25, weather.z, 1e-12)
        val native = Vector3(0.1, 0.09, 0.15)
        val result = color(Vector3(1.0, 0.0, 0.0), native)
        assertTrue(result.x in 0.0..native.x && result.y in 0.0..native.y && result.z in 0.0..native.z)
        assertEquals(Vector3.ZERO, color(Vector3(1.0, 0.0, 0.0), Vector3.ZERO))
    }

    @Test fun twilightFadesSmoothlyAndFiniteEdgeShortensTheColouringColumn() {
        val above = color(Vector3(1.0, 1e-7, 0.0))
        val below = color(Vector3(1.0, -1e-7, 0.0))
        assertEquals(above.x, below.x, 1e-5)
        assertEquals(above.y, below.y, 1e-5)
        assertEquals(above.z, below.z, 1e-5)
        val centre = color(Vector3(1.0, 0.0, 0.0))
        val edge = color(Vector3(1.0, 0.0, 0.0), cloudPoint = Vector3(4999.0, 192.0, 0.0))
        assertTrue(edge.z > centre.z)
        val weak = Vector3(0.02, 0.005, 0.001)
        val fading = SurfaceCloudColor.tint(white, weak, -0.159999)
        assertEquals(white.z, fading.z, 1e-9)
    }
}
