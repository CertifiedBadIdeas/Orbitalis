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

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs

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
        val p = FlightSpaceCoordinates.globalPosition(level, Vector3(player.x, player.y, player.z))
        val approaching =
            FlatPlanetSpace.contains(level) && abs(p.x) < FlatPlanetSpace.HALF_SIZE && abs(p.z) < FlatPlanetSpace.HALF_SIZE &&
                (
                    if (level.dimension() ==
                        Level.OVERWORLD
                    ) {
                        p.y >= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.PRELOAD_HEIGHT && player.deltaMovement.y >= 0
                    } else {
                        p.y in FlatPlanetSpace.SEA_LEVEL..(FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT) &&
                            player.deltaMovement.y <= 0
                    }
                )
        if (!approaching) {
            close(player)
            return
        }
        val other = if (level.dimension() == Level.OVERWORLD) FlatPlanetSpace.SPACE else Level.OVERWORLD
        val target = player.server.getLevel(other) ?: return
        preloads.getOrPut(player) { ShipDestinationPreload() }.update(target, FlightSpaceCoordinates.localPosition(other, p))
    }

    fun destination(
        level: Level,
        position: Vector3,
        verticalSpeed: Double,
    ): ResourceKey<Level>? {
        if (!FlatPlanetSpace.contains(level) || abs(position.x) >= FlatPlanetSpace.HALF_SIZE ||
            abs(position.z) >= FlatPlanetSpace.HALF_SIZE
        ) {
            return null
        }
        val p = FlightSpaceCoordinates.globalPosition(level, position)
        return when {
            level.dimension() == Level.OVERWORLD &&
                p.y >= FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT && verticalSpeed >= 0 -> FlatPlanetSpace.SPACE

            level.dimension() == FlatPlanetSpace.SPACE &&
                p.y in FlatPlanetSpace.SEA_LEVEL..(FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT) &&
                verticalSpeed <= 0 -> Level.OVERWORLD

            else -> null
        }
    }

    fun transfer(player: ServerPlayer): Boolean {
        if (!player.isAlive || player.isPassenger || player.isVehicle) {
            close(player)
            return false
        }
        preload(player)
        val current = Vector3(player.x, player.y, player.z)
        val key = destination(player.level(), current, player.deltaMovement.y) ?: return false
        val target = player.server.getLevel(key) ?: return false
        val p = FlightSpaceCoordinates.localPosition(key, FlightSpaceCoordinates.globalPosition(player.level(), current))
        target.getChunk(
            BlockPos
                .containing(p.x, p.y, p.z),
        )
        val velocity = player.deltaMovement
        if (!player.teleportTo(target, p.x, p.y, p.z, emptySet(), player.yRot, player.xRot)) return false
        player.deltaMovement = velocity
        player.fallDistance = 0f
        player.hasImpulse = true
        // Absolute teleport packets reset client velocity. Restore it after the position packet.
        player.connection.send(
            net.minecraft.network.protocol.game
                .ClientboundSetEntityMotionPacket(player),
        )
        close(player)
        return true
    }
}
