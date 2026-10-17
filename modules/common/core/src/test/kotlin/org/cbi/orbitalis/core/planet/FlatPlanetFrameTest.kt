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

class FlatPlanetFrameTest {
    @Test
    fun `surface and space translations round trip without changing horizontal coordinates`() {
        for (position in listOf(Vector3.ZERO, Vector3(-999.0, -64.0, 999.0), Vector3(1000.0, 1064.0, -500.0))) {
            val space = FlatPlanetFrame.toSpace(position)
            assertEquals(position.x, space.x)
            assertEquals(position.z, space.z)
            assertEquals(position.y + 4096.0, space.y)
            assertEquals(position, FlatPlanetFrame.toSurface(space))
        }
    }
}
