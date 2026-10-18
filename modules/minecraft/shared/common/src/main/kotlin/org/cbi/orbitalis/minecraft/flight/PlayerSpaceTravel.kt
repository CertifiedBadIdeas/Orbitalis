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

package org.cbi.orbitalis.minecraft.flight

import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.PlanetFlightEnvelope
import org.cbi.orbitalis.core.planet.Vector3

/** Native player travel, independent of optional Sable classes and test guidance. */
object PlayerSpaceTravel {
    private val preloads = mutableMapOf<ServerPlayer, ShipDestinationPreload>()

    fun close(player: ServerPlayer) {
        preloads.remove(player)?.close()
    }

    fun close(server: MinecraftServer) {
        preloads.keys
            .filter { it.server === server }
            .toList()
            .forEach(::close)
    }

    private fun preload(player: ServerPlayer) {
        val level = player.level()
        if (!FlatPlanetSpace.contains(level) || !CelestialFlight.available(level)) {
            close(player)
            return
        }
        val p = FlightSpaceCoordinates.globalPosition(level, Vector3(player.x, player.y, player.z))
        preloads.getOrPut(player) { ShipDestinationPreload() }.approach(level as net.minecraft.server.level.ServerLevel, p)
    }

    fun destination(
        level: Level,
        position: Vector3,
    ): ResourceKey<Level>? = destination(level, position, Vector3.ZERO)

    fun destination(
        level: Level,
        position: Vector3,
        verticalSpeed: Double,
    ): ResourceKey<Level>? = destination(level, position, Vector3(0.0, verticalSpeed, 0.0))

    fun destination(
        level: Level,
        position: Vector3,
        velocity: Vector3,
    ): ResourceKey<Level>? {
        if (level !is net.minecraft.server.level.ServerLevel || !FlatPlanetSpace.contains(level) ||
            !CelestialFlight.available(level)
        ) {
            return null
        }
        return CelestialFlight.sample(level.server).destination(
            level,
            org.cbi.orbitalis.core.planet
                .CartesianState(position, velocity),
        )
    }

    fun transfer(player: ServerPlayer): Boolean {
        if (!player.isAlive || player.isPassenger || player.isVehicle) {
            close(player)
            return false
        }
        val velocity = player.deltaMovement
        if (!velocity.x.isFinite() || !velocity.y.isFinite() || !velocity.z.isFinite() || velocity.length() > 500.0) {
            close(player)
            return false
        }
        preload(player)
        val current = Vector3(player.x, player.y, player.z)
        val sampledVerticalSpeed =
            if (player.level().dimension() ==
                org.cbi.orbitalis.minecraft.moon.MoonWorld.KEY
            ) {
                velocity.y * 20.0
            } else {
                preloads[player]?.sampledVelocity?.y
                    ?: (velocity.y * 20.0)
            }
        val key = destination(player.level(), current, Vector3(velocity.x * 20.0, sampledVerticalSpeed, velocity.z * 20.0)) ?: return false
        val target = player.server.getLevel(key) ?: return false
        val sample = CelestialFlight.sample(player.server)
        val state =
            sample.state(
                org.cbi.orbitalis.core.planet
                    .CartesianState(current, Vector3(velocity.x, velocity.y, velocity.z) * 20.0),
                player.level().dimension(),
                key,
            )
        val p = state.position
        val direction =
            sample.direction(
                Vector3(player.lookAngle.x, player.lookAngle.y, player.lookAngle.z),
                player.level().dimension(),
                key,
            )
        val yaw = Math.toDegrees(kotlin.math.atan2(-direction.x, direction.z)).toFloat()
        val pitch = Math.toDegrees(kotlin.math.asin((-direction.y).coerceIn(-1.0, 1.0))).toFloat()
        if (preloads[player]?.ready != true) return false
        val sourceDimension = player.level().dimension()
        val motionToken = PlayerFlightMotion.capture(player)
        if (!player.teleportTo(target, p.x, p.y, p.z, emptySet(), yaw, pitch)) return false
        player.fallDistance = 0f
        PlayerFlightMotion.restore(
            player,
            net.minecraft.world.phys.Vec3(
                state.velocity.x / 20.0,
                state.velocity.y / 20.0,
                state.velocity.z / 20.0,
            ),
            motionToken,
            org.cbi.orbitalis.core.orbit.Rotation3.fromAxes(
                sample.direction(Vector3(1.0, 0.0, 0.0), sourceDimension, key),
                sample.direction(Vector3(0.0, 1.0, 0.0), sourceDimension, key),
                sample.direction(Vector3(0.0, 0.0, 1.0), sourceDimension, key),
            ),
            sample
                .state(
                    org.cbi.orbitalis.core.planet
                        .CartesianState(current, Vector3.ZERO),
                    sourceDimension,
                    key,
                ).velocity * 0.05,
        )
        close(player)
        return true
    }
}
