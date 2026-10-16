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

package org.cbi.orbitalis.consumer.flight

import com.mojang.authlib.GameProfile
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import io.netty.channel.embedded.EmbeddedChannel
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.Connection
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.level.GameType
import java.util.UUID

/** Models server acknowledgments/contact for handoff tests; never claims a real game client. */
internal class InertPilot(
    helper: GameTestHelper,
) : AutoCloseable {
    val player: ServerPlayer
    private val channel: EmbeddedChannel
    private var teleportId: Int? = null

    init {
        val profile = GameProfile(UUID.randomUUID(), "orbitalis-pilot")
        val connection = Connection(PacketFlow.SERVERBOUND)
        channel = EmbeddedChannel(connection)
        player = ServerPlayer(helper.level.server, helper.level, profile, ClientInformation.createDefault())
        player.connection =
            object : ServerGamePacketListenerImpl(
                helper.level.server,
                connection,
                player,
                CommonListenerCookie.createInitial(profile, false),
            ) {
                override fun send(packet: Packet<*>) {
                    if (packet is ClientboundPlayerPositionPacket) teleportId = packet.id
                }

                override fun send(
                    packet: Packet<*>,
                    listener: PacketSendListener?,
                ) = send(packet)
            }
        player.setGameMode(GameType.CREATIVE)
    }

    fun attach(body: ServerSubLevel) {
        (player as EntityMovementExtension).`sable$setTrackingSubLevel`(body)
        val p = AutopilotSession.position(body)
        player.setPos(p.x, p.y + 3, p.z)
    }

    fun acknowledge(body: ServerSubLevel) {
        teleportId?.let { id ->
            teleportId = null
            player.connection.handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket(id))
        }
        attach(body)
    }

    override fun close() {
        player.discard()
        channel.finishAndReleaseAll()
    }
}
