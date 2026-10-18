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
import org.cbi.orbitalis.core.orbit.SolarSystemProfile

/** Server-selected mass-model surface strength, used to build the identical client field. */
data class PlayerGravityPayload(
    val acceleration: Double,
    val simulationTick: Long = 0,
    val profile: SolarSystemProfile? = null,
    val lunarSeed: Long = 0,
    val advancing: Boolean = true,
) : CustomPacketPayload {
    init {
        require(profile?.earthSpinEpochTick?.let { it <= simulationTick } != false)
        require(simulationTick >= 0 && (profile == null || profile.epochTick <= simulationTick))
        require(profile == null || profile.surfaceGravity == acceleration)
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
                    buffer.writeLong(payload.simulationTick)
                    buffer.writeLong(payload.lunarSeed)
                    buffer.writeBoolean(payload.advancing)
                    buffer.writeBoolean(payload.profile != null)
                    payload.profile?.let {
                        buffer.writeVarInt(SolarSystemProfile.VERSION)
                        buffer.writeVarInt(it.preset.version)
                        buffer.writeLong(it.epochTick)
                        buffer.writeDouble(it.earthPhase)
                        buffer.writeDouble(it.moonPhase)
                        buffer.writeBoolean(it.earthSpinEpochTick != null)
                        it.earthSpinEpochTick?.let(buffer::writeLong)
                    }
                }

                override fun decode(buffer: FriendlyByteBuf): PlayerGravityPayload {
                    val acceleration = buffer.readDouble()
                    val tick = buffer.readLong()
                    val lunarSeed = buffer.readLong()
                    val advancing = buffer.readBoolean()
                    val profile =
                        if (buffer.readBoolean()) {
                            val version = buffer.readVarInt()
                            require(version in 1..SolarSystemProfile.VERSION)
                            val preset = SolarSystemProfile.Preset.fromVersion(if (version >= 3) buffer.readVarInt() else version)
                            SolarSystemProfile(
                                acceleration,
                                buffer.readLong(),
                                buffer.readDouble(),
                                buffer.readDouble(),
                                preset,
                                if (version >= 3 && buffer.readBoolean()) buffer.readLong() else null,
                            )
                        } else {
                            null
                        }
                    return PlayerGravityPayload(acceleration, tick, profile, lunarSeed, advancing)
                }
            }
    }
}
