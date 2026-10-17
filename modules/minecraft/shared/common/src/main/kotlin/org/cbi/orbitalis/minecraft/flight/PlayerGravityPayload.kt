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

package org.cbi.orbitalis.minecraft.flight

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/** Server-selected mass-model surface strength, used to build the identical client field. */
data class PlayerGravityPayload(
    val acceleration: Double,
) : CustomPacketPayload {
    init {
        require(acceleration.isFinite() && acceleration > 0.0 && acceleration <= 1000.0)
    }

    override fun type(): CustomPacketPayload.Type<PlayerGravityPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayerGravityPayload>(ResourceLocation.fromNamespaceAndPath("orbitalis", "player_gravity"))
        val CODEC: StreamCodec<FriendlyByteBuf, PlayerGravityPayload> =
            object : StreamCodec<FriendlyByteBuf, PlayerGravityPayload> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    payload: PlayerGravityPayload,
                ) {
                    buffer.writeDouble(payload.acceleration)
                }

                override fun decode(buffer: FriendlyByteBuf) = PlayerGravityPayload(buffer.readDouble())
            }
    }
}
