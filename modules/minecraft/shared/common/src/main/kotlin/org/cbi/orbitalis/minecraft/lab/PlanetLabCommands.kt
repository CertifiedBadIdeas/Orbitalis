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

package org.cbi.orbitalis.minecraft.lab

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.cbi.orbitalis.core.planet.CubeFace
import java.util.Locale

/** Operator-only lab controls. No command is run automatically when a world starts. */
object PlanetLabCommands {
    private fun failure(message: String): Nothing = throw SimpleCommandExceptionType(Component.literal(message)).create()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val enter = Commands.literal("enter").executes { enter(it.source, CubeFace.POSITIVE_Z) }
        for (face in CubeFace.entries) {
            enter.then(Commands.literal(face.name.lowercase(Locale.ROOT)).executes { enter(it.source, face) })
        }
        dispatcher.register(
            Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(
                Commands
                    .literal("lab")
                    .then(enter)
                    .then(
                        Commands.literal("leave").executes { context ->
                            if (!PlanetLabServices
                                    .forServer(
                                        context.source.server,
                                    ).leave(context.source.playerOrException)
                            ) {
                                failure("You are not in the planet laboratory.")
                            }
                            context.source.sendSuccess({ Component.literal("Left the planet laboratory.") }, false)
                            1
                        },
                    ).then(
                        Commands.literal("sample").executes { context ->
                            val player = context.source.playerOrException
                            if (player.level().dimension() != PlanetLabCoordinates.DIMENSION) failure("Enter the planet laboratory first.")
                            val sample =
                                PlanetLabCoordinates.locate(player.position(), player.deltaMovement)
                                    ?: failure("Stand within a surface chart to sample it.")
                            val state = PlanetLabCoordinates.planet.toCartesian(sample.point, sample.velocity)
                            context.source.sendSuccess({
                                Component.literal(
                                    String.format(
                                        Locale.ROOT,
                                        "%s u=%.4f v=%.4f altitude=%.2f m; position=(%.3f, %.3f, %.3f) m; speed=%.3f m/s",
                                        sample.point.face,
                                        sample.point.u,
                                        sample.point.v,
                                        sample.point.altitudeMetres,
                                        state.position.x,
                                        state.position.y,
                                        state.position.z,
                                        state.velocity.length(),
                                    ),
                                )
                            }, false)
                            1
                        },
                    ).then(
                        Commands.literal("orbit").executes { context ->
                            val player = context.source.playerOrException
                            if (player.level().dimension() != PlanetLabCoordinates.DIMENSION) failure("Enter the planet laboratory first.")
                            val lab = PlanetLabServices.forServer(context.source.server)
                            if (!lab.observe(player)) failure("The observation platform is obstructed.")
                            lab.startOrbit()
                            context.source.sendSuccess({
                                Component.literal(
                                    "Reference orbit started. Green rings show the planet; the bright trail shows one orbit. Use /orbitalis lab leave to return.",
                                )
                            }, false)
                            1
                        },
                    ).then(
                        Commands.literal("stop").executes { context ->
                            if (context.source.level.dimension() !=
                                PlanetLabCoordinates.DIMENSION
                            ) {
                                failure("Run this command in the planet laboratory.")
                            }
                            PlanetLabServices.forServer(context.source.server).stopOrbit()
                            context.source.sendSuccess({ Component.literal("Reference orbit stopped.") }, false)
                            1
                        },
                    ),
            ),
        )
    }

    private fun enter(
        source: CommandSourceStack,
        face: CubeFace,
    ): Int {
        if (source.server.getLevel(PlanetLabCoordinates.DIMENSION) == null) failure("The planet laboratory dimension is unavailable.")
        if (!PlanetLabServices
                .forServer(
                    source.server,
                ).enter(source.playerOrException, face)
        ) {
            failure("The destination platform is obstructed or the transfer was rejected.")
        }
        source.sendSuccess({
            Component.literal(
                "Entered ${face.name.lowercase(Locale.ROOT)}. Use /orbitalis lab sample, /orbitalis lab orbit, or /orbitalis lab leave.",
            )
        }, false)
        return 1
    }
}
