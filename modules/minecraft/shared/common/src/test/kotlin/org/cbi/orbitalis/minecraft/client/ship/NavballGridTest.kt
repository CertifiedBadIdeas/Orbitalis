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

package org.cbi.orbitalis.minecraft.client.ship

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavballGridTest {
    @Test fun labelsAndRasterAgreeForUprightInvertedAndSidewaysGravity() {
        for (up in listOf(Vector3(0.0, 1.0, 0.0), Vector3(0.0, -1.0, 0.0), Vector3(0.0, 0.0, 1.0), Vector3(1.0, 0.0, 0.0))) {
            val frame = NavballGrid.worldFrame(up)
            assertTrue(kotlin.math.abs(frame.north.dot(up)) < 1e-12)
            assertTrue(kotlin.math.abs(frame.east.dot(up)) < 1e-12)
            for (pitch in listOf(-60.0, -30.0, 30.0, 60.0)) {
                val label = NavballGrid.point(frame, pitch, 23.0)
                assertEquals(pitch, NavballGrid.pitch(label, frame), 1e-10)
                assertEquals(NavballGrid.Line.MAJOR, NavballGrid.line(label, frame))
            }
            assertEquals(NavballGrid.Line.HORIZON, NavballGrid.line(frame.north, frame))
            assertEquals(NavballGrid.Line.MINOR, NavballGrid.line(NavballGrid.point(frame, 15.0, 23.0), frame))
            assertEquals(NavballGrid.Line.NONE, NavballGrid.line(NavballGrid.point(frame, 8.0, 23.0), frame))
        }
    }
}
