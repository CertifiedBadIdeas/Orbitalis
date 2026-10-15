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
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.cbi.orbitalis.minecraft.integration.sable.FlightTestTravel
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import java.util.Locale

/** Registered only with Sable present. No Create or Propulsion classes cross this boundary. */
object SableFlightIntegration {
    private val travel = mutableMapOf<MinecraftServer, FlightTestTravel>()

    fun install() {
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent -> register(event.dispatcher) }
        NeoForge.EVENT_BUS.addListener { event: ForgeSablePrePhysicsTickEvent ->
            SableFlightGravity.beforePhysics(event.physicsSystem.level, event.timeStep)
        }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppedEvent -> travel.remove(event.server) }
    }

    private fun failure(message: String): Nothing = throw SimpleCommandExceptionType(Component.literal(message)).create()

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val flight = Commands.literal("flight")
        for (action in listOf("enter", "leave", "sample")) {
            flight.then(
                Commands.literal(action).executes { context ->
                    val source = context.source
                    val player = source.playerOrException
                    val access = travel.getOrPut(source.server) { FlightTestTravel(source.server) }
                    val message =
                        try {
                            when (action) {
                                "enter" -> {
                                    if (!access.enter(player)) failure("Player transfer was rejected")
                                    "Entered flight test terrain. Build and assemble a ship here; only players are transferred. Use /orbitalis flight leave to return."
                                }

                                "leave" -> {
                                    if (!access.leave(player)) failure("Player transfer was rejected")
                                    "Left flight test space; ships remain there."
                                }

                                else -> {
                                    if (source.level.dimension() !=
                                        SableFlightGravity.DIMENSION
                                    ) {
                                        failure("Enter the flight test space first")
                                    }
                                    val body =
                                        Sable.HELPER.getTrackingSubLevel(player) as? ServerSubLevel
                                            ?: failure("Stand on a Sable ship and wait for native contact")
                                    val sample = SableFlightGravity.reading(body)
                                    String.format(
                                        Locale.ROOT,
                                        "Ship %s; mass=%.3f Sable units; centre-of-mass=(%.2f,%.2f,%.2f) m; altitude=%.2f m; speed=%.3f m/s; gravity=(%.4f,%.4f,%.4f) m/s²; |g|=%.4f m/s²",
                                        body.uniqueId,
                                        sample.mass,
                                        sample.position.x,
                                        sample.position.y,
                                        sample.position.z,
                                        (sample.position - SableFlightGravity.CENTRE).length() - SableFlightGravity.field.radius,
                                        sample.velocity.length(),
                                        sample.acceleration.x,
                                        sample.acceleration.y,
                                        sample.acceleration.z,
                                        sample.acceleration.length(),
                                    )
                                }
                            }
                        } catch (error: IllegalArgumentException) {
                            failure(error.message ?: "Flight command rejected")
                        }
                    source.sendSuccess({ Component.literal(message) }, false)
                    1
                },
            )
        }
        dispatcher.register(Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(flight))
    }
}
