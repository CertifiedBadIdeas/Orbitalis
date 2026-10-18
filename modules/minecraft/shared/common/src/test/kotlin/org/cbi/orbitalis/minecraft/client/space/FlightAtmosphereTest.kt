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

import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightAtmosphere
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlightAtmosphereTest {
    @Test fun leavesGroundNativeAndCompletesBeforeEitherHandoff() {
        assertEquals(0f, FlightAtmosphere.vacuum(64.0))
        assertEquals(0f, FlightAtmosphere.vacuum(FlightAtmosphere.START))
        assertEquals(1f, FlightAtmosphere.vacuum(FlightAtmosphere.END))
        assertEquals(0f, FlightAtmosphere.vacuum(Double.NaN))
        for (height in listOf(
            FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT,
            FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT,
        )) {
            val point = Vector3(81.0, height, -94.0)
            assertEquals(1f, FlightAtmosphere.vacuum(point.y))
            assertEquals(
                FlightAtmosphere.vacuum(point.y),
                FlightAtmosphere.vacuum(FlatPlanetFrame.toSurface(FlatPlanetFrame.toSpace(point)).y),
            )
        }
    }

    @Test fun edgeAndUndersideReachVacuumWithoutChangingTheGround() {
        assertEquals(0f, FlightAtmosphere.vacuum(Vector3(4999.0, 64.0, 4999.0)))
        for (point in listOf(Vector3(5300.0, 64.0, 0.0), Vector3(-5300.0, 64.0, -5300.0), Vector3(0.0, -400.0, 0.0))) {
            assertEquals(1f, FlightAtmosphere.vacuum(point))
            assertEquals(FlightAtmosphere.vacuum(point), FlightAtmosphere.vacuum(FlatPlanetFrame.toSurface(FlatPlanetFrame.toSpace(point))))
        }
    }

    @Test fun blendIsMonotonicAndFlatAtBothEndpoints() {
        val values =
            (0..100).map {
                FlightAtmosphere.vacuum(FlightAtmosphere.START + (FlightAtmosphere.END - FlightAtmosphere.START) * it / 100)
            }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a && b - a < 0.016f })
        assertTrue(FlightAtmosphere.vacuum(FlightAtmosphere.START + 0.01) < 1e-8f)
        assertTrue(FlightAtmosphere.vacuum(FlightAtmosphere.END - 0.01) > 0.999999f)
    }
}
