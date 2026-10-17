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
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.Vector3
import java.util.UUID

/** Native packet compatibility plus exact local-player motion after the absolute teleport reset. */
object PlayerFlightMotion {
    fun capture(player: ServerPlayer): UUID {
        val token = UUID.randomUUID()
        player.connection.send(
            ClientboundCustomPayloadPacket(
                FlightMotionPayload(
                    player.level().dimension().location(),
                    Vector3.ZERO,
                    true,
                    token,
                ),
            ),
        )
        return token
    }

    fun restore(
        player: ServerPlayer,
        velocity: Vec3,
        token: UUID = UUID(0L, 0L),
    ) {
        player.deltaMovement = velocity
        player.hasImpulse = true
        player.connection.send(ClientboundSetEntityMotionPacket(player))
        player.connection.send(
            ClientboundCustomPayloadPacket(
                FlightMotionPayload(
                    player.level().dimension().location(),
                    Vector3(velocity.x, velocity.y, velocity.z),
                    false,
                    token,
                ),
            ),
        )
    }
}

/** Velocity is blocks per native tick, with no short-packet clamp or plot transform. */
data class FlightMotionPayload(
    val dimension: ResourceLocation,
    val velocity: Vector3,
    val capture: Boolean = false,
    val token: UUID = UUID(0L, 0L),
) : CustomPacketPayload {
    init {
        require(dimension.toString() in setOf("minecraft:overworld", "orbitalis:space"))
        require(velocity.length() <= 500.0) { "Player transfer velocity exceeds 10 km/s" }
    }

    override fun type(): CustomPacketPayload.Type<FlightMotionPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FlightMotionPayload>(ResourceLocation.fromNamespaceAndPath("orbitalis", "flight_motion"))
        val CODEC: StreamCodec<FriendlyByteBuf, FlightMotionPayload> =
            object : StreamCodec<FriendlyByteBuf, FlightMotionPayload> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    payload: FlightMotionPayload,
                ) {
                    buffer.writeBoolean(payload.capture)
                    buffer.writeUUID(payload.token)
                    buffer.writeResourceLocation(payload.dimension)
                    buffer.writeDouble(payload.velocity.x)
                    buffer.writeDouble(payload.velocity.y)
                    buffer.writeDouble(payload.velocity.z)
                }

                override fun decode(buffer: FriendlyByteBuf): FlightMotionPayload {
                    val capture = buffer.readBoolean()
                    val token = buffer.readUUID()
                    return FlightMotionPayload(
                        buffer.readResourceLocation(),
                        Vector3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                        capture,
                        token,
                    )
                }
            }
    }
}

object FlightMotionReceiver {
    var accept: (FlightMotionPayload) -> Unit = {}
}
