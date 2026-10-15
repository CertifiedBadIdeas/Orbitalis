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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OrbitApsidesTest {
    private val gravity = TwoBodyGravity(1024.0)
    private val circular = CartesianState(Vector3(16.0, 0.0, 0.0), Vector3(0.0, 0.0, 8.0))

    @Test
    fun `circular orbit has equal apsides`() {
        val orbit = assertNotNull(gravity.apsides(circular))
        assertEquals(16.0, orbit.periapsis, 1e-6)
        assertEquals(16.0, orbit.apoapsis, 1e-6)
    }

    @Test
    fun `prograde raises apoapsis and retrograde lowers periapsis`() {
        val raised = assertNotNull(gravity.apsides(circular.copy(velocity = Vector3(0.0, 0.0, 9.0))))
        assertEquals(16.0, raised.periapsis, 1e-10)
        assertEquals(1296.0 / 47.0, raised.apoapsis, 1e-10)
        val lowered = assertNotNull(gravity.apsides(circular.copy(velocity = Vector3(0.0, 0.0, 7.0))))
        assertEquals(784.0 / 79.0, lowered.periapsis, 1e-10)
        assertEquals(16.0, lowered.apoapsis, 1e-10)
        // The same ellipse at another phase has the same apsides.
        var state = circular.copy(velocity = Vector3(0.0, 0.0, 9.0))
        repeat(1000) { state = gravity.step(state, 0.001) }
        assertEquals(raised.periapsis, gravity.apsides(state)!!.periapsis, 1e-5)
        assertEquals(raised.apoapsis, gravity.apsides(state)!!.apoapsis, 1e-5)
    }

    @Test
    fun `escape has no finite ellipse and radial fall reaches centre`() {
        assertNull(gravity.apsides(circular.copy(velocity = Vector3(0.0, 0.0, 12.0))))
        val radial = assertNotNull(gravity.apsides(circular.copy(velocity = Vector3.ZERO)))
        assertEquals(0.0, radial.periapsis)
        assertEquals(16.0, radial.apoapsis)
        assertFailsWith<IllegalArgumentException> { gravity.apsides(circular.copy(position = Vector3.ZERO)) }
    }
}
