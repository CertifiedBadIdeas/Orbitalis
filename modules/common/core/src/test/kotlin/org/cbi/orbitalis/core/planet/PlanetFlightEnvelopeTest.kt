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

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanetFlightEnvelopeTest {
    @Test fun distanceAndArrivalWorkOnEveryFaceAndAtCorners() {
        for (axis in 0..2) {
            for (sign in listOf(-1.0, 1.0)) {
                fun point(distance: Double): Vector3 {
                    val v = sign * ((if (axis == 1) 64.0 else 5000.0) + distance)
                    return when (axis) {
                        0 -> Vector3(v, 0.0, 0.0)
                        1 -> Vector3(0.0, v, 0.0)
                        else -> Vector3(0.0, 0.0, v)
                    }
                }
                val outward = (point(3000.0) - point(2900.0))
                assertEquals(2900.0, PlanetFlightEnvelope.distance(point(2900.0)))
                assertEquals(1.0, PlanetFlightEnvelope.arrival(point(2900.0), outward, Vector3.ZERO, true, 5.0)!!, 1e-5)
                assertEquals(1.0, PlanetFlightEnvelope.arrival(point(2100.0), outward * -1.0, Vector3.ZERO, false, 5.0)!!, 1e-5)
            }
        }
        assertEquals(sqrt(3.0) * 1000.0, PlanetFlightEnvelope.distance(Vector3(6000.0, 1064.0, 6000.0)), 1e-8)
        val corner = Vector3(7000.0, 64.0, 7000.0)
        val eta = (3000.0 / sqrt(2.0) - 2000.0) / 100.0
        assertEquals(eta, PlanetFlightEnvelope.arrival(corner, Vector3(100.0, 0.0, 100.0), Vector3.ZERO, true, 5.0)!!, 1e-5)
    }

    @Test fun accelerationHysteresisAndGrazingApproachStayBounded() {
        val p = Vector3(7900.0, 0.0, 0.0)
        assertEquals(1.0, PlanetFlightEnvelope.arrival(p, Vector3.ZERO, Vector3(200.0, 0.0, 0.0), true, 5.0)!!, 1e-5)
        assertNull(PlanetFlightEnvelope.arrival(p, Vector3(-100.0, 0.0, 0.0), Vector3.ZERO, true, 5.0))
        assertNull(PlanetFlightEnvelope.arrival(p, Vector3(10.0, 0.0, 0.0), Vector3.ZERO, true, 5.0))
        assertEquals(0.0, PlanetFlightEnvelope.arrival(Vector3(8001.0, 0.0, 0.0), Vector3.ZERO, Vector3.ZERO, true, 5.0))
        assertEquals(0.0, PlanetFlightEnvelope.arrival(Vector3(6999.0, 0.0, 0.0), Vector3.ZERO, Vector3.ZERO, false, 5.0))
        assertNull(PlanetFlightEnvelope.arrival(Vector3(7500.0, 0.0, 0.0), Vector3.ZERO, Vector3.ZERO, true, 5.0))
        assertNull(PlanetFlightEnvelope.arrival(Vector3(7500.0, 0.0, 0.0), Vector3.ZERO, Vector3.ZERO, false, 5.0))
        // A short entry near a rounded corner is not visible from endpoint signs alone.
        val start = Vector3(6900.0, 0.0, 6100.0)
        val arrival = PlanetFlightEnvelope.arrival(start, Vector3(0.0, 0.0, -4000.0), Vector3.ZERO, false, 5.0)
        assertEquals((1100.0 - sqrt(390000.0)) / 4000.0, arrival!!, 1e-5)
    }
}
