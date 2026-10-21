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

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.clouds.EarthCloudState
import org.cbi.orbitalis.minecraft.clouds.EarthCloudStatePayload

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object EarthCloudEvents {
    fun snapshot(server: MinecraftServer): EarthCloudState.Snapshot {
        val earth = server.overworld()
        return EarthCloudState.Snapshot(
            EarthCloudState.derivedSeed(earth.seed),
            earth.getRainLevel(1f),
            earth.getThunderLevel(1f),
            earth.gameTime,
        )
    }

    private fun send(player: ServerPlayer) = PacketDistributor.sendToPlayer(player, EarthCloudStatePayload(snapshot(player.server)))

    @JvmStatic @SubscribeEvent
    fun login(event: PlayerEvent.PlayerLoggedInEvent) {
        (event.entity as? ServerPlayer)?.let(::send)
    }

    @JvmStatic @SubscribeEvent
    fun dimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        (event.entity as? ServerPlayer)?.let(::send)
    }

    @JvmStatic @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        if (event.server.tickCount % 20 != 0 ||
            event.server.playerList.players
                .isEmpty()
        ) {
            return
        }
        val payload = EarthCloudStatePayload(snapshot(event.server))
        event.server.playerList.players
            .forEach { PacketDistributor.sendToPlayer(it, payload) }
    }
}
