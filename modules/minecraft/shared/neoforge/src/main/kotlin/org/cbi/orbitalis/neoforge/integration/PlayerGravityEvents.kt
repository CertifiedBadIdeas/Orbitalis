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

package org.cbi.orbitalis.neoforge.integration

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.flight.PlayerGravity
import org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object PlayerGravityEvents {
    private val sent = java.util.WeakHashMap<MinecraftServer, Double>()

    private fun send(
        player: ServerPlayer,
        gravity: Double,
    ) {
        player.connection.send(ClientboundCustomPayloadPacket(PlayerGravityPayload(gravity)))
    }

    @JvmStatic @SubscribeEvent
    fun login(event: PlayerEvent.PlayerLoggedInEvent) {
        (event.entity as? ServerPlayer)?.let { send(it, PlayerGravity.serverAcceleration(it.serverLevel())) }
    }

    @JvmStatic @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        val gravity = PlayerGravity.serverAcceleration(event.server.overworld())
        if (sent.put(event.server, gravity) != gravity) {
            event.server.playerList.players
                .forEach { send(it, gravity) }
        }
    }

    @JvmStatic @SubscribeEvent
    fun stop(event: ServerStoppedEvent) {
        sent.remove(event.server)
    }
}
