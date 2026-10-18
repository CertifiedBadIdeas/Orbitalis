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
import kotlin.test.Test
import kotlin.test.assertTrue

class LunarDynamicsTest {
    @Test
    fun `surface compatibility supports fixed lunar ground and retains Coriolis for motion`() {
        val profile = SolarSystemProfile(11.0)
        for (tick in 0L..100000L step 911) {
            val field = CelestialDynamics(profile, tick)
            val resting = CartesianState(Vector3(0.0, 100.0, 0.0), Vector3.ZERO)
            val a = field.lunarAcceleration(resting)
            assertTrue((a - Vector3(0.0, -11.0 / 6.0, 0.0)).length() < 1e-9)
            val moving = resting.copy(velocity = Vector3(30.0, 0.0, 0.0))
            val frame = profile.lunarSurfaceFrame(tick)
            val omega = frame.orientation.inverse(frame.angularVelocity)
            val expected = a - omega.cross(moving.velocity) * 2.0
            assertTrue((field.lunarAcceleration(moving) - expected).length() < 1e-9)
            assertTrue((field.acceleration(Vector3(100.0, 100.0, 100.0)) - Vector3(0.0, -11.0, 0.0)).length() < 1e-9)
        }
    }

    @Test
    fun `moving lunar field samples absolute time rather than call order`() {
        val profile = SolarSystemProfile(11.0)
        val a = CelestialDynamics(profile, 1500)
        val b = CelestialDynamics(profile, 1700)
        val point = Vector3(12000.0, 8000.0, 0.0)
        assertTrue((a.acceleration(point, 10.0) - b.acceleration(point)).length() < 1e-10)
        assertTrue((a.acceleration(point) - a.acceleration(point, 100.0)).length() > 1e-5)
    }

    @Test
    fun `coast preview intersects the moving lunar surface at several orbital phases`() {
        val profile = SolarSystemProfile(11.0)
        for (epoch in listOf(0L, 6000L, 30000L)) {
            val frame = profile.lunarSurfaceFrame(epoch)
            val start = frame.toParent(CartesianState(Vector3(0.0, 300.0, 0.0), Vector3(0.0, -20.0, 0.0)))
            val result =
                CoastPrediction.predict(
                    profile.earthGravity,
                    start,
                    CoastPrediction.Limits(horizon = 30.0),
                    CelestialDynamics(profile, epoch),
                )
            assertTrue(result.end == CoastPrediction.End.BODY_INTERSECTION)
            val impact = profile.lunarSurfaceFrame(epoch, result.terminal.seconds).fromParent(result.terminal.state)
            assertTrue(kotlin.math.abs(impact.position.y - 89.0) < 0.1)
        }
    }
}
