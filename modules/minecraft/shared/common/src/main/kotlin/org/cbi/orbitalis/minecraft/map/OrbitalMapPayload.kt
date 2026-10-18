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

package org.cbi.orbitalis.minecraft.map

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import java.util.UUID

data class MapObject(
    val id: UUID,
    val name: String,
    val position: Vector3,
    val velocity: Vector3,
    val loaded: Boolean,
    val epoch: Long,
) {
    init {
        require(name.length <= 32 && name.none { it.isISOControl() })
        require(position.length() <= 1e8 && velocity.length() <= 10000.0 && epoch >= 0)
    }
}

/** Version-one screen subscription. Identity and world access come from the native connection. */
data class MapRequest(
    val token: UUID,
    val open: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MapRequest>(ResourceLocation.fromNamespaceAndPath("orbitalis", "map_request"))
        val CODEC: StreamCodec<FriendlyByteBuf, MapRequest> =
            object : StreamCodec<FriendlyByteBuf, MapRequest> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    value: MapRequest,
                ) {
                    buffer.writeUUID(value.token)
                    buffer.writeBoolean(value.open)
                }

                override fun decode(buffer: FriendlyByteBuf) = MapRequest(buffer.readUUID(), buffer.readBoolean())
            }
    }
}

/** Physical surface-frame coordinates; no native block payloads or storage pointers cross this boundary. */
data class MapSnapshot(
    val token: UUID,
    val tick: Long,
    val player: Vector3,
    val objects: List<MapObject>,
    val total: Int,
    val gravity: Double = 11.0,
    val simulationTick: Long = 0,
    val celestialProfile: SolarSystemProfile? = null,
) : CustomPacketPayload {
    init {
        require(gravity.isFinite() && gravity in 1e-6..1e6)
        require(tick >= 0 && simulationTick >= 0 && player.length() <= 1e8)
        celestialProfile?.let {
            require(it.epochTick <= simulationTick && it.surfaceGravity == gravity)
            require(it.earthSpinEpochTick?.let { epoch -> epoch <= simulationTick } != false)
        }
        require(objects.size <= MAX_OBJECTS && total in objects.size..1000000)
        require(objects.map { it.id }.toSet().size == objects.size)
    }

    override fun type() = TYPE

    companion object {
        const val MAX_OBJECTS = 4096
        val TYPE = CustomPacketPayload.Type<MapSnapshot>(ResourceLocation.fromNamespaceAndPath("orbitalis", "map_snapshot"))
        val CODEC: StreamCodec<FriendlyByteBuf, MapSnapshot> =
            object : StreamCodec<FriendlyByteBuf, MapSnapshot> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    value: MapSnapshot,
                ) {
                    buffer.writeUUID(value.token)
                    buffer.writeLong(value.tick)
                    vector(buffer, value.player)
                    buffer.writeDouble(value.gravity)
                    buffer.writeVarInt(value.total)
                    buffer.writeVarInt(value.objects.size)
                    for (item in value.objects) {
                        buffer.writeUUID(item.id)
                        buffer.writeUtf(item.name, 32)
                        vector(buffer, item.position)
                        vector(buffer, item.velocity)
                        buffer.writeBoolean(item.loaded)
                        buffer.writeLong(item.epoch)
                    }
                    buffer.writeLong(value.simulationTick)
                    val profile = value.celestialProfile
                    buffer.writeBoolean(profile != null)
                    if (profile != null) {
                        buffer.writeVarInt(SolarSystemProfile.VERSION)
                        buffer.writeDouble(profile.surfaceGravity)
                        buffer.writeLong(profile.epochTick)
                        buffer.writeDouble(profile.earthPhase)
                        buffer.writeDouble(profile.moonPhase)
                        buffer.writeBoolean(profile.earthSpinEpochTick != null)
                        profile.earthSpinEpochTick?.let(buffer::writeLong)
                    }
                }

                override fun decode(buffer: FriendlyByteBuf): MapSnapshot {
                    val token = buffer.readUUID()
                    val tick = buffer.readLong()
                    val player = vector(buffer)
                    val gravity = buffer.readDouble()
                    require(gravity.isFinite() && gravity in 1e-6..1e6)
                    val total = buffer.readVarInt()
                    val count = buffer.readVarInt()
                    require(count in 0..MAX_OBJECTS && total in count..1000000)
                    val objects =
                        List(count) {
                            MapObject(
                                buffer.readUUID(),
                                buffer.readUtf(32),
                                vector(buffer),
                                vector(buffer),
                                buffer.readBoolean(),
                                buffer.readLong(),
                            )
                        }
                    val simulationTick = buffer.readLong()
                    val profile =
                        if (buffer.readBoolean()) {
                            require(buffer.readVarInt() == SolarSystemProfile.VERSION) { "Unknown celestial profile version" }
                            SolarSystemProfile(
                                buffer.readDouble(),
                                buffer.readLong(),
                                buffer.readDouble(),
                                buffer.readDouble(),
                                if (buffer.readBoolean()) buffer.readLong() else null,
                            ).also { require(it.earthSpinEpochTick?.let { epoch -> epoch <= simulationTick } != false) }
                        } else {
                            null
                        }
                    return MapSnapshot(token, tick, player, objects, total, gravity, simulationTick, profile)
                }
            }

        private fun vector(
            buffer: FriendlyByteBuf,
            v: Vector3,
        ) {
            buffer.writeDouble(v.x)
            buffer.writeDouble(v.y)
            buffer.writeDouble(v.z)
        }

        private fun vector(buffer: FriendlyByteBuf) = Vector3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
    }
}

object MapSnapshotReceiver {
    var accept: (MapSnapshot) -> Unit = {}
}
