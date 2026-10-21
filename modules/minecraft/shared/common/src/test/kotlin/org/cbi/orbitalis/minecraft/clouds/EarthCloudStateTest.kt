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

package org.cbi.orbitalis.minecraft.clouds

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EarthCloudStateTest {
    @AfterTest fun reset() = EarthCloudState.clearClient()

    @Test fun weatherInterpolatesWithoutRestartingOnDuplicateOrOlderDimensionPackets() {
        val start = EarthCloudState.Snapshot(42L, 0f, 0f, 100L)
        EarthCloudState.receive(start, 0L)
        EarthCloudState.receive(start.copy(rain = 1f, thunder = 0.8f, weatherTick = 120L), 1_000_000_000L)
        assertEquals(0.5f, EarthCloudState.sampleClient(1_500_000_000L)!!.rain)
        assertEquals(0.4f, EarthCloudState.sampleClient(1_500_000_000L)!!.thunder)
        EarthCloudState.receive(start.copy(weatherTick = 119L), 1_500_000_000L)
        EarthCloudState.receive(start.copy(weatherTick = 120L), 1_500_000_000L)
        assertEquals(0.75f, EarthCloudState.sampleClient(1_750_000_000L)!!.rain)
        assertEquals(1f, EarthCloudState.sampleClient(3_000_000_000L)!!.rain)
    }

    @Test fun worldChangesAndLogoutDoNotLeakOldWeatherOrCloudIdentity() {
        EarthCloudState.receive(EarthCloudState.Snapshot(1L, 1f, 1f, 300L), 0L)
        val other = EarthCloudState.Snapshot(2L, 0f, 0f, 1L)
        EarthCloudState.receive(other, 100L)
        assertEquals(other, EarthCloudState.client)
        assertEquals(other, EarthCloudState.sampleClient(100L))
        EarthCloudState.clearClient()
        assertNull(EarthCloudState.client)
        assertNull(EarthCloudState.sampleClient())
    }

    @Test fun derivedPatternIdentityIsStableWithoutSendingTheRawSeed() {
        assertEquals(EarthCloudState.derivedSeed(12L), EarthCloudState.derivedSeed(12L))
        assertNotEquals(12L, EarthCloudState.derivedSeed(12L))
        assertNotEquals(EarthCloudState.derivedSeed(12L), EarthCloudState.derivedSeed(13L))
    }

    @Test fun windUsesTheSamePhysicalEpochRegardlessOfRecipientAndStaysPreciseAtHugeTicks() {
        val period = 65536.0
        val almost = EarthCloudState.wind(Long.MAX_VALUE - 1, -0.025, period)
        val next = EarthCloudState.wind(Long.MAX_VALUE, -0.025, period)
        assertEquals(0.03, (next.x - almost.x + period) % period, 1e-8)
        assertEquals(0.01, (next.z - almost.z + period) % period, 1e-8)
        assertEquals(EarthCloudState.wind(42L, 0.01, period), EarthCloudState.wind(42L + 6553600L, 0.01, period))
        val before = EarthCloudState.wind(-1L, 0.0, period)
        val origin = EarthCloudState.wind(0L, 0.0, period)
        assertEquals(0.03, (origin.x - before.x + period) % period, 1e-8)
        assertEquals(0.01, (origin.z - before.z + period) % period, 1e-8)
        for (value in listOf(almost.x, almost.z, next.x, next.z)) assertTrue(value in 0.0..<period)
    }

    @Test fun payloadRoundTripsBoundedWeatherAndRejectsUnknownVersionsAndMalformedValues() {
        val expected = EarthCloudStatePayload(EarthCloudState.Snapshot(Long.MIN_VALUE, 0.75f, 0.25f, Long.MAX_VALUE))
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            EarthCloudStatePayload.CODEC.encode(buffer, expected)
            assertTrue(buffer.readableBytes() <= 34)
            assertEquals(expected, EarthCloudStatePayload.CODEC.decode(buffer))
            buffer.clear()
            buffer.writeByte(77)
            assertFailsWith<IllegalArgumentException> { EarthCloudStatePayload.CODEC.decode(buffer) }
            for (rain in listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f)) {
                buffer.clear()
                buffer
                    .writeByte(
                        EarthCloudStatePayload.VERSION,
                    ).writeLong(1L)
                    .writeFloat(rain)
                    .writeFloat(0f)
                    .writeVarLong(20L)
                    .writeDouble(192.0)
                assertFailsWith<IllegalArgumentException> { EarthCloudStatePayload.CODEC.decode(buffer) }
            }
            buffer.clear()
            buffer.writeByte(EarthCloudStatePayload.VERSION)
            assertFailsWith<IndexOutOfBoundsException> { EarthCloudStatePayload.CODEC.decode(buffer) }
        } finally {
            buffer.release()
        }
    }

    @Test fun snapshotAndWindRejectInvalidPhysicalParameters() {
        for (height in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 1025.0)) {
            assertFailsWith<IllegalArgumentException> { EarthCloudState.Snapshot(1L, 0f, 0f, 0L, height) }
        }
        assertFailsWith<IllegalArgumentException> { EarthCloudState.Snapshot(1L, 0f, Float.NaN, 0L) }
        assertFailsWith<IllegalArgumentException> { EarthCloudState.Snapshot(1L, 0f, 0f, -1L) }
        assertFailsWith<IllegalArgumentException> { EarthCloudState.wind(0L, Double.NaN, 65536.0) }
        assertFailsWith<IllegalArgumentException> { EarthCloudState.wind(0L, 0.0, -1.0) }
    }
}
