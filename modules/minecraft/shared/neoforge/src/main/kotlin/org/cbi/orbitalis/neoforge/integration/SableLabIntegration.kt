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

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import org.cbi.orbitalis.minecraft.integration.sable.SableLab
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import java.util.Locale

/** Loaded only after the loader has confirmed Sable is installed. */
object SableLabIntegration {
    private val scenes = mutableMapOf<MinecraftServer, SableLab>()

    fun install() {
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent -> register(event.dispatcher) }
        NeoForge.EVENT_BUS.addListener { event: ForgeSablePrePhysicsTickEvent ->
            scenes[event.physicsSystem.level.server]?.takeIf { it.level === event.physicsSystem.level }?.beforePhysics(event.timeStep)
        }
        NeoForge.EVENT_BUS.addListener { event: ForgeSablePostPhysicsTickEvent ->
            scenes[event.physicsSystem.level.server]?.takeIf { it.level === event.physicsSystem.level }?.afterPhysics(event.timeStep)
        }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppingEvent -> scenes.remove(event.server)?.clear() }
    }

    fun scene(server: MinecraftServer): SableLab =
        scenes.getOrPut(server) {
            SableLab(checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION)))
        }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("orbitalis").requires { it.hasPermission(2) }
        val sable = Commands.literal("sable")
        for (action in listOf("start", "sample", "shift", "clear")) {
            sable.then(
                Commands.literal(action).executes { context ->
                    val source = context.source
                    if (source.level.dimension() != PlanetLabCoordinates.DIMENSION) {
                        throw SimpleCommandExceptionType(Component.literal("Enter the planet laboratory first.")).create()
                    }
                    val lab = scene(source.server)
                    val message =
                        when (action) {
                            "start" -> {
                                lab.start()
                                "Started two Sable probes: a 0.5 s force pulse followed by coasting. Use sample or clear."
                            }

                            "clear" -> {
                                lab.clear()
                                "Cleared Orbitalis diagnostic constructions and their loading tickets."
                            }

                            "shift" -> {
                                val player = source.playerOrException
                                if (!player.isCreative || player.isPassenger || lab.size != 2 || lab.shiftPending) {
                                    throw SimpleCommandExceptionType(
                                        Component.literal(
                                            "Start two probes and wait for any pending shift; an unmounted creative observer is required.",
                                        ),
                                    ).create()
                                }
                                lab.requestShift(Vec3(32.0, 0.0, 0.0), player)
                                "Queued a 32 m frame shift for both probes and this observer. The static platforms remain in place."
                            }

                            else -> {
                                if (lab.size !=
                                    2
                                ) {
                                    throw SimpleCommandExceptionType(Component.literal("Start the Sable scene first.")).create()
                                }
                                val a = lab.reading(0)
                                String.format(
                                    Locale.ROOT,
                                    "Sable t=%.3f s; callbacks=%d; dt=%.6f s; mass=%.3f Sable units; speed=%.6f m/s; expected delta-v=%.6f m/s; shifts=%d",
                                    lab.physicsSeconds,
                                    lab.callbacks,
                                    lab.lastStepSeconds,
                                    a.mass,
                                    a.velocity.length(),
                                    lab.commandedDeltaV,
                                    lab.completedShifts,
                                )
                            }
                        }
                    source.sendSuccess({ Component.literal(message) }, false)
                    1
                },
            )
        }
        dispatcher.register(root.then(Commands.literal("lab").then(sable)))
    }
}
