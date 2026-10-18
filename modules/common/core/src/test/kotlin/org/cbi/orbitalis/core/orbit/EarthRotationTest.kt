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
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarthRotationTest {
    private val profile = SolarSystemProfile(11.0, earthSpinEpochTick = 1000L)

    @Test fun `twenty minute spin rotates around an in-plane axis without retuning lunar phase`() {
        val native = Vector3(2000.0, 64.0, 300.0)
        val start = profile.earthSurfaceFrame(1000)
        assertTrue((start.orientation.apply(native) - native).length() < 1e-10)
        val quarter = profile.earthSurfaceFrame(7000)
        assertTrue((quarter.orientation.apply(native) - Vector3(-64.0, 2000.0, 300.0)).length() < 1e-9)
        assertTrue((profile.earthSurfaceFrame(25000).orientation.apply(native) - native).length() < 1e-9)
        assertEquals(Vector3(0.0, 0.0, 2.0 * PI / 1200.0), start.angularVelocity)
        val legacy = profile.copy(earthSpinEpochTick = null)
        assertEquals(Rotation3.IDENTITY, legacy.earthSurfaceFrame(7000).orientation)
        assertEquals(profile.lunarSurfaceFrame(7000), legacy.lunarSurfaceFrame(7000))
        assertEquals(quarter, profile.retune(32.0, 7000).earthSurfaceFrame(7000))
    }

    @Test fun `ground speed and acceleration are derivatives of the same rotating frame`() {
        val ground = CartesianState(Vector3(4000.0, 100.0, 200.0), Vector3.ZERO)
        for (tick in listOf(1000L, 7000L, 19000L, Long.MAX_VALUE - 1L)) {
            val frame = profile.earthSurfaceFrame(tick)
            val state = frame.toParent(ground)
            val omega = 2.0 * PI / 1200.0
            assertEquals(omega * kotlin.math.hypot(4000.0, 100.0), state.velocity.length(), 1e-10)
            assertTrue((frame.fromParent(state).velocity - Vector3.ZERO).length() < 1e-10)
            val a = profile.earthSurfaceFrame(tick, -0.001).toParent(ground)
            val b = profile.earthSurfaceFrame(tick, 0.001).toParent(ground)
            assertTrue(((b.position - a.position) * 500.0 - state.velocity).length() < 1e-6)
            assertTrue(((b.velocity - a.velocity) * 500.0 - frame.toParentAcceleration(ground, Vector3.ZERO)).length() < 1e-8)
        }
    }

    @Test fun `ground compatibility and Coriolis follow native Earth axes at every phase`() {
        val still = CartesianState(Vector3(1200.0, 100.0, 200.0), Vector3.ZERO)
        for (tick in listOf(1000L, 7000L, 13000L, 19000L)) {
            val field = CelestialDynamics(profile, tick)
            assertTrue((field.earthAcceleration(still) - Vector3(0.0, -11.0, 0.0)).length() < 1e-9)
            val moving = still.copy(velocity = Vector3(20.0, 0.0, 0.0))
            val expected = Vector3(0.0, -11.0 - 2.0 * (2.0 * PI / 1200.0) * 20.0, 0.0)
            assertTrue((field.earthAcceleration(moving) - expected).length() < 1e-9)
        }
    }

    @Test fun `native handoff preserves root position velocity direction and angular motion`() {
        val native = CartesianState(Vector3(1234.0, 3100.0, -500.0), Vector3(40.0, 100.0, 3.0))
        for (tick in listOf(1000L, 7000L, 13000L, 19000L)) {
            val frames = SurfaceFlightFrames(profile, tick)
            val earth = SurfaceFlightFrames.World.EARTH
            val space = SurfaceFlightFrames.World.SPACE
            val moved = frames.state(native, earth, space)
            val returned = frames.state(moved, space, earth)
            assertTrue((returned.position - native.position).length() < 1e-9)
            assertTrue((returned.velocity - native.velocity).length() < 1e-9)
            val root = frames.frame(space).toParent(moved)
            val expected = frames.frame(earth).toParent(native)
            assertTrue((root.position - expected.position).length() < 1e-9)
            assertTrue((root.velocity - expected.velocity).length() < 1e-9)
            val spin = frames.angularVelocity(Vector3.ZERO, earth, space)
            assertTrue((spin - Vector3(0.0, 0.0, 2.0 * PI / 1200.0)).length() < 1e-12)
            assertTrue(
                (
                    frames.direction(Vector3(0.0, 1.0, 0.0), earth, space) -
                        frames.frame(earth).orientation.yAxis
                ).length() < 1e-12,
            )
        }
    }

    @Test fun `tidally locked Moon follows the Earth centre through many independent Earth rotations`() {
        for (tick in 0L..150000L step 3599L) {
            val frame = profile.lunarSurfaceFrame(tick, 0.025)
            val facing = frame.orientation.yAxis
            assertTrue((facing + frame.origin.position.normalized()).length() < 1e-12)
        }
    }

    @Test fun `coast intersects rotated Earth instead of its former axis aligned box`() {
        val dynamic = profile.copy(earthSpinEpochTick = 0)
        val sources = CelestialDynamics(dynamic, 6000)
        val state = CartesianState(Vector3(70.0, 2000.0, 0.0), Vector3(-100.0, 0.0, 0.0))
        val limits = CoastPrediction.Limits(horizon = 0.2, step = 0.01, detectEntry = false)
        val prediction = CoastPrediction.predict(sources.earth, state, limits, sources)
        assertEquals(CoastPrediction.End.BODY_INTERSECTION, prediction.end)
        assertTrue(prediction.points.last().seconds in 0.03..0.12)
        val legacy = CelestialDynamics(dynamic.copy(earthSpinEpochTick = null), 6000)
        assertEquals(CoastPrediction.End.HORIZON, CoastPrediction.predict(legacy.earth, state, limits, legacy).end)
    }

    @kotlin.test.Test fun `inertial insertion clears the rotating slab for a full free turn`() {
        val profile = SolarSystemProfile(11.0, earthSpinEpochTick = 0L)
        for (tick in listOf(13L, 6000L, 17317L, 23001L)) {
            val model = CelestialDynamics(profile, tick)
            val field = profile.earthGravity
            val orbit =
                org.cbi.orbitalis.core.planet.BallisticOrbit
                    .firstRevolution(field, model, clearance = 64.0)
            assertTrue(orbit.minimumClearance >= 64.0)
            assertTrue(orbit.period > 20.0 && orbit.period < 3600.0)
            assertTrue(orbit.stateAt(orbit.period + 1.0) == orbit.stateAt(orbit.period))
            println(
                "ROTATING_EARTH_INSERTION tick=$tick period=${orbit.period} speed=${orbit.initial.velocity.length()} clearance=${orbit.minimumClearance}",
            )
        }
    }
}
