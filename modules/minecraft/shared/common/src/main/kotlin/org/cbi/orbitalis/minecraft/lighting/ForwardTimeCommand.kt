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

package org.cbi.orbitalis.minecraft.lighting

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation

/** Accelerate actual server ticks; never teleport clock epochs past unintegrated object states. */
object ForwardTimeCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val time = Commands.literal("time")

        fun start(
            source: CommandSourceStack,
            ticks: Int,
        ): Int {
            val server = source.server
            val manager = server.tickRateManager()
            if (manager.isFrozen || manager.isSprinting || !OrbitalSimulation.canAccelerate(server) ||
                !OrbitalSimulation.mayAdvance(server)
            ) {
                source.sendFailure(
                    Component.literal("Cannot accelerate: loaded ships, paused simulation or an existing tick sprint. Unload ships first."),
                )
                return 0
            }
            manager.requestGameToSprint(ticks)
            source.sendSuccess({
                Component.literal(
                    "Advancing $ticks real simulation ticks forwards as fast as the server can run. Use /orbitalis time stop to stop.",
                )
            }, true)
            return 1
        }
        time.then(
            Commands.literal("add").then(
                Commands.argument("seconds", IntegerArgumentType.integer(1, 1200)).executes {
                    start(it.source, IntegerArgumentType.getInteger(it, "seconds") * 20)
                },
            ),
        )
        val set = Commands.literal("set")

        fun next(
            source: CommandSourceStack,
            daytime: Int,
        ): Int {
            val profile = OrbitalSimulation.profile(source.server)
            val tick = OrbitalSimulation.data(source.server)?.clock
            if (profile == null || tick == null) {
                source.sendFailure(Component.literal("Physical sky metadata unavailable."))
                return 0
            }
            return start(source, ForwardSkyTime.ticksUntil(profile, tick, daytime))
        }
        for ((name, value) in mapOf(
            "sunrise" to 0,
            "day" to 1000,
            "noon" to 6000,
            "sunset" to 12000,
            "night" to 13000,
            "midnight" to 18000,
        )) {
            set.then(Commands.literal(name).executes { next(it.source, value) })
        }
        set.then(
            Commands.argument("daytime", IntegerArgumentType.integer(0, 23999)).executes {
                next(it.source, IntegerArgumentType.getInteger(it, "daytime"))
            },
        )
        time.then(set)
        time.then(
            Commands.literal("stop").executes {
                val stopped =
                    it.source.server
                        .tickRateManager()
                        .stopSprinting()
                it.source.sendSuccess(
                    { Component.literal(if (stopped) "Time acceleration stopped." else "No time acceleration is running.") },
                    true,
                )
                if (stopped) 1 else 0
            },
        )
        dispatcher.register(Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(time))
    }
}
