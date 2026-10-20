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

package org.cbi.orbitalis.minecraft.flight

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientEphemerisClockTest {
    @Test fun `early and late snapshots preserve the displayed epoch and converge without reversing`() {
        val clock = ClientEphemerisClock()
        clock.accept(5000, true)
        repeat(4) { clock.advance() }

        fun shown(partial: Float) = clock.tick / 20.0 + clock.renderSeconds(partial)
        for (correction in listOf(1L, -1L, 2L, -2L)) {
            val before = shown(0.4f)
            clock.accept(clock.tick + correction, true)
            assertEquals(before, shown(0.4f), 1e-10)
            repeat(12) {
                val last = shown(1f)
                clock.advance()
                assertEquals(last, shown(0f), 1e-10)
                kotlin.test.assertTrue(shown(1f) >= last)
            }
            assertEquals(0.0, clock.renderSeconds(1f), 1e-10)
        }
    }

    @Test fun `render epoch moves continuously between ticks and stops at the prediction limit`() {
        val clock = ClientEphemerisClock()
        clock.accept(5000, true)
        clock.advance()
        assertEquals(-0.05, clock.renderSeconds(0f), 1e-12)
        assertEquals(-0.025, clock.renderSeconds(0.5f), 1e-12)
        assertEquals(0.0, clock.renderSeconds(1f), 1e-12)
        clock.accept(5001, true)
        assertEquals(-0.025, clock.renderSeconds(0.5f), 1e-12)
        val last = clock.tick / 20.0 + clock.renderSeconds(1f)
        clock.advance()
        assertEquals(last, clock.tick / 20.0 + clock.renderSeconds(0f), 1e-12)
        repeat(20) { clock.advance() }
        assertEquals(0.0, clock.renderSeconds(0.2f))
        clock.accept(5020, false)
        clock.advance()
        assertEquals(0.0, clock.renderSeconds(0.3f))
    }

    @Test fun `ephemeris uses bounded client ticks rather than reset dimension time`() {
        val clock = ClientEphemerisClock()
        clock.accept(5000, true)
        repeat(6) { clock.advance() }
        assertEquals(5006L, clock.tick)
        repeat(100) { clock.advance() }
        assertEquals(5010L, clock.tick)
        clock.accept(5015, false)
        repeat(100) { clock.advance() }
        assertEquals(5015L, clock.tick)
        clock.accept(5015, true)
        clock.advance()
        assertEquals(5016L, clock.tick)
        clock.accept(Long.MAX_VALUE, true)
        clock.advance()
        assertEquals(Long.MAX_VALUE, clock.tick)
    }

    @Test fun `tick sprint snapshots cannot accumulate an unbounded display delay`() {
        val clock = ClientEphemerisClock()
        clock.accept(5000L, true)
        for (tick in 5010L..29000L step 10) {
            clock.accept(tick, true)
            val lag = -clock.renderSeconds(0.5f) * 20.0
            kotlin.test.assertTrue(lag in -10.0..10.0, "Sprint snapshot accumulated $lag ticks of visual lag")
        }
        repeat(12) { clock.advance() }
        assertEquals(0.0, clock.renderSeconds(1f), 1e-10)
    }
}
