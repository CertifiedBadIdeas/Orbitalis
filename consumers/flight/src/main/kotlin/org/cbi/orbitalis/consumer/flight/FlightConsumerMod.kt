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
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.creative_vector_thruster.CreativeVectorThrusterBlockEntity
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.UuidArgument
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
import org.cbi.orbitalis.minecraft.integration.sable.SableShipContact
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
        private val controls = mutableMapOf<MinecraftServer, MutableMap<UUID, Control>>()

        private fun ship(player: ServerPlayer): ServerSubLevel {
            val body =
                SableShipContact.resolve(player) as? ServerSubLevel
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
                val existing = controls[source.server]?.get(body.uniqueId)
                require(existing == null || existing.owner == player.uuid) { "Another pilot owns the test autopilot" }
                stop(source.server, null, body.uniqueId)
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
                controls.getOrPut(source.server) { linkedMapOf() }[body.uniqueId] = Control(player.uuid, session = session)
                source.sendSuccess(
                    { Component.literal("Propulsion autopilot engaged. Leave manual controls off. " + session.status()) },
                    false,
                )
                1
            }

        private fun mission(
            source: CommandSourceStack,
            land: Boolean,
            id: UUID? = null,
        ): Int =
            commandResult {
                val player = source.playerOrException
                require(player.isCreative) { "Test autopilot requires creative mode" }
                val selected = id ?: if (land) selectedId(source) else ship(player).uniqueId
                val current = controls[source.server]?.get(selected)
                require(current == null || current.owner == player.uuid) { "Another pilot owns the test autopilot" }
                if (land && current?.mission != null) {
                    current.mission.requestLanding()
                    source.sendSuccess({ Component.literal(current.status()) }, false)
                    return@commandResult 1
                }
                val body = FlightShips.loaded(source.server, selected)
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
                stop(source.server, null, body.uniqueId)
                val manager = SableFlightIntegration.transferService(source.server)
                val rocket =
                    body.plot.loadedChunks.any { chunk ->
                        chunk.chunk.blockEntities.values.any {
                            it is CreativeVectorThrusterBlockEntity
                        }
                    }
                val mission: FlightMission =
                    if (rocket) {
                        RocketMission(
                            player,
                            body,
                            site,
                            manager,
                            land,
                        )
                    } else {
                        FlatOrbitalMission(player, body, site, manager, land)
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
                controls.getOrPut(source.server) { linkedMapOf() }[body.uniqueId] = Control(player.uuid, mission = mission)
                source.sendSuccess({ Component.literal(mission.status()) }, false)
                1
            }

        private fun selectedId(source: CommandSourceStack): UUID {
            val player = source.playerOrException
            (SableShipContact.resolve(player) as? ServerSubLevel)?.let { return it.uniqueId }
            return controls[source.server]?.filterValues { it.owner == player.uuid }?.keys?.singleOrNull()
                ?: throw IllegalArgumentException("Specify a ship UUID; use /orbitalis flight autopilot list")
        }

        private fun stop(
            server: MinecraftServer,
            reason: String?,
            id: UUID? = null,
        ) {
            val ids = id?.let { listOf(it) } ?: controls[server]?.keys?.toList().orEmpty()
            for (ship in ids) {
                val control = controls[server]?.remove(ship) ?: continue
                control.close()
                if (reason != null) {
                    server.playerList.getPlayer(control.owner)?.sendSystemMessage(
                        Component.literal("Autopilot $ship disengaged: $reason. Native engine fade still applies."),
                    )
                }
            }
            if (controls[server]?.isEmpty() == true) controls.remove(server)
        }

        private fun stopCommand(
            source: CommandSourceStack,
            id: UUID?,
        ): Int =
            commandResult {
                val selected = id ?: selectedId(source)
                val active = controls[source.server]?.get(selected)
                require(active == null || active.owner == source.playerOrException.uuid) { "Another pilot owns this autopilot" }
                stop(source.server, "manual stop", selected)
                1
            }

        private fun status(
            source: CommandSourceStack,
            id: UUID?,
        ): Int =
            commandResult {
                val selected = id ?: selectedId(source)
                val entry = FlightShips.entries(source.server).firstOrNull { it.id == selected }
                require(entry != null) { "Unknown ship $selected" }
                source.sendSuccess({
                    Component.literal(entry.describe() + " | " + (controls[source.server]?.get(selected)?.status() ?: "autopilot off"))
                }, false)
                1
            }

        private fun list(
            source: CommandSourceStack,
            page: Int,
        ): Int =
            commandResult {
                val entries = FlightShips.entries(source.server)
                val pages = maxOf(1, (entries.size + 19) / 20)
                require(page <= pages) { "There are $pages pages" }
                source.sendSuccess(
                    { Component.literal("Ships: ${entries.size}; page $page/$pages; coordinates in the named dimension") },
                    false,
                )
                for (entry in entries.drop((page - 1) * 20).take(20)) {
                    val stage =
                        controls[source.server]?.get(entry.id)?.mission?.stage
                            ?: if (controls[source.server]?.containsKey(entry.id) == true) "HOLD" else "off"
                    source.sendSuccess({ Component.literal(entry.describe() + " | autopilot " + stage) }, false)
                }
                entries.size
            }

        private fun shipArgument(action: (CommandSourceStack, UUID) -> Int) =
            Commands
                .argument("ship", UuidArgument.uuid())
                .suggests { context, builder ->
                    SharedSuggestionProvider.suggest(FlightShips.entries(context.source.server).map { it.id.toString() }, builder)
                }.executes { action(it.source, UuidArgument.getUuid(it, "ship")) }

        @JvmStatic
        @SubscribeEvent
        fun register(event: RegisterCommandsEvent) {
            val command =
                Commands
                    .literal("autopilot")
                    .then(
                        Commands.literal("orbit").executes { mission(it.source, false) }.then(
                            shipArgument {
                                source,
                                id,
                                ->
                                mission(source, false, id)
                            },
                        ),
                    ).then(
                        Commands.literal("land").executes { mission(it.source, true) }.then(
                            shipArgument {
                                source,
                                id,
                                ->
                                mission(source, true, id)
                            },
                        ),
                    ).then(
                        Commands
                            .literal("list")
                            .executes {
                                list(it.source, 1)
                            }.then(
                                Commands
                                    .argument(
                                        "page",
                                        IntegerArgumentType.integer(1),
                                    ).executes { list(it.source, IntegerArgumentType.getInteger(it, "page")) },
                            ),
                    ).then(Commands.literal("hold").executes { engage(it.source, null) })
                    .then(
                        Commands.literal("altitude").then(
                            Commands
                                .argument("height", DoubleArgumentType.doubleArg(32.0, 15000.0))
                                .executes { engage(it.source, DoubleArgumentType.getDouble(it, "height")) },
                        ),
                    ).then(
                        Commands.literal("stop").executes { stopCommand(it.source, null) }.then(shipArgument(::stopCommand)),
                    ).then(
                        Commands.literal("status").executes { status(it.source, null) }.then(shipArgument(::status)),
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
            for ((id, active) in controls[event.server]?.toMap().orEmpty()) {
                try {
                    val player = event.server.playerList.getPlayer(active.owner)
                    val mission = active.mission
                    if (mission != null) {
                        val previous = mission.stage
                        val landed = mission.tick(player)
                        if (player != null && mission.stage != previous) player.sendSystemMessage(Component.literal(active.status()))
                        if (landed) stop(event.server, "landed at the saved launch site", id)
                    } else {
                        val session = checkNotNull(active.session)
                        if (session.body.isRemoved) {
                            stop(event.server, "ship unloaded or removed", id)
                        } else if (!SubLevelPhysicsSystem.require(session.body.level).paused) {
                            session.update()
                        }
                    }
                } catch (error: RuntimeException) {
                    stop(event.server, error.message, id)
                }
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
                    nodes[2].node.name == "transfer"
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
