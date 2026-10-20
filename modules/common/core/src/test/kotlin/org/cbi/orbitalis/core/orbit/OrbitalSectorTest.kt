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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.*

class OrbitalSectorTest {
    @Test fun canonicalAddressesRejectAliasesAndOverflow() {
        for (sector in listOf(OrbitalSector(0, 0, 0), OrbitalSector(-21, 7, 98), OrbitalSector(1000000, -1000000, 4))) {
            assertEquals(sector, OrbitalSector.parse(sector.path))
        }
        for (path in listOf("sector/00_0_0", "sector/-0_0_0", "sector/1000001_0_0", "sector/99999999999_0_0", "sector/1_2", "space")) {
            assertNull(OrbitalSector.parse(path))
        }
        assertFailsWith<IllegalArgumentException> { OrbitalSector.containing(Vector3(Double.NaN, 0.0, 0.0)) }
    }

    @Test fun heliocentricChartRoundTripsPositionMotionAndAcceleration() {
        val profile = SolarSystemProfile(11.0, selectedEarthWidth = 20000)
        val tick = 47123L
        val sector = OrbitalSector(24, -18, 4)
        val chart = sector.frame(profile, tick)
        val local = CartesianState(OrbitalSector.NATIVE_CENTRE + Vector3(25.125, -93.5, 4.25), Vector3(13.0, -8.0, 0.25))
        val physical = chart.toParent(local)
        val earth = profile.earthOrbit.sample(tick)
        assertTrue((physical.position + earth.origin.position - sector.origin - Vector3(25.125, -93.5, 4.25)).length() < 1e-7)
        val restored = chart.fromParent(physical)
        assertTrue((restored.position - local.position).length() < 1e-7)
        assertTrue((restored.velocity - local.velocity).length() < 1e-10)
        val force = Vector3(1.0, 2.0, 3.0)
        assertTrue((chart.fromParentAcceleration(physical, chart.toParentAcceleration(local, force)) - force).length() < 1e-12)
        // A heliocentric inertial chart cancels the geocentric origin's solar acceleration.
        assertTrue((chart.originAcceleration + earth.originAcceleration).length() < 1e-12)
    }

    @Test fun adjacentChartHandoffKeepsSmallNativeCoordinatesAndContinuousMotion() {
        val profile = SolarSystemProfile(11.0)
        val frames = SurfaceFlightFrames(profile, 3000)
        val first = OrbitalSector(20, 0, 0)
        val p = first.origin + Vector3(OrbitalSector.WIDTH * 0.5 + OrbitalSector.HYSTERESIS + 1.0, 0.0, 0.0)
        assertFalse(first.retains(p))
        val next = OrbitalSector.containing(p)
        assertEquals(OrbitalSector(21, 0, 0), next)
        assertTrue(next.retains(p))
        val root =
            CartesianState(
                p -
                    profile.earthOrbit
                        .sample(frames.tick)
                        .origin.position,
                Vector3(321.0, -45.0, 9.0),
            )
        val target = frames.orbitalDestination(root, FlightFrame.Sector(first))
        assertEquals(FlightFrame.Sector(next), target)
        val local = frames.frame(target).fromParent(root)
        assertTrue(local.position.length() < 2e6)
        assertTrue((frames.frame(target).toParent(local).velocity - root.velocity).length() < 1e-10)
    }

    @Test fun oldEarthAndMoonChartsAndNearEarthSpaceStayUnchanged() {
        val frames = SurfaceFlightFrames(SolarSystemProfile(11.0), 0)
        assertEquals(frames.frame(SurfaceFlightFrames.World.EARTH), frames.frame(FlightFrame.Surface(SolarSystemProfile.EARTH)))
        assertEquals(frames.frame(SurfaceFlightFrames.World.MOON), frames.frame(FlightFrame.Surface(SolarSystemProfile.MOON)))
        assertEquals(frames.frame(SurfaceFlightFrames.World.SPACE), frames.frame(FlightFrame.LegacySpace))
        assertEquals(
            FlightFrame.LegacySpace,
            frames.orbitalDestination(CartesianState(Vector3(0.0, 5000.0, 0.0), Vector3.ZERO), FlightFrame.Sector(OrbitalSector(0, 0, 0))),
        )
    }
}
