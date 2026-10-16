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
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.CubicPlanetFixture
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
    private var prepared = false

    fun prepare(): Int {
        if (prepared) return 0
        val level = checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION))
        val origin = BlockPos.containing(CubePlayerPhysics.CENTRE)
        // A marker avoids refilling excavations when this runtime is recreated after restart.
        if (level.getBlockState(origin).`is`(Blocks.LODESTONE)) {
            prepared = true
            return 0
        }
        // Reject an occupied site before any writes instead of overwriting somebody's laboratory.
        for (x in -20..19) {
            for (z in -20..19) {
                for (y in -20..19) {
                    check(
                        level.getBlockState(origin.offset(x, y, z)).isAir,
                    ) { "Cube fixture site is occupied; use a fresh disposable world." }
                }
            }
        }
        var changed = 0
        for (x in -20..19) {
            for (z in -20..19) {
                for (y in -20..19) {
                    if (!CubicPlanetFixture.solid(x, y, z)) continue
                    val block =
                        when {
                            y >= 15 -> Blocks.GREEN_CONCRETE
                            x >= 15 -> Blocks.ORANGE_CONCRETE
                            z >= 15 -> Blocks.BLUE_CONCRETE
                            x <= -16 -> Blocks.RED_CONCRETE
                            y <= -16 -> Blocks.PURPLE_CONCRETE
                            z <= -16 -> Blocks.YELLOW_CONCRETE
                            else -> Blocks.STONE
                        }
                    if (level.setBlock(origin.offset(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS)) changed++
                }
            }
        }
        check(level.setBlock(origin, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_CLIENTS)) { "Cube marker placement failed." }
        prepared = true
        return changed
    }

    /** Explicit disposable-scene reset. Ordinary entry must continue to preserve edits. */
    fun rebuild(): Int {
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
        prepared = false
        return prepare()
    }

    fun enter(player: ServerPlayer): Boolean {
        prepare()
        val level = checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION))
        val outside = !CubePlayerPhysics.active(player)
        val saved = ReturnPoint(player.level().dimension(), player.position(), player.yRot, player.xRot)
        // The raised +Y terrain ends at y=115; start near its +X edge looking towards it.
        val target = CubePlayerPhysics.CENTRE.add(10.0, 19.05, 0.5)
        if (!level.noCollision(
                player,
                net.minecraft.world.phys.AABB(
                    target.x - 0.3,
                    target.y,
                    target.z - 0.3,
                    target.x + 0.3,
                    target.y + 1.8,
                    target.z + 0.3,
                ),
            )
        ) {
            return false
        }
        val mode = player as CubePlayerMode
        val wasEnrolled = mode.orbitalisCubeEnrolled()
        mode.orbitalisSetCubeEnrolled(true)
        (player as CubePlayerFrame).orbitalisSetUp(null)
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
