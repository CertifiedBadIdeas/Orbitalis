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

/** Operator-only lab controls. No command is run automatically when a world starts. */
object PlanetLabCommands {
    private fun failure(message: String): Nothing = throw SimpleCommandExceptionType(Component.literal(message)).create()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(
                Commands
                    .literal("lab")
                    .then(
                        Commands
                            .literal("cube")
                            .then(
                                Commands.literal("enter").executes { context ->
                                    val lab = CubePlanetLab.forServer(context.source.server)
                                    try {
                                        if (!lab.enter(context.source.playerOrException)) failure("Cube entry is obstructed.")
                                    } catch (error: IllegalStateException) {
                                        failure(error.message ?: "Cube preparation failed.")
                                    }
                                    context.source.sendSuccess({
                                        Component.literal(
                                            "Cube proof of concept: walk forward across the orange edge. Use /orbitalis lab cube leave to return.",
                                        )
                                    }, false)
                                    1
                                },
                            ).then(
                                Commands.literal("leave").executes { context ->
                                    if (!CubePlanetLab
                                            .forServer(
                                                context.source.server,
                                            ).leave(context.source.playerOrException)
                                    ) {
                                        failure("Enter the cube scene first.")
                                    }
                                    1
                                },
                            ).then(
                                Commands.literal("rebuild").executes { context ->
                                    val count =
                                        try {
                                            CubePlanetLab.forServer(context.source.server).rebuild()
                                        } catch (error: IllegalStateException) {
                                            failure(error.message ?: "Cube rebuild failed.")
                                        }
                                    context.source.sendSuccess(
                                        {
                                            Component.literal(
                                                "Rebuilt disposable cube ($count blocks); previous fixture edits were removed.",
                                            )
                                        },
                                        true,
                                    )
                                    1
                                },
                            ).then(
                                Commands.literal("sample").executes { context ->
                                    val player = context.source.playerOrException
                                    if (!CubePlayerPhysics.active(player)) failure("Enter the cube scene first.")
                                    val p = CubePlayerPhysics.centre(player).subtract(CubePlayerPhysics.CENTRE)
                                    val gravity =
                                        CubePlayerPhysics.GRAVITY.acceleration(
                                            org.cbi.orbitalis.core.planet
                                                .Vector3(p.x, p.y, p.z),
                                        )
                                    context.source.sendSuccess({
                                        Component.literal(
                                            "Collider up=${CubePlayerPhysics.up(
                                                player,
                                            )}; gravity=$gravity; centre=$p; velocity=${player.deltaMovement}; grounded=${player.onGround()}",
                                        )
                                    }, false)
                                    1
                                },
                            ),
                    ),
            ),
        )
    }
}
