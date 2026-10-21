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

import org.cbi.orbitalis.core.cloud.ProceduralCloudField
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudSectorSelectionTest {
    @Test
    fun `negative sectors cover finite footprint under wind`() {
        val size = ProceduralCloudField.SECTOR_SIZE
        val windX = 700.0
        val windZ = 1200.0
        val sectors = CloudSectorSelection.sectors(500.0, Vector3(0.0, 2000.0, 0.0), windX, windZ, false).toSet()
        for (x in -500..500 step 10) {
            for (z in -500..500 step 10) {
                assertTrue(
                    CloudSectorSelection.Sector(
                        kotlin.math.floor((x - windX) / size).toInt(),
                        kotlin.math.floor((z - windZ) / size).toInt(),
                    ) in sectors,
                )
            }
        }
        assertTrue(sectors.all { it.x < 0 && it.z < 0 })
    }

    @Test
    fun `surface work remains bounded when planet grows`() {
        val eye = Vector3(0.0, 64.0, 0.0)
        val small = CloudSectorSelection.sectors(10000.0, eye, 12.0, 34.0, true)
        val large = CloudSectorSelection.sectors(1000000.0, eye, 12.0, 34.0, true)
        assertEquals(small, large)
        assertTrue(large.size in 1..676)
        assertTrue(CloudSectorSelection.sectors(500.0, Vector3(10000.0, 64.0, 0.0), 0.0, 0.0, true).isEmpty())
    }

    @Test
    fun `field wrap only translates sector indices`() {
        val eye = Vector3(100.0, 300.0, -200.0)
        val first = CloudSectorSelection.sectors(2500.0, eye, 3.0, 2.0, false)
        val wrapped = CloudSectorSelection.sectors(2500.0, eye, 3.0 + ProceduralCloudField.PERIOD, 2.0, false)
        val shift = (ProceduralCloudField.PERIOD / ProceduralCloudField.SECTOR_SIZE).toInt()
        assertEquals(first.map { it.copy(x = it.x - shift) }, wrapped)
    }

    @Test
    fun `distance and projected scale choose bounded detail`() {
        assertEquals(1, CloudSectorSelection.step(10.0, null, false))
        assertEquals(2, CloudSectorSelection.step(200.0, null, false))
        assertEquals(8, CloudSectorSelection.step(10000.0, null, false))
        assertEquals(8, CloudSectorSelection.step(0.0, 0.001, true))
        assertEquals(1, CloudSectorSelection.step(100000.0, 0.75, false))
    }
}
