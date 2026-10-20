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

package org.cbi.orbitalis.minecraft.surface

import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceAtlasContractTest {
    @Test fun legacyRecordsKeepReliefButNeverGuessWaterFromBlueColour() {
        val loaded =
            SurfaceAtlasData.load(
                CompoundTag().apply {
                    putInt("Version", 1)
                    putIntArray("Samples", intArrayOf(0, 0, 64, 0x0033ff))
                },
            )
        assertTrue(loaded.supported)
        assertEquals(SurfaceAtlas.Sample(0, 0, 64, 0x0033ff, 0), loaded.atlas.all().single())
        val saved = loaded.save(CompoundTag(), RegistryAccess.EMPTY)
        assertEquals(2, saved.getInt("Version"))
        assertEquals(
            loaded.atlas.all().toList(),
            SurfaceAtlasData
                .load(saved)
                .atlas
                .all()
                .toList(),
        )
    }

    @Test fun materialSurvivesSaveAndInvalidRecordsRemainUnknown() {
        val data = SurfaceAtlasData()
        val sample = SurfaceAtlas.Sample(2, -3, 81, 0x123456, 191)
        data.record(sample)
        assertEquals(
            sample,
            SurfaceAtlasData
                .load(data.save(CompoundTag(), RegistryAccess.EMPTY))
                .atlas
                .all()
                .single(),
        )
        val malformed =
            SurfaceAtlasData.load(
                CompoundTag().apply {
                    putInt("Version", 2)
                    putIntArray("Samples", intArrayOf(0, 0, 80, 0, 256, 2, -3, 81, 0x123456, 191))
                },
            )
        assertEquals(listOf(sample), malformed.atlas.all().toList())
        for (tag in listOf(
            CompoundTag(),
            CompoundTag().apply { putInt("Version", 3) },
            CompoundTag().apply { putInt("Version", 2) },
            CompoundTag().apply {
                putInt("Version", 2)
                putString("Samples", "wrong")
            },
            CompoundTag().apply {
                putInt("Version", 2)
                putIntArray("Samples", intArrayOf(0))
            },
        )) {
            val unsupported = SurfaceAtlasData.load(tag)
            assertFalse(unsupported.supported)
            assertFalse(unsupported.record(sample))
            assertFalse(unsupported.isDirty)
            assertFailsWith<IllegalStateException> { unsupported.save(CompoundTag(), RegistryAccess.EMPTY) }
        }
    }

    @Test fun packetsPreserveFractionAndRejectWrongVersionsAndOversizedBatches() {
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            val payload =
                SurfaceAtlasPayload(
                    UUID.randomUUID(),
                    true,
                    17,
                    listOf(
                        SurfaceAtlas.Sample(0, 0, 64, 0xff, 0),
                        SurfaceAtlas.Sample(1, 0, 64, 0xff, 128),
                        SurfaceAtlas.Sample(2, 0, 64, 0xff, 255),
                    ),
                )
            SurfaceAtlasPayload.CODEC.encode(buffer, payload)
            assertEquals(payload, SurfaceAtlasPayload.CODEC.decode(buffer))
            assertEquals(0, buffer.readableBytes())
            for (version in listOf(1, 3)) {
                buffer.clear().writeVarInt(version)
                assertFailsWith<IllegalArgumentException> { SurfaceAtlasPayload.CODEC.decode(buffer) }
            }
            buffer.clear()
            buffer
                .writeVarInt(SurfaceAtlasPayload.VERSION)
                .writeUUID(payload.session)
                .writeBoolean(false)
                .writeLong(0)
                .writeVarInt(257)
            assertFailsWith<IllegalArgumentException> { SurfaceAtlasPayload.CODEC.decode(buffer) }
        } finally {
            buffer.release()
        }
    }
}
