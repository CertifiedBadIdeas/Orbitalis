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

package org.cbi.orbitalis.minecraft.lighting

import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkyLightingTest {
    @Test fun `noon dusk midnight and dawn angles agree with native daylight cosine`() {
        for (direction in listOf(Vector3(0.0, 1.0, 0.0), Vector3(-1.0, 0.0, 0.0), Vector3(0.0, -1.0, 0.0), Vector3(1.0, 0.0, 0.0))) {
            val angle = SkyLighting.daylightAngle(direction)
            assertTrue(angle >= 0f && angle < 1f)
            assertEquals(direction.y, cos(angle * 2.0 * PI), 1e-6)
        }
    }

    @Test fun `planet normal and native sky see the same physical sun through rotation`() {
        val profile = SolarSystemProfile(11.0, earthPhase = 0.47, earthSpinEpochTick = 0L)
        for (tick in listOf(0L, 6000L, 12000L, 18000L, 24000L, 1000000L)) {
            val local = SkyLighting.earthDirection(profile, tick, 0.023)
            val frame = profile.earthSurfaceFrame(tick, 0.023)
            val root = SkyLighting.direction(profile, tick, 0.023, Vector3.ZERO)
            assertTrue((frame.orientation.apply(local) - root).length() < 1e-10)
            assertEquals(root.dot(frame.orientation.apply(Vector3(0.0, 1.0, 0.0))), local.y, 1e-10)
        }
        assertTrue((SkyLighting.earthDirection(profile, 0L) - SkyLighting.earthDirection(profile, 12000L)).length() > 1.9)
    }

    @Test fun `requested solar times always advance to the next matching event`() {
        val profile = SolarSystemProfile(11.0, earthPhase = 0.7, earthSpinEpochTick = 0L)
        for (tick in listOf(0L, 12000L, 23999L, 10000000L)) {
            for (daytime in listOf(0, 1000, 6000, 12000, 13000, 18000, 23999)) {
                val delta = ForwardSkyTime.ticksUntil(profile, tick, daytime)
                assertTrue(delta in 1..24000)
                val actual = SkyLighting.daylightAngle(SkyLighting.earthDirection(profile, tick + delta))
                val target = ((0.25 - daytime / 24000.0) % 1.0 + 1.0) % 1.0
                val error = ((actual - target + 1.5) % 1.0) - 0.5
                assertTrue(kotlin.math.abs(error) <= 1.1 / 24000.0, "Forward event did not match: $tick $daytime $delta $error")
            }
        }
    }
}
