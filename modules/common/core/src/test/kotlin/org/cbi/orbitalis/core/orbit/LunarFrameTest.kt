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
import kotlin.test.Test
import kotlin.test.assertTrue

class LunarFrameTest {
    @Test
    fun `surface normal faces Earth over the complete orbit and native states round trip`() {
        val profile = SolarSystemProfile(11.0)
        val local = CartesianState(Vector3(237.0, 500.0, -316.0), Vector3(12.0, -3.0, 5.0))
        for (tick in 0L..100000L step 137) {
            val frame = profile.lunarSurfaceFrame(tick)
            assertTrue(frame.orientation.yAxis.dot(frame.origin.position.normalized()) < -0.999999999)
            val restored = frame.fromParent(frame.toParent(local))
            assertTrue((restored.position - local.position).length() < 1e-8)
            assertTrue((restored.velocity - local.velocity).length() < 1e-8)
            val omega = Vector3(0.1, -0.2, 0.3)
            assertTrue((frame.fromParentAngularVelocity(frame.toParentAngularVelocity(omega)) - omega).length() < 1e-10)
        }
    }

    @Test
    fun `stationary ground point derivatives include synchronous spin`() {
        val profile = SolarSystemProfile(11.0)
        val ground = CartesianState(Vector3(1000.0, 64.0, 0.0), Vector3.ZERO)
        for (tick in listOf(1000L, 5000L, 15000L)) {
            val frame = profile.lunarSurfaceFrame(tick)
            val a = profile.lunarSurfaceFrame(tick - 1).toParent(ground)
            val b = profile.lunarSurfaceFrame(tick + 1).toParent(ground)
            val middle = frame.toParent(ground)
            assertTrue(((b.position - a.position) * 10.0 - middle.velocity).length() < 1e-5)
            assertTrue(((b.velocity - a.velocity) * 10.0 - frame.toParentAcceleration(ground, Vector3.ZERO)).length() < 1e-6)
        }
    }

    @Test
    fun `moving lunar entry uses surface relative descent and hysteresis after handoff`() {
        val frames = SurfaceFlightFrames(SolarSystemProfile(11.0), 12345)
        val ground = CartesianState(Vector3(113.0, 450.0, -372.0), Vector3(1.0, -12.0, 0.0))
        val space = frames.state(ground, SurfaceFlightFrames.World.MOON, SurfaceFlightFrames.World.SPACE)
        kotlin.test.assertEquals(SurfaceFlightFrames.World.MOON, frames.destination(space, SurfaceFlightFrames.World.SPACE))
        kotlin.test.assertNull(frames.destination(ground, SurfaceFlightFrames.World.MOON))
        val rising = ground.copy(position = Vector3(113.0, 665.0, -372.0), velocity = Vector3(0.0, 12.0, 0.0))
        kotlin.test.assertEquals(SurfaceFlightFrames.World.SPACE, frames.destination(rising, SurfaceFlightFrames.World.MOON))
        kotlin.test.assertNull(
            frames.destination(
                frames.state(rising, SurfaceFlightFrames.World.MOON, SurfaceFlightFrames.World.SPACE),
                SurfaceFlightFrames.World.SPACE,
            ),
        )
        val restored = frames.state(space, SurfaceFlightFrames.World.SPACE, SurfaceFlightFrames.World.MOON)
        assertTrue((restored.position - ground.position).length() < 1e-8)
        assertTrue((restored.velocity - ground.velocity).length() < 1e-8)
    }
}
