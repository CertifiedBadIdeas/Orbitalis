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

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/** Dedicated version-one payload leaves all existing physics and sky-lighting packet layouts intact. */
data class EarthCloudStatePayload(
    val snapshot: EarthCloudState.Snapshot,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val VERSION = 1
        val TYPE = CustomPacketPayload.Type<EarthCloudStatePayload>(ResourceLocation.fromNamespaceAndPath("orbitalis", "earth_cloud_state"))
        val CODEC: StreamCodec<FriendlyByteBuf, EarthCloudStatePayload> =
            object : StreamCodec<FriendlyByteBuf, EarthCloudStatePayload> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    value: EarthCloudStatePayload,
                ) {
                    buffer.writeByte(VERSION)
                    buffer.writeLong(value.snapshot.seed)
                    buffer.writeFloat(value.snapshot.rain)
                    buffer.writeFloat(value.snapshot.thunder)
                    buffer.writeVarLong(value.snapshot.weatherTick)
                    buffer.writeDouble(value.snapshot.baseHeight)
                }

                override fun decode(buffer: FriendlyByteBuf): EarthCloudStatePayload {
                    require(buffer.readUnsignedByte().toInt() == VERSION) { "Unsupported Earth cloud state version" }
                    return EarthCloudStatePayload(
                        EarthCloudState.Snapshot(
                            buffer.readLong(),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            buffer.readVarLong(),
                            buffer.readDouble(),
                        ),
                    )
                }
            }
    }
}
