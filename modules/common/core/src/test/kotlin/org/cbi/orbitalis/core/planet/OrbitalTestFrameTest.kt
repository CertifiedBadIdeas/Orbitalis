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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrbitalTestFrameTest {
    @Test
    fun `representation round trip preserves position and central gravity`() {
        val centre = Vector3(0.0, -5936.0, 0.0)
        val gravity = SphericalGravity(6000.0, 9.81)
        val surface = Vector3(1000.0, 700.0, -400.0)
        val orbit = OrbitalTestFrame.toOrbit(surface)
        assertEquals(surface, OrbitalTestFrame.toSurface(orbit))
        assertEquals(gravity.acceleration(surface - centre), gravity.acceleration(orbit - OrbitalTestFrame.toOrbit(centre)))
    }

    @Test
    fun `patch edges are accepted and distant landing locations are rejected`() {
        OrbitalTestFrame.requirePatch(Vector3(2048.0, 600.0, -2048.0))
        assertFailsWith<IllegalArgumentException> { OrbitalTestFrame.requirePatch(Vector3(2048.001, 600.0, 0.0)) }
    }
}
