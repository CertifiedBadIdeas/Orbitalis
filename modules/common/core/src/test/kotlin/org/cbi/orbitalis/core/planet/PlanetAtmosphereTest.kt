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
import kotlin.test.assertTrue

class PlanetAtmosphereTest {
    @Test
    fun `air ends continuously at the ascent height and outside the footprint`() {
        assertEquals(0.0, PlanetAtmosphere.vacuum(Vector3(4500.0, 320.0, -4500.0)))
        assertEquals(1.0, PlanetAtmosphere.vacuum(Vector3(0.0, 3064.0, 0.0)))
        assertEquals(1.0, PlanetAtmosphere.vacuum(Vector3(5300.0, 64.0, 0.0)))
        assertEquals(1.0, PlanetAtmosphere.vacuum(Vector3(0.0, -400.0, 0.0)))
        var previous = 0.0
        for (y in 320..3064) {
            val vacuum = PlanetAtmosphere.vacuum(Vector3(0.0, y.toDouble(), 0.0))
            assertTrue(vacuum >= previous && vacuum <= 1.0)
            previous = vacuum
        }
        assertTrue(PlanetAtmosphere.vacuum(Vector3(0.0, 3063.999, 0.0)) > 0.999999)
    }
}
