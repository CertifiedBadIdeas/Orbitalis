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

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertTrue

class FlightControlAxesTest {
    private val up = Vector3(0.0, 1.0, 0.0)
    private val look = Vector3(0.0, 0.0, 1.0)

    @Test fun `creative lift and steering preserve root direction across moving lunar handoff`() {
        val profile = SolarSystemProfile(11.0)
        for (tick in listOf(0L, 8000L, 160000L, 330000L, 650000L)) {
            val r = profile.lunarSurfaceFrame(tick).orientation
            for (view in listOf(look, (look + up).normalized(), up, up * -1.0)) {
                val screenUp =
                    if (view == up) {
                        look * -1.0
                    } else if (view == up * -1.0) {
                        look
                    } else {
                        up
                    }
                val source = FlightControlAxes.fromView(up, view, screenUp)
                val destination = FlightControlAxes.fromView(r.apply(up), r.apply(view), r.apply(screenUp))
                for (input in listOf(up, up * -1.0, look, Vector3(1.0, 0.0, 0.0), Vector3(1.0, 1.0, 1.0))) {
                    assertTrue((r.apply(source.acceleration(input, 0.15)) - destination.acceleration(input, 0.15)).length() < 1e-10)
                }
            }
        }
    }

    @Test fun `native upright input retains vanilla normalization and speed`() {
        val axes = FlightControlAxes.fromView(up, look, up)
        assertTrue((axes.acceleration(look, 0.15) - look * 0.15).length() < 1e-12)
        assertTrue((axes.acceleration(Vector3(1.0, 0.0, 0.0), 0.15) - Vector3(0.15, 0.0, 0.0)).length() < 1e-12)
        assertTrue(kotlin.math.abs(axes.acceleration(Vector3(1.0, 0.0, 1.0), 0.15).length() - 0.15) < 1e-12)
        assertTrue(axes.acceleration(Vector3.ZERO, 0.15) == Vector3.ZERO)
    }

    @Test fun `vertical view retains transported screen orientation when gravity projection is undefined`() {
        for (tick in listOf(0L, 8000L, 160000L, 330000L)) {
            val r = SolarSystemProfile(11.0).lunarSurfaceFrame(tick).orientation
            val previous = r.apply(look)
            val desired = r.apply(up)
            val preserved = LunarApproachView.stableUp(desired, desired, previous)
            assertTrue((preserved - previous).length() < 1e-12)
            assertTrue((LunarApproachView.stableUp(desired, previous, previous) - desired).length() < 1e-12)
        }
    }
}
