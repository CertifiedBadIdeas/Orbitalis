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
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
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
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.cbi.orbitalis.core.planet.SurfaceFlight
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
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post -> scenes[event.server]?.sampleRider() }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppingEvent -> scenes.remove(event.server)?.clear() }
    }

    fun scene(server: MinecraftServer): SableLab =
        scenes.getOrPut(server) {
            SableLab(checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION)))
        }

    private fun startScene(
        lab: SableLab,
        orbital: Boolean,
    ) {
        try {
            if (orbital) lab.startOrbit() else lab.start()
        } catch (error: IllegalArgumentException) {
            throw SimpleCommandExceptionType(Component.literal(error.message ?: "Scene start rejected.")).create()
        }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("orbitalis").requires { it.hasPermission(2) }
        val sable = Commands.literal("sable")
        for (action in listOf("start", "orbit", "flight", "platform", "board", "sample", "shift", "clear")) {
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
                                startScene(lab, false)
                                "Started two Sable probes: a 0.5 s force pulse followed by coasting. Use sample or clear."
                            }

                            "orbit" -> {
                                startScene(lab, true)
                                "Started two Sable constructions on a 16 m circular orbit: central gravity, initial speed 8 m/s. Use sample or clear."
                            }

                            "flight" -> {
                                try {
                                    lab.startFlight()
                                } catch (error: IllegalArgumentException) {
                                    throw SimpleCommandExceptionType(Component.literal(error.message ?: "Flight start rejected.")).create()
                                }
                                "Started one Sable surface flight: takeoff, insertion, unpowered orbit and return to the pad at (0,104,120). Use sample or clear."
                            }

                            "platform" -> {
                                try {
                                    lab.startPlatform()
                                } catch (error: IllegalArgumentException) {
                                    throw SimpleCommandExceptionType(Component.literal(error.message ?: "Platform rejected.")).create()
                                }
                                "Prepared a 3x3 Sable deck at (0,105,120). Disable creative flight, then use board and launch short|orbit."
                            }

                            "board" -> {
                                try {
                                    lab.board(source.playerOrException)
                                } catch (error: IllegalArgumentException) {
                                    throw SimpleCommandExceptionType(Component.literal(error.message ?: "Boarding rejected.")).create()
                                }
                                "Boarded once. Release movement keys and wait for contact before launch short|orbit; sample reports native support."
                            }

                            "clear" -> {
                                lab.clear()
                                "Cleared Orbitalis diagnostic constructions and their loading tickets."
                            }

                            "shift" -> {
                                val player = source.playerOrException
                                try {
                                    lab.requestShift(Vec3(32.0, 0.0, 0.0), player)
                                } catch (error: IllegalArgumentException) {
                                    throw SimpleCommandExceptionType(Component.literal(error.message ?: "Shift rejected.")).create()
                                }
                                "Queued a 32 m frame shift. An airborne rider platform also relocates its owned pad; sample reports cancellation."
                            }

                            else -> {
                                if (lab.size == 0) {
                                    throw SimpleCommandExceptionType(
                                        Component.literal(lab.failureReason ?: "Start the Sable scene first."),
                                    ).create()
                                }
                                val a = lab.reading(0)
                                val orbit = lab.orbitMetrics
                                val flight = lab.flightReport
                                if (flight != null) {
                                    val state = lab.orbitalState(0)
                                    String.format(
                                        Locale.ROOT,
                                        "Sable flight %s; t=%.2f s; radius=%.3f m; speed=%.3f m/s; thrust=%.3f m/s²; coast=%.3f turns; max coast energy error=%.6g",
                                        flight.phase,
                                        flight.seconds,
                                        state.position.length(),
                                        state.velocity.length(),
                                        flight.thrust.length(),
                                        flight.coastAngle / (2.0 * Math.PI),
                                        flight.maximumCoastEnergyError,
                                    )
                                } else if (orbit != null) {
                                    val radius = lab.orbitalState(0).position.length()
                                    String.format(
                                        Locale.ROOT,
                                        "Sable orbit t=%.3f s; gold radius=%.4f m; speed=%.4f m/s; max position error=%.6f m; max energy error=%.6g; shifts=%d; burns=%d; pending=%s",
                                        orbit.seconds,
                                        radius,
                                        a.velocity.length(),
                                        orbit.maximumPositionError,
                                        orbit.maximumRelativeEnergyError,
                                        lab.completedShifts,
                                        lab.completedBurns,
                                        lab.burnPending,
                                    ) +
                                        (0..1).joinToString("") { index ->
                                            val apsides = lab.orbitApsides(index)
                                            if (apsides == null) {
                                                "; probe $index escaping"
                                            } else {
                                                String.format(
                                                    Locale.ROOT,
                                                    "; probe %d peri=%.3f m apo=%.3f m",
                                                    index,
                                                    apsides.periapsis,
                                                    apsides.apoapsis,
                                                )
                                            }
                                        } + (lab.burnRejection?.let { "; $it" } ?: "")
                                } else {
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
                        }
                    val rider = lab.riderReport
                    val telemetry =
                        if (rider == null) {
                            ""
                        } else {
                            String.format(
                                Locale.ROOT,
                                "; rider supported=%s; local feet=(%.3f,%.3f,%.3f); contact/lost ticks=%d/%d; max deck drift=%.3f m; max height error=%.3f m",
                                rider.supported,
                                rider.localFeet.x,
                                rider.localFeet.y,
                                rider.localFeet.z,
                                rider.supportedTicks,
                                rider.unsupportedTicks,
                                rider.maximumHorizontalDrift,
                                rider.maximumHeightError,
                            )
                        }
                    source.sendSuccess({ Component.literal(message + telemetry + (lab.shiftRejection?.let { "; $it" } ?: "")) }, false)
                    1
                },
            )
        }
        val launch = Commands.literal("launch")
        for ((name, itinerary) in listOf("short" to SurfaceFlight.Itinerary.HOP, "orbit" to SurfaceFlight.Itinerary.ORBIT)) {
            launch.then(
                Commands.literal(name).executes { context ->
                    val source = context.source
                    if (source.level.dimension() != PlanetLabCoordinates.DIMENSION) {
                        throw SimpleCommandExceptionType(Component.literal("Enter the planet laboratory first.")).create()
                    }
                    try {
                        scene(source.server).launch(itinerary, source.playerOrException)
                    } catch (error: IllegalArgumentException) {
                        throw SimpleCommandExceptionType(Component.literal(error.message ?: "Launch rejected.")).create()
                    }
                    source.sendSuccess({
                        Component.literal("Started $name platform flight. Use sample to inspect contact and drift, or clear to return.")
                    }, false)
                    1
                },
            )
        }
        sable.then(launch)
        val burn = Commands.literal("burn")
        for (direction in listOf("prograde", "retrograde")) {
            burn.then(
                Commands.literal(direction).then(
                    Commands.argument("probe", IntegerArgumentType.integer(0, 1)).then(
                        Commands.argument("deltaV", DoubleArgumentType.doubleArg(0.01, 1.0)).executes { context ->
                            val source = context.source
                            if (source.level.dimension() != PlanetLabCoordinates.DIMENSION) {
                                throw SimpleCommandExceptionType(Component.literal("Enter the planet laboratory first.")).create()
                            }
                            try {
                                scene(source.server).requestBurn(
                                    IntegerArgumentType.getInteger(context, "probe"),
                                    direction == "prograde",
                                    DoubleArgumentType.getDouble(context, "deltaV"),
                                )
                            } catch (error: IllegalArgumentException) {
                                throw SimpleCommandExceptionType(Component.literal(error.message ?: "Burn rejected.")).create()
                            }
                            source.sendSuccess(
                                { Component.literal("Queued $direction impulse; use sample to inspect both orbits and any cancellation.") },
                                false,
                            )
                            1
                        },
                    ),
                ),
            )
        }
        sable.then(burn)
        dispatcher.register(root.then(Commands.literal("lab").then(sable)))
    }
}
