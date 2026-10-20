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

package org.cbi.orbitalis.minecraft.lighting

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/** Separate versioned channel avoids changing existing physical-state packets. */
data class SkyLightingPayload(
    val cosmic: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SkyLightingPayload>(ResourceLocation.fromNamespaceAndPath("orbitalis", "sky_lighting"))
        val CODEC: StreamCodec<FriendlyByteBuf, SkyLightingPayload> =
            object : StreamCodec<FriendlyByteBuf, SkyLightingPayload> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    value: SkyLightingPayload,
                ) {
                    buffer.writeByte(if (value.cosmic) 1 else 0)
                }

                override fun decode(buffer: FriendlyByteBuf): SkyLightingPayload {
                    val mode = buffer.readUnsignedByte().toInt()
                    require(mode in 0..1) { "Unknown sky lighting mode" }
                    return SkyLightingPayload(mode == 1)
                }
            }
    }
}
