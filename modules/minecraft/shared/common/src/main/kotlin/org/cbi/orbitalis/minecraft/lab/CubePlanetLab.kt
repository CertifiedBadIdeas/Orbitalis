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

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Explicitly built disposable fixture. Existing blocks and excavations survive repeated entry. */
class CubePlanetLab(
    private val server: MinecraftServer,
) {
    private data class ReturnPoint(
        val dimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
    )

    private val returns = mutableMapOf<UUID, ReturnPoint>()
    private val terrain: CubePlanetTerrain by lazy {
        val level = checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION))
        checkNotNull(CubePlanetTerrain.factory) { "The local-frame cube requires Sable." }(level)
    }

    fun prepare(): Int = terrain.prepare()

    /** Explicit disposable-scene reset. Ordinary entry must continue to preserve edits. */
    fun rebuild(): Int {
        val activeTerrain = terrain
        val level = checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION))
        check(level.players().none { CubePlayerPhysics.active(it) }) { "Leave the cube scene before rebuilding it." }
        val origin = BlockPos.containing(CubePlayerPhysics.CENTRE)
        for (x in -20..19) {
            for (z in -20..19) {
                for (y in -20..19) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)
                }
            }
        }
        activeTerrain.clear()
        return prepare()
    }

    fun enter(
        player: ServerPlayer,
        face: Direction = Direction.UP,
    ): Boolean {
        prepare()
        val level = checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION))
        val outside = !CubePlayerPhysics.active(player)
        val saved = ReturnPoint(player.level().dimension(), player.position(), player.yRot, player.xRot)
        // Clear lane outside the relief strips; network position remains anchored in world Y.
        val target =
            CubePlayerPhysics.CENTRE
                .add(CubePlayerPhysics.rotate(Vec3(10.0, 16.95, -10.0), face))
                .add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0)
        val centre = target.add(0.0, CubePlayerPhysics.HALF_HEIGHT, 0.0)
        val half =
            Vec3(
                if (face.axis ==
                    Direction.Axis.X
                ) {
                    .9
                } else {
                    .3
                },
                if (face.axis == Direction.Axis.Y) .9 else .3,
                if (face.axis == Direction.Axis.Z) .9 else .3,
            )
        val bounds =
            net.minecraft.world.phys
                .AABB(centre.subtract(half), centre.add(half))
        if (!level.noCollision(player, bounds) || !terrain.clearForEntry(bounds)) return false
        val mode = player as CubePlayerMode
        val wasEnrolled = mode.orbitalisCubeEnrolled()
        mode.orbitalisSetCubeEnrolled(true)
        (player as CubePlayerFrame).orbitalisSetUp(face)
        val moved = player.teleportTo(level, target.x, target.y, target.z, emptySet(), -90f, 0f)
        if (moved) {
            if (outside) returns[player.uuid] = saved
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
            CubePlayerPhysics.refresh(player)
        }
        if (!moved) mode.orbitalisSetCubeEnrolled(wasEnrolled)
        return moved
    }

    fun leave(player: ServerPlayer): Boolean {
        if (player.level().dimension() != PlanetLabCoordinates.DIMENSION ||
            (!CubePlayerPhysics.active(player) && player.uuid !in returns)
        ) {
            return false
        }
        val saved = returns[player.uuid]
        val level = saved?.dimension?.let(server::getLevel) ?: server.overworld()
        val pos =
            if (saved != null &&
                level.dimension() == saved.dimension
            ) {
                saved.position
            } else {
                Vec3.atBottomCenterOf(level.sharedSpawnPos.above())
            }
        (player as CubePlayerFrame).orbitalisSetUp(null)
        val moved = player.teleportTo(level, pos.x, pos.y, pos.z, emptySet(), saved?.yaw ?: 0f, saved?.pitch ?: 0f)
        if (moved) {
            (player as CubePlayerMode).orbitalisSetCubeEnrolled(false)
            player.setPos(player.position())
            returns.remove(player.uuid)
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
        }
        return moved
    }

    companion object {
        private val servers = mutableMapOf<MinecraftServer, CubePlanetLab>()

        fun forServer(server: MinecraftServer): CubePlanetLab = servers.getOrPut(server) { CubePlanetLab(server) }

        fun forget(server: MinecraftServer) {
            servers.remove(server)
        }
    }
}
