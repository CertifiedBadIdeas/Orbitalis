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

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.event.CommandEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.cbi.orbitalis.neoforge.integration.SableFlightIntegration
import org.joml.Quaterniond
import java.util.UUID

@Mod(FlightConsumerMod.ID)
@EventBusSubscriber(modid = FlightConsumerMod.ID)
class FlightConsumerMod {
    private class Control(
        val owner: UUID,
        val session: AutopilotSession? = null,
        val mission: FlightMission? = null,
    ) {
        fun status() = mission?.status() ?: checkNotNull(session).status()

        fun close() {
            if (mission != null) mission.close() else checkNotNull(session).close()
        }
    }

    companion object {
        const val ID = "orbitalis_flight_consumer"
        private val controls = mutableMapOf<MinecraftServer, Control>()

        private fun ship(player: ServerPlayer): ServerSubLevel {
            val body =
                Sable.HELPER.getTrackingSubLevel(player) as? ServerSubLevel
                    ?: throw IllegalArgumentException("Stand on the ship with creative flight disabled")
            check(body.massTracker.centerOfMass != null && body.massTracker.mass > 0) { "Wait for native ship mass initialization" }
            return body
        }

        private fun commandResult(action: () -> Int): Int =
            try {
                action()
            } catch (error: IllegalArgumentException) {
                throw SimpleCommandExceptionType(Component.literal(error.message.orEmpty())).create()
            } catch (error: IllegalStateException) {
                throw SimpleCommandExceptionType(Component.literal(error.message.orEmpty())).create()
            }

        private fun engage(
            source: CommandSourceStack,
            altitude: Double?,
        ): Int =
            commandResult {
                val player = source.playerOrException
                require(player.isCreative) { "Test autopilot requires creative mode" }
                val body = ship(player)
                require(!SubLevelPhysicsSystem.require(body.level).paused) { "Wait for transfer/recovery to finish" }
                val existing = controls[source.server]
                require(existing == null || existing.owner == player.uuid) { "Another pilot owns the test autopilot" }
                stop(source.server, null)
                var target = AutopilotSession.position(body)
                if (altitude != null) {
                    val global = FlightSpaceCoordinates.globalPosition(body.level, target)
                    val goal =
                        Vector3(
                            global.x,
                            FlatPlanetSpace.SEA_LEVEL + altitude,
                            global.z,
                        )
                    target = FlightSpaceCoordinates.localPosition(body.level.dimension(), goal)
                }
                val session = AutopilotSession(body, target)
                controls[source.server] = Control(player.uuid, session = session)
                source.sendSuccess(
                    { Component.literal("Propulsion autopilot engaged. Leave manual controls off. " + session.status()) },
                    false,
                )
                1
            }

        private fun mission(
            source: CommandSourceStack,
            land: Boolean,
        ): Int =
            commandResult {
                val player = source.playerOrException
                require(player.isCreative) { "Test autopilot requires creative mode" }
                val current = controls[source.server]
                require(current == null || current.owner == player.uuid) { "Another pilot owns the test autopilot" }
                if (land && current?.mission != null) {
                    current.mission.requestLanding()
                    source.sendSuccess({ Component.literal(current.status()) }, false)
                    return@commandResult 1
                }
                val body = ship(player)
                stop(source.server, null)
                val sites = LaunchSites.get(source.server)
                val site =
                    if (land) {
                        sites.require(player.uuid, body.uniqueId)
                    } else {
                        LaunchSites.Site(
                            body.uniqueId,
                            AutopilotSession.position(body),
                            Quaterniond(body.logicalPose().orientation()),
                            body.level.dimension(),
                        )
                    }
                val manager = SableFlightIntegration.transferService(source.server)
                val mission: FlightMission =
                    if (site.surface ==
                        Level.OVERWORLD
                    ) {
                        FlatOrbitalMission(player, body, site, manager, land)
                    } else {
                        OrbitalMission(player, body, site, manager, land)
                    }
                try {
                    if (!land) {
                        sites.put(player.uuid, site)
                        source.server
                            .overworld()
                            .dataStorage
                            .save()
                    }
                } catch (failure: RuntimeException) {
                    mission.close()
                    throw failure
                }
                controls[source.server] = Control(player.uuid, mission = mission)
                source.sendSuccess({ Component.literal(mission.status()) }, false)
                1
            }

        private fun stop(
            server: MinecraftServer,
            reason: String?,
        ) {
            val control = controls.remove(server) ?: return
            control.close()
            val player = server.playerList.getPlayer(control.owner)
            if (player != null && reason != null) {
                player.sendSystemMessage(
                    Component.literal("Autopilot disengaged: $reason. Native engine fade still applies."),
                )
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun register(event: RegisterCommandsEvent) {
            val command =
                Commands
                    .literal("autopilot")
                    .then(Commands.literal("orbit").executes { mission(it.source, false) })
                    .then(Commands.literal("land").executes { mission(it.source, true) })
                    .then(Commands.literal("hold").executes { engage(it.source, null) })
                    .then(
                        Commands.literal("altitude").then(
                            Commands
                                .argument("height", DoubleArgumentType.doubleArg(32.0, 15000.0))
                                .executes { engage(it.source, DoubleArgumentType.getDouble(it, "height")) },
                        ),
                    ).then(
                        Commands.literal("stop").executes {
                            val active = controls[it.source.server]
                            if (active != null && active.owner != it.source.playerOrException.uuid) {
                                throw SimpleCommandExceptionType(Component.literal("Another pilot owns the test autopilot")).create()
                            }
                            stop(it.source.server, "manual stop")
                            1
                        },
                    ).then(
                        Commands.literal("status").executes {
                            val active = controls[it.source.server]
                            it.source.sendSuccess({ Component.literal(active?.status() ?: "Autopilot is off") }, false)
                            1
                        },
                    )
            event.dispatcher.register(
                Commands
                    .literal("orbitalis")
                    .requires { it.hasPermission(2) }
                    .then(Commands.literal("flight").then(command)),
            )
        }

        @JvmStatic
        @SubscribeEvent
        fun tick(event: ServerTickEvent.Pre) {
            val active = controls[event.server] ?: return
            try {
                val player = event.server.playerList.getPlayer(active.owner)
                val mission = active.mission
                if (mission != null) {
                    val previous = mission.stage
                    val landed = mission.tick(player)
                    if (player != null && mission.stage != previous) player.sendSystemMessage(Component.literal(active.status()))
                    if (landed) stop(event.server, "landed at the saved launch site")
                    return
                }
                val session = checkNotNull(active.session)
                val body = session.body
                if (player == null || !player.isAlive || player.level() !== body.level ||
                    Sable.HELPER.getTrackingSubLevel(player) !== body || body.isRemoved ||
                    SubLevelPhysicsSystem.require(body.level).paused
                ) {
                    stop(event.server, "pilot/ship unavailable or physics paused")
                    return
                }
                session.update()
            } catch (error: RuntimeException) {
                stop(event.server, error.message)
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun command(event: CommandEvent) {
            // Release real engine inputs before transfer snapshots, including nested execute commands.
            var context: CommandContextBuilder<CommandSourceStack>? = event.parseResults.context
            while (context != null) {
                val nodes = context.nodes
                if (nodes.size >= 3 && nodes[0].node.name == "orbitalis" && nodes[1].node.name == "flight" &&
                    nodes[2].node.name in setOf("transfer", "leave")
                ) {
                    stop(event.parseResults.context.source.server, "flight travel requested")
                }
                context = context.child
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun stopping(event: ServerStoppingEvent) = stop(event.server, null)
    }
}
