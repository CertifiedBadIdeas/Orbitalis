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

import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.lighting.SkyLightingData
import org.cbi.orbitalis.minecraft.lighting.SkyLightingPayload

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object SkyLightingEvents {
    @JvmStatic @SubscribeEvent
    fun login(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        PacketDistributor.sendToPlayer(player, SkyLightingPayload(SkyLightingData.get(player.server).cosmic))
    }

    @JvmStatic @SubscribeEvent
    fun commands(event: RegisterCommandsEvent) {
        org.cbi.orbitalis.minecraft.lighting.ForwardTimeCommand
            .register(event.dispatcher)
        val sky =
            Commands.literal("sky").executes {
                val mode = if (SkyLightingData.get(it.source.server).cosmic) "cosmic" else "vanilla"
                it.source.sendSuccess({ Component.literal("Overworld sky: $mode. Space always uses the physical Sun.") }, false)
                1
            }
        for (mode in listOf("vanilla", "cosmic")) {
            sky.then(
                Commands.literal(mode).executes {
                    val data = SkyLightingData.get(it.source.server)
                    if (!data.writable) {
                        it.source.sendFailure(Component.literal("Unsupported sky lighting data; preserved without changes."))
                        0
                    } else {
                        data.select(mode == "cosmic")
                        it.source.server.playerList.players.forEach { player ->
                            PacketDistributor.sendToPlayer(player, SkyLightingPayload(data.cosmic))
                        }
                        it.source.sendSuccess({
                            Component.literal(
                                "Overworld sky: $mode. Cosmic daylight follows physical rotation independently of sleep and /time.",
                            )
                        }, true)
                        1
                    }
                },
            )
        }
        event.dispatcher.register(Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(sky))
    }
}
