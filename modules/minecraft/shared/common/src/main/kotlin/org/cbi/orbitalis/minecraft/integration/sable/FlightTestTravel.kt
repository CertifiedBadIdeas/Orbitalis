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

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Player-only operator travel. Return points are transient; ships always stay in their own Level. */
class FlightTestTravel(
    private val server: MinecraftServer,
) {
    private data class ReturnPoint(
        val dimension: ResourceKey<Level>,
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
    )

    private val returns = mutableMapOf<UUID, ReturnPoint>()

    fun enter(player: ServerPlayer): Boolean {
        require(player.isCreative && !player.isPassenger) { "Enter as an unmounted creative player" }
        require(player.level().dimension() != SableFlightGravity.DIMENSION) { "You are already in the flight test space" }
        val level = checkNotNull(server.getLevel(SableFlightGravity.DIMENSION)) { "Flight test dimension is unavailable" }
        // Level height queries return minBuildHeight when the destination chunk is not loaded.
        level.getChunkAt(BlockPos.ZERO)
        val spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO)
        val saved = ReturnPoint(player.level().dimension(), player.position(), player.yRot, player.xRot)
        val moved = player.teleportTo(level, spawn.x + 0.5, spawn.y + 1.0, spawn.z + 0.5, emptySet(), 0f, 0f)
        if (moved) {
            returns[player.uuid] = saved
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
        }
        return moved
    }

    fun leave(player: ServerPlayer): Boolean {
        require(!player.isPassenger) { "Dismount before leaving the flight test space" }
        require(SableFlightGravity.isFlightSpace(player.level())) { "Enter the flight test space first" }
        val saved = returns[player.uuid]
        val level = saved?.let { server.getLevel(it.dimension) } ?: server.overworld()
        val position =
            saved?.takeIf { it.dimension == level.dimension() }?.position ?: run {
                level.getChunkAt(level.sharedSpawnPos)
                Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.sharedSpawnPos))
            }
        val moved = player.teleportTo(level, position.x, position.y, position.z, emptySet(), saved?.yaw ?: 0f, saved?.pitch ?: 0f)
        if (moved) {
            returns.remove(player.uuid)
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
        }
        return moved
    }
}
