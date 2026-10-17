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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AltitudeArrivalTest {
    @Test
    fun `arrival uses acceleration braking and directed roots`() {
        assertEquals(5.0, AltitudeArrival.seconds(0.0, 0.0, 8.0, 100.0, true, 10.0))
        assertEquals(4.0, AltitudeArrival.seconds(100.0, -5.0, 0.0, 80.0, false, 10.0))
        assertEquals(1.0, AltitudeArrival.seconds(0.0, 20.0, -10.0, 15.0, true, 10.0))
        assertEquals(3.0, AltitudeArrival.seconds(0.0, 20.0, -10.0, 15.0, false, 10.0))
        assertNull(AltitudeArrival.seconds(0.0, 20.0, -10.0, 30.0, true, 10.0))
        assertNull(AltitudeArrival.seconds(0.0, 1.0, 0.0, 100.0, true, 10.0))
        assertEquals(0.0, AltitudeArrival.seconds(100.0, 0.0, 0.0, 80.0, true, 10.0))
    }

    @Test
    fun `ten kilometre model has proportional bands and a safe numerical orbit`() {
        assertEquals(10000.0, FlatPlanetDefinition.SIZE)
        assertEquals(3000.0, FlatPlanetDefinition.ASCENT_HEIGHT)
        assertEquals(2000.0, FlatPlanetDefinition.DESCENT_HEIGHT)
        val field = SurfaceGravityLayer(RectangularGravity(5000.0, 64.0, 5000.0, 11.0), 11.0, fullHeight = 3000.0)
        for (diagonal in listOf(false, true)) {
            val orbit = BallisticOrbit.plan(field, 5000.0, diagonal = diagonal)
            kotlin.test.assertTrue(orbit.minimumClearance >= 32.0)
            println("TEN_KM_ORBIT diagonal=$diagonal period=${orbit.period} velocity=${orbit.initial.velocity.length()}")
        }
    }
}
