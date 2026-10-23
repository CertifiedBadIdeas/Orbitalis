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

package org.cbi.orbitalis.minecraft.integration.sable

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeBlockEntityMetadataTest {
    private fun payload() =
        CompoundTag().apply {
            putString("id", "test:tank")
            putInt("x", 12)
            putInt("y", 64)
            putInt("z", 3)
            putInt("fuel", 1000)
        }

    private fun plot(payload: CompoundTag) =
        CompoundTag().apply {
            put(
                "chunks",
                CompoundTag().apply {
                    put(
                        "0",
                        CompoundTag().apply {
                            put("block_entities", ListTag().also { it.add(payload) })
                            putString("sections", "unchanged terrain")
                        },
                    )
                },
            )
        }

    @Test fun lazyEmptyMetadataDoesNotInvalidatePreparedPlotOrMutateEitherSnapshot() {
        val absent = plot(payload())
        val allocated = plot(payload().apply { put("NeoForgeData", CompoundTag()) })
        val before = allocated.copy()
        assertTrue(NativeBlockEntityMetadata.equivalentPlots(absent, allocated))
        assertTrue(NativeBlockEntityMetadata.equivalentPlots(allocated, absent))
        assertEquals(before, allocated)
        assertEquals(payload(), NativeBlockEntityMetadata.forComparison(payload().apply { put("NeoForgeData", CompoundTag()) }))
    }

    @Test fun realMetadataAndFuelChangesRemainStrictlyProtected() {
        val plain = payload()
        val stamped = plain.copy().apply { put("NeoForgeData", CompoundTag().apply { putString("owner", "original") }) }
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(plot(plain), plot(stamped)))
        assertEquals(stamped, NativeBlockEntityMetadata.forComparison(stamped))
        val changed = stamped.copy().apply { getCompound("NeoForgeData").putString("owner", "other") }
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(plot(stamped), plot(changed)))
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(plot(plain), plot(plain.copy().apply { putInt("fuel", 999) })))
    }

    @Test fun malformedMetadataAndNestedCargoAreNotNormalizedAway() {
        val plain = payload()
        val malformed = plain.copy().apply { putString("NeoForgeData", "invalid") }
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(plot(plain), plot(malformed)))
        val cargo = plain.copy().apply { put("cargo", CompoundTag().apply { put("NeoForgeData", CompoundTag()) }) }
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(plot(plain), plot(cargo)))
        val moved = plain.copy().apply { putInt("x", 13) }
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(plot(plain), plot(moved)))
    }

    @Test fun chunkAndTerrainDifferencesRemainVisible() {
        val original = plot(payload())
        val terrain = original.copy().apply { getCompound("chunks").getCompound("0").putString("sections", "changed") }
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(original, terrain))
        assertFalse(NativeBlockEntityMetadata.equivalentPlots(original, CompoundTag()))
    }
}
