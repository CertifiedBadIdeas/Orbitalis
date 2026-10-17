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

package org.cbi.orbitalis.neoforge.navigation

import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.integration.sable.ShipTrajectoryService
import org.cbi.orbitalis.minecraft.navigation.NavigationBookmarks
import org.cbi.orbitalis.minecraft.navigation.TrajectoryPayload
import java.util.Locale

/** Separate event owner, leaving optional navball, consumer guidance and engine APIs independent. */
@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object TrajectoryEvents {
    private val services = mutableMapOf<MinecraftServer, ShipTrajectoryService>()

    private fun service(server: MinecraftServer): ShipTrajectoryService =
        services.getOrPut(server) {
            ShipTrajectoryService(server) { player, result ->
                PacketDistributor.sendToPlayer(
                    player,
                    TrajectoryPayload(
                        result,
                        if (result ==
                            null
                        ) {
                            null
                        } else {
                            NavigationBookmarks.get(server).get(player.uuid)
                        },
                    ),
                )
            }
        }

    @JvmStatic
    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        services[event.server]?.tick()
    }

    @JvmStatic
    @SubscribeEvent
    fun stop(event: ServerStoppedEvent) {
        services.remove(event.server)?.close()
    }

    @JvmStatic
    @SubscribeEvent
    fun commands(event: RegisterCommandsEvent) {
        if (!ModList.get().isLoaded("sable")) return
        val trajectory = Commands.literal("trajectory")
        trajectory.then(
            Commands.literal("on").executes { context ->
                val player = context.source.playerOrException
                try {
                    service(player.server).enable(player)
                    context.source.sendSuccess({
                        Component.literal(
                            "Coast prediction on: cyan path, yellow surface-entry cross, red body warning. All thrust omitted.",
                        )
                    }, false)
                    1
                } catch (error: IllegalArgumentException) {
                    context.source.sendFailure(Component.literal(error.message ?: "Cannot enable trajectory"))
                    0
                }
            },
        )
        trajectory.then(
            Commands.literal("off").executes { context ->
                val player = context.source.playerOrException
                service(player.server).disable(player)
                context.source.sendSuccess({ Component.literal("Coast prediction off.") }, false)
                1
            },
        )
        trajectory.then(
            Commands.literal("status").executes { context ->
                val player = context.source.playerOrException
                val snapshot = services[player.server]?.latest(player)
                if (snapshot == null) {
                    context.source.sendFailure(
                        Component.literal("No current prediction. Enable trajectory and remain aboard a loaded ship."),
                    )
                    0
                } else {
                    val point = snapshot.prediction.terminal
                    val p = point.state.position
                    context.source.sendSuccess({
                        Component.literal(
                            String.format(
                                Locale.ROOT,
                                "Coast: %s after %.1f s; physical position=(%.1f, %.1f, %.1f) m. No thrust, drag or terrain prediction.",
                                snapshot.prediction.end,
                                point.seconds,
                                p.x,
                                p.y,
                                p.z,
                            ),
                        )
                    }, false)
                    1
                }
            },
        )
        val navigation = Commands.literal("navigation").then(trajectory)
        navigation.then(
            Commands.literal("mark").executes { context ->
                val player = context.source.playerOrException
                try {
                    require(player.level().dimension() == Level.OVERWORLD) { "Mark the launch site in Overworld" }
                    NavigationBookmarks.get(player.server).put(player.uuid, Vector3(player.x, player.y, player.z))
                    context.source.sendSuccess({
                        Component.literal("Personal surface marker saved. It will appear as a green cross with the coast forecast.")
                    }, false)
                    1
                } catch (error: IllegalArgumentException) {
                    context.source.sendFailure(Component.literal(error.message ?: "Cannot mark this location"))
                    0
                }
            },
        )
        navigation.then(
            Commands.literal("unmark").executes { context ->
                val player = context.source.playerOrException
                NavigationBookmarks.get(player.server).clear(player.uuid)
                context.source.sendSuccess({ Component.literal("Personal surface marker removed.") }, false)
                1
            },
        )
        // Keep the existing operator command root policy.
        event.dispatcher.register(Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(navigation))
    }
}
