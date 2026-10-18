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

package org.cbi.orbitalis.minecraft.client.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeldFrameTimingTest {
    @Test fun waitsForStableReadinessThenRevealsSmoothly() {
        val view = HeldFrameTiming()
        assertEquals(0f, view.opacity(0.0, true))
        view.begin(1.0)
        assertEquals(1f, view.opacity(2.0, false))
        assertEquals(1f, view.opacity(2.0, true))
        assertEquals(1f, view.opacity(2.19, true))
        val halfway = view.opacity(2.325, true)
        assertTrue(halfway in 0.499f..0.501f)
        assertEquals(0f, view.opacity(2.46, true))
        assertFalse(view.active)
    }

    @Test fun lostReadinessRestartsTheSettleWindowAndTimeoutCannotBeExtended() {
        val view = HeldFrameTiming()
        view.begin(1.0)
        view.opacity(2.0, true)
        assertTrue(view.opacity(2.3, true) < 1f)
        assertEquals(1f, view.opacity(2.31, false))
        assertEquals(1f, view.opacity(2.4, true))
        assertEquals(1f, view.opacity(2.59, true))
        assertEquals(0f, view.opacity(11.0, false))
        assertFalse(view.active)
        view.begin(12.0)
        view.clear()
        assertEquals(0f, view.opacity(12.1, true))
    }
}
