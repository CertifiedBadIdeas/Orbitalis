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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpaceTestFrameTest {
    @Test
    fun `full orbit stays above Minecraft floor without changing the physical field`() {
        val centre = Vector3(0.0, -5936.0, 0.0)
        val gravity = CubicGravity(6000.0, 9.81, 64.0, 512.0, 6000.0)
        val radius = 18000.0
        for (step in 0..360) {
            val angle = step * PI / 180.0
            val radial = Vector3(radius * sin(angle), radius * cos(angle), 0.0)
            val physical = centre + radial
            val local = SpaceTestFrame.toSpace(physical)
            assertTrue(local.y >= 8063.99)
            assertTrue((SpaceTestFrame.toSurface(local) - physical).length() < 1e-10)
            assertTrue(gravity.altitude(radial) >= 6000.0)
            val expected = radial * (-9.81 * 6000.0 * 6000.0 / (radius * radius * radius))
            assertTrue((gravity.acceleration(radial) - expected).length() < 1e-10)
            assertTrue((gravity.acceleration(local - SpaceTestFrame.toSpace(centre)) - expected).length() < 1e-10)
        }
        assertEquals(Vector3(0.0, 256.0, 0.0), OrbitalTestFrame.origin)
    }
}
