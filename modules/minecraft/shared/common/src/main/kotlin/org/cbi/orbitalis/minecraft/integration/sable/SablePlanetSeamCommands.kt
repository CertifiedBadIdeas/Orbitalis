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

package org.cbi.orbitalis.minecraft.integration.sable

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

/** Transient override of the default native block-interaction experiment on the existing small cube. */
object SablePlanetSeamCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(
                Commands.literal("planet").then(
                    Commands.literal("seams").then(
                        Commands.argument("enabled", BoolArgumentType.bool()).executes {
                            val source = it.source
                            if (source.level.dimension() != PlanetLabCoordinates.DIMENSION) {
                                source.sendFailure(Component.literal("Enter the laboratory cube dimension first."))
                                0
                            } else {
                                val value = BoolArgumentType.getBool(it, "enabled")
                                PlanetNeighbours.enable(source.level, value)
                                source.sendSuccess({ Component.literal("Experimental planet neighbour hooks: $value") }, false)
                                1
                            }
                        },
                    ),
                ),
            ),
        )
    }
}
