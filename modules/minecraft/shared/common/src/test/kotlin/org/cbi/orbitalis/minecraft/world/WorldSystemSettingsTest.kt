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

package org.cbi.orbitalis.minecraft.world

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorldSystemSettingsTest {
    @Test fun creationSizesRoundTripIncludingSmallPreviewAndDefaults() {
        for (width in WorldSystemSettings.PRESETS + listOf(1001, 19999)) {
            val settings = WorldSystemSettings(width)
            assertEquals(settings, WorldSystemSettings.decode(settings.encode()))
        }
        assertEquals(10000, WorldSystemSettings().earthWidth)
    }

    @Test fun absenceIsLegacyRatherThanANewDefaultSelection() {
        assertNull(WorldSystemSettings.decode(null))
        assertEquals(WorldSystemSettings(10000), WorldSystemSettings.decode(WorldSystemSettings().encode()))
    }

    @Test fun invalidOrUnknownSettingsCannotSilentlyResizeAWorld() {
        for (width in listOf(Int.MIN_VALUE, 0, 999, 20001, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { WorldSystemSettings(width) }
        }
        val future =
            WorldSystemSettings(1000).encode().apply {
                putInt("Version", 2)
                putString("Future", "retain")
            }
        val before = future.copy()
        assertFailsWith<IllegalArgumentException> { WorldSystemSettings.decode(future) }
        assertEquals(before, future)
        assertFailsWith<IllegalArgumentException> { WorldSystemSettings.decode(CompoundTag()) }
        assertFailsWith<IllegalArgumentException> { WorldSystemSettings.decode(StringTag.valueOf("opaque")) }
        assertFailsWith<IllegalArgumentException> {
            WorldSystemSettings.decode(WorldSystemSettings().encode().apply { putString("EarthWidth", "1000") })
        }
    }
}
