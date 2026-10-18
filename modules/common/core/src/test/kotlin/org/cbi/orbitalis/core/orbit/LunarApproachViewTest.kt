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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LunarApproachViewTest {
    private val up = Vector3(0.0, 1.0, 0.0)

    @Test fun `approach horizon matches across top edge and underside handoffs at many phases`() {
        val profile = SolarSystemProfile(11.0, earthSpinEpochTick = 0L)
        for (tick in 0L..60000L step 977) {
            val frames = SurfaceFlightFrames(profile, tick)
            val reference = LunarApproachView()
            for (local in listOf(
                Vector3(0.0, 1560.0, 0.0),
                Vector3(2895.0, 0.0, 0.0),
                Vector3(0.0, -1560.0, 0.0),
                Vector3(0.0, 1865.0, 0.0),
                Vector3(3210.0, 0.0, 0.0),
                Vector3(0.0, -1865.0, 0.0),
            )) {
                val root = frames.point(local, SurfaceFlightFrames.World.MOON, SurfaceFlightFrames.World.SPACE)
                val before = reference.up(frames, root, SurfaceFlightFrames.World.SPACE)
                val after = reference.up(frames, local, SurfaceFlightFrames.World.MOON)
                assertTrue(
                    (frames.direction(before, SurfaceFlightFrames.World.SPACE, SurfaceFlightFrames.World.MOON) - after).length() < 1e-9,
                )
                assertTrue((after - up).length() < 1e-9)
            }
        }
    }

    @Test fun `distant view stays Earth upright and blend is continuous`() {
        val frames = SurfaceFlightFrames(SolarSystemProfile(11.0), 8000)
        val reference = LunarApproachView()
        var previous: Vector3? = null
        for (distance in (kotlin.math.ceil(frames.profile.moonHillRadius).toInt() + 10) downTo 1900 step 5) {
            val local = Vector3(0.0, 64.0 + distance, 0.0)
            val root = frames.point(local, SurfaceFlightFrames.World.MOON, SurfaceFlightFrames.World.SPACE)
            val now = reference.up(frames, root, SurfaceFlightFrames.World.SPACE)
            if (local.length() >= frames.profile.moonHillRadius) assertTrue((now - up).length() < 1e-9)
            previous?.let { assertTrue((now - it).length() < 0.01) }
            previous = now
        }
        assertTrue(LunarApproachView.roll(up, up, Vector3(-1.0, 0.0, 0.0)) == 0.0)
        assertTrue(LunarApproachView.roll(Vector3(0.0, 0.0, 1.0), up, Vector3(-1.0, 0.0, 0.0)).isFinite())
    }

    @Test fun `lunar camera never captures a view outside the lunar Hill sphere`() {
        val profile = SolarSystemProfile(11.0)
        for (tick in listOf(0L, 160000L, 330000L, 650000L)) {
            val frames = SurfaceFlightFrames(profile, tick)
            val view = LunarApproachView()
            for (axis in listOf(Vector3(1.0, 0.0, 0.0), up, Vector3(0.0, 0.0, 1.0))) {
                val outside = axis * (profile.moonHillRadius + 1.0)
                val root = frames.point(outside, SurfaceFlightFrames.World.MOON, SurfaceFlightFrames.World.SPACE)
                assertTrue((view.up(frames, root, SurfaceFlightFrames.World.SPACE) - up).length() < 1e-12)
            }
        }
        assertTrue(profile.moonHillRadius in 25000.0..35000.0)
        assertTrue(profile.earthHillRadius > profile.moonOrbitRadius + profile.moonHillRadius)
    }

    @Test fun `wire rotations reject shear reflection and scale`() {
        val x = Vector3(1.0, 0.0, 0.0)
        val z = Vector3(0.0, 0.0, 1.0)
        assertFailsWith<IllegalArgumentException> { Rotation3.fromAxes(x, up, z * -1.0) }
        assertFailsWith<IllegalArgumentException> { Rotation3.fromAxes(x, x, z) }
        assertFailsWith<IllegalArgumentException> { Rotation3.fromAxes(x * 2.0, up, z) }
    }

    @Test fun `player compatibility gravity is covariant at lunar entry and exit`() {
        val profile = SolarSystemProfile(11.0)
        for (tick in listOf(0L, 6000L, 12000L, 30000L)) {
            val dynamics = CelestialDynamics(profile, tick, 32.0, 32.0 / 6.0)
            val frame = profile.lunarSurfaceFrame(tick)
            for (p in listOf(Vector3(0.0, 1564.0, 0.0), Vector3(2900.0, 0.0, 0.0), Vector3(0.0, -1864.0, 0.0))) {
                val local = CartesianState(p, Vector3(120.0, -400.0, 30.0))
                val root = frame.toParent(local)
                val parent = frame.toParentAcceleration(local, dynamics.lunarAcceleration(local))
                assertTrue((parent - dynamics.acceleration(root.position)).length() < 1e-8)
            }
        }
    }

    @Test fun `mouse turns follow rolled screen axes`() {
        val look = Vector3(0.0, 0.0, 1.0)
        val left = Vector3(1.0, 0.0, 0.0)
        val normal = LunarApproachView.turnRay(look, up, left, 10.0, 10.0)
        assertTrue(normal.x < 0.0 && normal.y < 0.0)
        val rolled = LunarApproachView.turnRay(look, up * -1.0, left * -1.0, 10.0, 10.0)
        assertTrue(rolled.x > 0.0 && rolled.y > 0.0)
    }
}
