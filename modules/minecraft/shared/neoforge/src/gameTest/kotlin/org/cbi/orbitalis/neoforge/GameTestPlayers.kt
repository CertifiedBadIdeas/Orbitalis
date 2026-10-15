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

package org.cbi.orbitalis.neoforge

import com.mojang.authlib.GameProfile
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
import java.util.IdentityHashMap
import java.util.UUID

/** Server behavior fixture; deliberately does not claim a client login/network handshake. */
object GameTestPlayers {
    private val teleportAcks = IdentityHashMap<ServerPlayer, () -> Unit>()

    fun acknowledgeTeleport(player: ServerPlayer) {
        checkNotNull(teleportAcks[player]) { "Not an inert GameTest pilot" }.invoke()
    }

    fun create(helper: GameTestHelper): ServerPlayer {
        val profile = GameProfile(UUID.randomUUID(), "orbitalis-test")
        val connection = Connection(PacketFlow.SERVERBOUND)
        // NeoForge dimension changes inspect channel attributes even with an inert packet sink.
        val channel = EmbeddedChannel(connection)
        var teleportId: Int? = null
        val player =
            object : ServerPlayer(helper.level.server, helper.level, profile, ClientInformation.createDefault()) {
                override fun remove(reason: RemovalReason) {
                    super.remove(reason)
                    if (reason.shouldDestroy()) {
                        teleportAcks.remove(this)
                        channel.finishAndReleaseAll()
                    }
                }
            }
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
                ) {
                    send(packet)
                }
            }
        teleportAcks[player] = {
            val id = checkNotNull(teleportId) { "No teleport packet was sent" }
            teleportId = null
            player.connection.handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket(id))
        }
        // No login is simulated: registering this player would broadcast unnegotiated mod payloads.
        player.setGameMode(GameType.CREATIVE)
        return player
    }
}
