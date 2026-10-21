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

package org.cbi.orbitalis.core.flight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrbitalInsertionRampTest {
    @Test
    fun highSpeedInsertionRespectsPhysicalAccelerationBudget() {
        for (speed in listOf(0.0, 20.0, 200.0, 450.0)) {
            val ramp = OrbitalInsertionRamp(speed)
            for (index in 0..1000) {
                val sample = ramp.sample(ramp.duration * index / 1000.0)
                assertTrue(sample.derivative * speed <= ramp.accelerationBudget + 1e-10)
                assertTrue(sample.rate in 0.0..1.0)
            }
            assertTrue(ramp.duration >= 40.0)
            assertEquals(0.0, ramp.sample(0.0).elapsed)
            assertEquals(ramp.duration * 0.5, ramp.sample(ramp.duration).elapsed, 1e-9)
            assertEquals(ramp.duration * 0.5 + 10.0, ramp.sample(ramp.duration + 10.0).elapsed, 1e-9)
            assertEquals(1.0, ramp.sample(ramp.duration).rate)
            assertEquals(0.0, ramp.sample(ramp.duration).derivative)
        }
    }

    @Test
    fun integratedTrajectoryTimeHasTheRequestedRateAndAcceleration() {
        val ramp = OrbitalInsertionRamp(200.0)
        val step = 1e-3
        for (fraction in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
            val time = ramp.duration * fraction
            val before = ramp.sample(time - step)
            val current = ramp.sample(time)
            val after = ramp.sample(time + step)
            assertEquals(current.rate, (after.elapsed - before.elapsed) / (2 * step), 1e-7)
            assertEquals(current.derivative, (after.rate - before.rate) / (2 * step), 1e-7)
        }
    }
}
