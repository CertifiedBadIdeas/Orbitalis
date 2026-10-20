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
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtmosphereOpticsTest {
    @Test fun verticalSunColumnsRemainComparableAcrossCloudHeightsAndPlanetSizes() {
        for (halfSize in listOf(500.0, 1000.0, 2500.0, 5000.0)) {
            for (top in listOf(152.0, 216.0, 280.0)) {
                val color = AtmosphereOptics.sunTransmission(Vector3(0.0, 63.0, 0.0), Vector3(0.0, 1.0, 0.0), halfSize, 63.0, top)
                assertEquals(exp(-0.135), color.x, 1e-12)
                assertEquals(exp(-0.265), color.y, 1e-12)
                assertEquals(exp(-0.585), color.z, 1e-12)
            }
        }
    }

    @Test fun grazingSunIsWarmerButHorizontalLimitIsFiniteAndContinuous() {
        val point = Vector3(0.0, 64.0, 0.0)

        fun transmission(y: Double) = AtmosphereOptics.sunTransmission(point, Vector3(1.0, y, 0.0), 500.0, 63.0, 216.0)
        val horizon = transmission(0.0)
        val above = transmission(1e-10)
        val below = transmission(-1e-10)
        assertTrue(horizon.x > horizon.y && horizon.y > horizon.z)
        assertTrue(horizon.x > 0.0 && horizon.x < 1.0)
        assertEquals(horizon.x, above.x, 1e-8)
        assertEquals(horizon.x, below.x, 1e-8)
        val zenith = transmission(1.0)
        assertTrue(horizon.z / horizon.x < zenith.z / zenith.x)
    }

    @Test fun opaqueEarthBlocksTheSunButItsFiniteEdgeDoesNotCastAnInfiniteShadow() {
        val down = Vector3(0.0, -1.0, 0.0)
        assertEquals(Vector3.ZERO, AtmosphereOptics.sunTransmission(Vector3(0.0, 100.0, 0.0), down, 500.0, 63.0, 216.0, 60.0))
        val edge = AtmosphereOptics.sunTransmission(Vector3(530.0, 100.0, 0.0), down, 500.0, 63.0, 216.0, 60.0)
        assertTrue(edge.x > 0.0 && edge.z > 0.0)
    }

    @Test fun higherCloudsReceiveLessAttenuatedSunlight() {
        fun transmission(y: Double) = AtmosphereOptics.sunTransmission(Vector3(0.0, y, 0.0), Vector3(0.5, 0.5, 0.0), 5000.0, 63.0, 216.0)
        val low = transmission(64.0)
        val high = transmission(192.0)
        assertTrue(high.x > low.x && high.y > low.y && high.z > low.z)
    }

    @Test fun nearbySideBoundaryShortensTheGrazingOpticalPath() {
        fun transmission(x: Double) = AtmosphereOptics.sunTransmission(Vector3(x, 100.0, 0.0), Vector3(1.0, 0.0, 0.0), 5000.0, 63.0, 216.0)
        assertTrue(transmission(4999.0).z > transmission(0.0).z)
    }

    @Test fun analyticColumnsMatchIndependentMidpointIntegrationOnRisingAndFallingPaths() {
        for (vertical in listOf(-0.2, -1e-8, 0.0, 1e-8, 0.7)) {
            val altitude = 80.0
            val distance = 100.0
            val scaleHeight = 35.0
            val samples = 10000
            val numerical =
                (0 until samples).sumOf { index ->
                    exp(-(altitude + vertical * (index + 0.5) * distance / samples) / scaleHeight) * distance / samples
                }
            assertEquals(numerical, AtmosphereOptics.column(altitude, vertical, distance, scaleHeight), 1e-7)
        }
    }
}
