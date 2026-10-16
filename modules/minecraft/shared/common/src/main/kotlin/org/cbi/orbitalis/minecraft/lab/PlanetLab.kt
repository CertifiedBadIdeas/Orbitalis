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
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CubeFace
import org.cbi.orbitalis.core.planet.TwoBodyGravity
import org.cbi.orbitalis.core.planet.Vector3
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Server-owned, opt-in diagnostic scene. It does not persist or transport spacecraft. */
class PlanetLab(
    private val server: MinecraftServer,
) {
    private data class ReturnPoint(
        val dimension: ResourceKey<Level>,
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
    )

    private val returns = mutableMapOf<UUID, ReturnPoint>()
    private val radius = PlanetLabCoordinates.planet.radiusMetres
    private val gravity = TwoBodyGravity(9.81 * radius * radius)
    private val semiMajorAxis = radius * 1.2
    private val period = 2 * PI * sqrt(semiMajorAxis * semiMajorAxis * semiMajorAxis / gravity.mu)
    var orbitState: CartesianState? = null
        private set
    var elapsedSeconds = 0.0
        private set
    private var ticks = 0

    fun level(): ServerLevel =
        checkNotNull(server.getLevel(PlanetLabCoordinates.DIMENSION)) { "Planet laboratory dimension is unavailable" }

    /** Fill only air cells in this bounded laboratory pad. Existing solid blocks are preserved. */
    fun prepareChart(
        level: ServerLevel,
        face: CubeFace,
    ): Int {
        require(level.dimension() == PlanetLabCoordinates.DIMENSION) { "Pads may only be placed in the laboratory" }
        val centre = PlanetLabCoordinates.chartCentre(face)
        var changed = 0
        for (x in -PlanetLabCoordinates.HALF_WIDTH..PlanetLabCoordinates.HALF_WIDTH) {
            for (z in -PlanetLabCoordinates.HALF_WIDTH..PlanetLabCoordinates.HALF_WIDTH) {
                val pos = centre.offset(x, 0, z)
                if (level.getBlockState(pos).isAir) {
                    val block = if (x % 4 == 0 || z % 4 == 0) Blocks.SMOOTH_QUARTZ else Blocks.BLUE_CONCRETE
                    if (level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS)) changed++
                }
            }
        }
        return changed
    }

    fun enter(
        player: ServerPlayer,
        face: CubeFace,
    ): Boolean {
        val level = level()
        prepareChart(level, face)
        val centre = PlanetLabCoordinates.chartCentre(face)
        if (!level.getBlockState(centre.above()).isAir || !level.getBlockState(centre.above(2)).isAir) return false
        val outside = player.level().dimension() != PlanetLabCoordinates.DIMENSION
        val returnPoint = ReturnPoint(player.level().dimension(), player.position(), player.yRot, player.xRot)
        val moved = player.teleportTo(level, centre.x + 0.5, centre.y + 1.0, centre.z + 0.5, emptySet(), 0f, 0f)
        if (moved && outside) returns[player.uuid] = returnPoint
        if (moved) {
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
        }
        return moved
    }

    fun leave(player: ServerPlayer): Boolean {
        if (player.level().dimension() != PlanetLabCoordinates.DIMENSION) return false
        val saved = returns[player.uuid]
        val destination = saved?.let { server.getLevel(it.dimension) } ?: server.overworld()
        val spawn = destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destination.sharedSpawnPos)
        val position = if (saved != null && destination.dimension() == saved.dimension) saved.position else Vec3.atBottomCenterOf(spawn)
        val moved = player.teleportTo(destination, position.x, position.y, position.z, emptySet(), saved?.yaw ?: 0f, saved?.pitch ?: 0f)
        if (moved) {
            returns.remove(player.uuid)
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
        }
        return moved
    }

    fun startOrbit() {
        val direction = Vector3(1.0, -1.0, 0.0).normalized()
        val tangent = Vector3(1.0, 1.0, 1.0).normalized().cross(direction).normalized()
        val speed = sqrt(gravity.mu * (2 / radius - 1 / semiMajorAxis))
        orbitState = CartesianState(direction * radius, tangent * speed)
        elapsedSeconds = 0.0
        ticks = 0
    }

    fun stopOrbit() {
        orbitState = null
        elapsedSeconds = 0.0
        ticks = 0
    }

    fun observe(player: ServerPlayer): Boolean {
        val level = level()
        val centre = PlanetLabCoordinates.observationFloor
        for (x in -3..3) {
            for (z in -3..3) {
                val pos = centre.offset(x, 0, z)
                if (level.getBlockState(pos).isAir) level.setBlock(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Block.UPDATE_CLIENTS)
            }
        }
        if (!level.getBlockState(centre.above()).isAir || !level.getBlockState(centre.above(2)).isAir) return false
        val moved = player.teleportTo(level, centre.x + 0.5, centre.y + 1.0, centre.z + 0.5, emptySet(), 0f, -35f)
        if (moved) {
            player.deltaMovement = Vec3.ZERO
            player.fallDistance = 0f
        }
        return moved
    }

    /** Four physics steps per 20 Hz server tick. Pauses when the server itself is paused. */
    fun tick() {
        var state = orbitState ?: return
        repeat(4) {
            val step = min(0.0125, period - elapsedSeconds)
            if (step > 0) {
                state = gravity.step(state, step)
                elapsedSeconds += step
            }
        }
        orbitState = state
        val level = level()
        val origin = PlanetLabCoordinates.orbitalOrigin

        fun particle(
            p: Vector3,
            body: Boolean,
        ) {
            val type = if (body) ParticleTypes.HAPPY_VILLAGER else ParticleTypes.END_ROD
            for (player in level.players()) {
                level.sendParticles(player, type, true, origin.x + p.x, origin.y + p.y, origin.z + p.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        particle(state.position, false)
        if (ticks++ % 20 == 0) {
            repeat(48) { index ->
                val angle = 2 * PI * index / 48
                val a = radius * cos(angle)
                val b = radius * sin(angle)
                particle(Vector3(a, b, 0.0), true)
                particle(Vector3(a, 0.0, b), true)
                particle(Vector3(0.0, a, b), true)
            }
        }
        if (elapsedSeconds >= period) orbitState = null
    }
}

/** Loader lifecycle delegates here; one independent runtime per server, discarded on shutdown. */
object PlanetLabServices {
    private val servers = mutableMapOf<MinecraftServer, PlanetLab>()

    fun forServer(server: MinecraftServer): PlanetLab = servers.getOrPut(server) { PlanetLab(server) }

    fun tick(server: MinecraftServer) {
        servers[server]?.tick()
    }

    fun forget(server: MinecraftServer) {
        servers.remove(server)
        CubePlanetLab.forget(server)
    }
}
