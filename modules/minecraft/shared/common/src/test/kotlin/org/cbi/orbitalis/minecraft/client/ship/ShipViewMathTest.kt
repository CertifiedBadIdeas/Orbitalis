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

package org.cbi.orbitalis.minecraft.client.ship

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipViewMathTest {
    @Test fun wholeBoundingSphereFitsBothPortraitAndLandscapeFrames() {
        for (aspect in listOf(0.4, 1.0, 16.0 / 9.0, 3.0)) {
            for (radius in listOf(1.0, 10.0, 100.0)) {
                val distance = ShipViewMath.fitDistance(radius, 70.0, aspect)
                val occupied = asin(radius / distance)
                val vertical = Math.toRadians(35.0)
                assertTrue(occupied < vertical && occupied < atan(tan(vertical) * aspect))
            }
        }
    }

    @Test fun viewingDirectionIsWorldUprightAndZoomNeverEntersTheHull() {
        assertTrue((ShipViewMath.direction(0.0, 0.0) - Vector3(0.0, 0.0, 1.0)).length() < 1e-9)
        assertTrue(ShipViewMath.direction(20.0, 25.0).y < 0.0)
        assertEquals(1.0, ShipViewMath.zoom(1.0, 1000.0))
        assertEquals(8.0, ShipViewMath.zoom(8.0, -1000.0))
    }

    @Test fun navballSupportsVerticalInvertedAndSidewaysFlightWithoutEulerSingularities() {
        val centre = ShipViewMath.hemisphere(0.0, 0.0)!!
        assertEquals(1.0, centre.dot(Vector3(0.0, 0.0, 1.0)))
        assertEquals(-1.0, centre.dot(Vector3(0.0, 0.0, -1.0)))
        assertEquals(0.0, centre.dot(Vector3(1.0, 0.0, 0.0)))
        assertNull(ShipViewMath.hemisphere(1.0, 1.0))
        assertNull(ShipViewMath.marker(Vector3.ZERO))
        val velocity = Vector3(0.0, 0.0, 50.0)
        assertNotNull(ShipViewMath.marker(velocity))
        assertNull(ShipViewMath.marker(velocity * -1.0))
    }

    @Test
    fun rocketAndHorizontalShipAxesKeepNoseAndHorizonExplicit() {
        assertTrue((Vector3(0.0, 0.0, 1.0) - ShipViewMath.instrument(Vector3(0.0, 1.0, 0.0), true)).length() < 1e-12)
        assertEquals(Vector3(0.0, 1.0, 0.0), ShipViewMath.instrument(Vector3(0.0, 0.0, -1.0), true))
        assertTrue((Vector3(1.0, 0.0, 0.0) - ShipViewMath.instrument(Vector3(1.0, 0.0, 0.0), true)).length() < 1e-12)
        assertEquals(Vector3(0.0, 1.0, 0.0), ShipViewMath.instrument(Vector3(0.0, 1.0, 0.0), false))
        val unit =
            org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
                .gravityField(1.0)
        val native =
            org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
                .gravityField(11.0)
        for (point in listOf(Vector3(0.0, 81.0, 0.0), Vector3(800.0, 964.0, 500.0), Vector3(0.0, -964.0, 0.0))) {
            assertTrue((unit.acceleration(point).normalized() - native.acceleration(point).normalized()).length() < 1e-10)
        }
    }
}
