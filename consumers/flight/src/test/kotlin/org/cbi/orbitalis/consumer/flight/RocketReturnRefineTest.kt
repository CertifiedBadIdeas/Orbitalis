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

package org.cbi.orbitalis.consumer.flight

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.RectangularGravity
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketReturnRefineTest {
    @Test fun refinementPreservesActualCoastStateAndFixedBurnEpoch() {
        val field = SurfaceGravityLayer(RectangularGravity(1000.0, 64.0, 1000.0, 11.0), 11.0)
        val initial = CartesianState(Vector3(-1000.0, 1200.0, 0.0), Vector3(60.0, -10.0, 0.0))
        val delay = 2.35
        val gravity = Vector3(0.0, -0.25, 0.0)
        val path =
            RocketReturn.refine(
                field,
                initial,
                Vector3(0.0, 90.0, 0.0),
                60.0,
                Vector3(1.0, 0.0, 0.0),
                delay,
                { _, seconds -> gravity + Vector3(0.0, 0.001 * seconds, 0.0) },
                0.0,
                200.0,
            )
        val expectedPosition =
            initial.position + initial.velocity * delay + gravity * (delay * delay / 2.0) +
                Vector3(0.0, 0.001 * delay * delay * delay / 6.0, 0.0)
        val expectedVelocity = initial.velocity + gravity * delay + Vector3(0.0, 0.001 * delay * delay / 2.0, 0.0)
        val start = path.at(0.0).state
        assertTrue((start.position - expectedPosition).length() < 1e-8)
        assertTrue((start.velocity - expectedVelocity).length() < 1e-8)
        var elapsed = 0.0
        for (segment in path.segments.dropLast(1)) {
            elapsed += segment.duration
            val before = path.at(elapsed - 1e-6)
            val after = path.at(elapsed + 1e-6)
            assertTrue((before.state.position - after.state.position).length() < 1e-3)
            assertTrue((before.state.velocity - after.state.velocity).length() < 1e-3)
            assertTrue((before.acceleration - after.acceleration).length() < 1e-3)
        }
    }
}
