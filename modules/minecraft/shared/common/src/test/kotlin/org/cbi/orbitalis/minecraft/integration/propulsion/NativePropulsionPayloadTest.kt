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

package org.cbi.orbitalis.minecraft.integration.propulsion

import net.minecraft.nbt.CompoundTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class NativePropulsionPayloadTest {
    private fun motor() =
        CompoundTag().apply {
            putString("id", "create:motor")
            putFloat("Speed", 256f)
            putInt("ScrollValue", 256)
            put("Network", CompoundTag().apply { putLong("Id", 42) })
        }

    @Test fun rebuiltMotorMayRequestNativeKineticAttachmentWithoutChangingSavedPayloads() {
        val original = motor()
        val rebuilt = original.copy().apply { putBoolean("NeedsSpeedUpdate", true) }
        val before = rebuilt.copy()
        assertEquals(NativePropulsionPayload.forComparison(original), NativePropulsionPayload.forComparison(rebuilt))
        assertEquals(before, rebuilt)
        for (changed in listOf(
            original.copy().apply { putInt("ScrollValue", -256) },
            original.copy().apply { putFloat("Speed", 0f) },
            original.copy().apply { getCompound("Network").putLong("Id", 43) },
        )) {
            assertNotEquals(NativePropulsionPayload.forComparison(original), NativePropulsionPayload.forComparison(changed))
        }
    }

    @Test fun malformedOrUnrelatedWorkFlagsAreNotSilentlyDiscarded() {
        for (malformed in listOf(
            motor().apply { putString("NeedsSpeedUpdate", "true") },
            motor().apply { putBoolean("NeedsSpeedUpdate", false) },
        )) {
            assertFailsWith<IllegalStateException> { NativePropulsionPayload.forComparison(malformed) }
        }
        val unrelated =
            motor().apply {
                putString("id", "other:motor")
                putBoolean("NeedsSpeedUpdate", true)
            }
        assertEquals(unrelated, NativePropulsionPayload.forComparison(unrelated))
    }
}
