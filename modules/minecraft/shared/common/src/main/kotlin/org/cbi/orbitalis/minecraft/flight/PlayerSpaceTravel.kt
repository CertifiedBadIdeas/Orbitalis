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
        if (!FlatPlanetSpace.contains(level)) {
            close(player)
            return
        }
        val p = FlightSpaceCoordinates.globalPosition(level, Vector3(player.x, player.y, player.z))
        preloads.getOrPut(player) { ShipDestinationPreload() }.approach(level as net.minecraft.server.level.ServerLevel, p)
    }

    fun destination(
        level: Level,
        position: Vector3,
    ): ResourceKey<Level>? {
        if (!FlatPlanetSpace.contains(level)) return null
        val distance = PlanetFlightEnvelope.distance(FlightSpaceCoordinates.globalPosition(level, position))
        return when {
            level.dimension() == Level.OVERWORLD && distance >= FlatPlanetSpace.ASCENT_HEIGHT -> FlatPlanetSpace.SPACE
            level.dimension() == FlatPlanetSpace.SPACE && distance <= FlatPlanetSpace.DESCENT_HEIGHT -> Level.OVERWORLD
            else -> null
        }
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
        val key = destination(player.level(), current) ?: return false
        val target = player.server.getLevel(key) ?: return false
        val p = FlightSpaceCoordinates.localPosition(key, FlightSpaceCoordinates.globalPosition(player.level(), current))
        if (preloads[player]?.ready != true) return false
        val motionToken = PlayerFlightMotion.capture(player)
        if (!player.teleportTo(target, p.x, p.y, p.z, emptySet(), player.yRot, player.xRot)) return false
        player.fallDistance = 0f
        PlayerFlightMotion.restore(player, velocity, motionToken)
        close(player)
        return true
    }
}
