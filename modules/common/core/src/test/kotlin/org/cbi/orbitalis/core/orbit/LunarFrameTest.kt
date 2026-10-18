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
    @Test fun `small bodies keep a native approach floor and hysteresis gap`() {
        val half = Vector3(100.0, 64.0, 100.0)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            org.cbi.orbitalis.core.planet
                .BodyFlightEnvelope(half, 600.0, 400.0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            org.cbi.orbitalis.core.planet
                .BodyFlightEnvelope(half, 1600.0, 1500.0)
        }
        val envelope = org.cbi.orbitalis.core.planet.PlanetFlightEnvelope.moon
        kotlin.test.assertTrue(envelope.descentDistance >= 1500.0)
        kotlin.test.assertTrue(envelope.ascentDistance - envelope.descentDistance >= 300.0)
    }

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
        val rising = ground.copy(position = Vector3(113.0, 1865.0, -372.0), velocity = Vector3(0.0, 12.0, 0.0))
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

    @Test
    fun `every lunar face and rounded corner uses the same moving entry exit envelope`() {
        val envelope = org.cbi.orbitalis.core.planet.PlanetFlightEnvelope.moon
        val normals =
            listOf(
                Vector3(1.0, 0.0, 0.0),
                Vector3(-1.0, 0.0, 0.0),
                Vector3(0.0, 1.0, 0.0),
                Vector3(0.0, -1.0, 0.0),
                Vector3(0.0, 0.0, 1.0),
                Vector3(0.0, 0.0, -1.0),
                Vector3(1.0, -1.0, 1.0).normalized(),
            )
        for (tick in listOf(0L, 6000L, 30000L)) {
            val frames = SurfaceFlightFrames(SolarSystemProfile(11.0), tick)
            for (normal in normals) {
                val surface = Vector3(normal.x.signExtent(1400.0), normal.y.signExtent(64.0), normal.z.signExtent(1400.0))

                fun local(distance: Double) = CartesianState(surface + normal * distance, normal * -100.0)

                fun route(distance: Double) =
                    frames.destination(
                        frames.state(
                            local(distance),
                            SurfaceFlightFrames.World.MOON,
                            SurfaceFlightFrames.World.SPACE,
                        ),
                        SurfaceFlightFrames.World.SPACE,
                    )
                kotlin.test.assertEquals(SurfaceFlightFrames.World.MOON, route(1499.0))
                kotlin.test.assertNull(route(1501.0))
                kotlin.test.assertNull(frames.destination(local(1799.0), SurfaceFlightFrames.World.MOON))
                kotlin.test.assertEquals(SurfaceFlightFrames.World.SPACE, frames.destination(local(1801.0), SurfaceFlightFrames.World.MOON))
                kotlin.test.assertEquals(1.0, envelope.arrival(local(1600.0).position, normal * -100.0, Vector3.ZERO, false, 5.0)!!, 1e-5)
                kotlin.test.assertEquals(1.0, envelope.arrival(local(1700.0).position, normal * 100.0, Vector3.ZERO, true, 5.0)!!, 1e-5)
            }
        }
    }

    private fun Double.signExtent(extent: Double): Double =
        when {
            this > 0.0 -> extent
            this < 0.0 -> -extent
            else -> 0.0
        }
}
