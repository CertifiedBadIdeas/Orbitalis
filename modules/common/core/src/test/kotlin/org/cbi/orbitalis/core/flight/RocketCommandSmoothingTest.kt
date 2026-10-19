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

import org.cbi.orbitalis.core.planet.Vector3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketCommandSmoothingTest {
    @Test fun suddenFeedbackChangesHaveBoundedJerkAndConvergeWithoutOvershoot() {
        val filter = RocketCommandSmoothing(10.0)
        var previous = Vector3.ZERO
        for (target in listOf(Vector3(0.0, 15.0, 0.0), Vector3(8.0, 2.0, -5.0), Vector3.ZERO)) {
            repeat(100) {
                val next = filter.next(target)
                assertTrue((next - previous).length() <= 0.50000001)
                assertTrue((target - next).length() <= (target - previous).length() + 1e-10)
                previous = next
            }
            assertTrue((previous - target).length() < 1e-10)
        }
    }
}
