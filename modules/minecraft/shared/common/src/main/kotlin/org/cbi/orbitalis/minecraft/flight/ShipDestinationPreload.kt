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
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.core.orbit.SurfaceFlightFrames
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.PlanetFlightEnvelope
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.cbi.orbitalis.minecraft.space.SpaceWorlds
import java.util.UUID
import java.util.WeakHashMap

/** Bounded native terrain ticket with a measured time budget and an immutable arrival forecast. */
class ShipDestinationPreload : AutoCloseable {
    private val token = UUID.randomUUID()

    private data class Target(
        val level: ServerLevel,
        val chunk: ChunkPos,
        val radius: Int,
        var touched: Long,
    )

    private var target: Target? = null
    private val recent = linkedMapOf<Pair<ChunkPos, Int>, Target>()
    private var started = 0L
    private var measured = false
    private var previousLevel: ServerLevel? = null
    private var previousTick = -1L
    private var previousPosition: Vector3? = null
    private var previousVelocity: Vector3? = null
    private var acceleration = Vector3.ZERO
    val sampledVelocity: Vector3? get() = previousVelocity
    val active: Boolean get() = target != null
    val ready: Boolean get() =
        target?.let { (level, chunk) ->
            target?.radius == 0 ||
                (level.chunkSource.getChunkNow(chunk.x, chunk.z) != null && level.areEntitiesLoaded(chunk.toLong()))
        } == true

    /** All positions are inertial geocentric root axes, velocities are m/s. Null velocity samples player movement. */
    fun approach(
        level: ServerLevel,
        physicalPosition: Vector3,
        velocity: Vector3? = null,
        allowNewTicket: Boolean = true,
    ): Boolean {
        if (!FlatPlanetSpace.contains(level)) {
            close()
            return false
        }
        val tick = level.gameTime
        val dt = if (previousLevel === level && previousTick >= 0L) (tick - previousTick) * 0.05 else 0.0
        val observed =
            velocity ?: if (dt > 0.0 && dt <= 1.0 && previousPosition != null) {
                (physicalPosition - checkNotNull(previousPosition)) * (1.0 / dt)
            } else {
                Vector3.ZERO
            }
        if (!physicalPosition.length().isFinite() || !observed.length().isFinite()) {
            close()
            return false
        }
        if (dt > 0.0 && dt <= 1.0 && previousVelocity != null) {
            val measuredAcceleration = (observed - checkNotNull(previousVelocity)) * (1.0 / dt)
            acceleration =
                acceleration * 0.5 + measuredAcceleration * (0.5 * minOf(1.0, 200.0 / measuredAcceleration.length().coerceAtLeast(1e-8)))
        } else if (dt != 0.0 || previousLevel !== level) {
            acceleration = Vector3.ZERO
        }
        previousLevel = level
        previousTick = tick
        previousPosition = physicalPosition
        previousVelocity = if (velocity != null || dt > 0.0) observed else null
        val sample = CelestialFlight.sample(level.server)
        // Interplanetary routes are predicted in physical time, including moving frame origins.
        val currentKey = level.dimension()
        val extended =
            OrbitalSectors.sector(currentKey) != null ||
                currentKey.location().toString() in setOf("orbitalis:mercury", "orbitalis:venus")
        for (step in 0..21) {
            val delay = step.toDouble()
            val future = CelestialFlight(SurfaceFlightFrames(sample.frames.profile, sample.frames.tick + step * 20L))
            val crossing =
                CartesianState(
                    physicalPosition + observed * delay + acceleration * (0.5 * delay * delay),
                    observed + acceleration * delay,
                )
            val native = future.nativeState(crossing, currentKey)
            val key = future.destination(level, native) ?: continue
            if (!extended && OrbitalSectors.sector(key) == null &&
                key.location().toString() !in setOf("orbitalis:mercury", "orbitalis:venus")
            ) {
                break
            }
            val destination = OrbitalSectors.ensure(level.server, key) ?: return false
            val lead = (budgets.getOrPut(destination, ::Budget).seconds * 2.0 + 1.0).coerceIn(2.0, 21.0)
            if (delay > lead || (!allowNewTicket && !active)) {
                close()
                return false
            }
            forecastDestination(destination, future.nativePoint(crossing.position, key), observed.length())
            return true
        }
        if (extended) {
            close()
            return false
        }
        val nativeMoon = sample.nativeState(CartesianState(physicalPosition, observed), MoonWorld.KEY)
        val fromMoon = level.dimension() == MoonWorld.KEY
        val lunarAcceleration = sample.dynamics.lunarAcceleration(nativeMoon)
        val moonEta =
            sample.frames.profile.geometry.moonEnvelope.arrival(
                nativeMoon.position,
                nativeMoon.velocity,
                lunarAcceleration,
                fromMoon,
                30.0,
            )
        val ascending = level.dimension() == net.minecraft.world.level.Level.OVERWORLD
        val nativeEarth = sample.nativeState(CartesianState(physicalPosition, observed), net.minecraft.world.level.Level.OVERWORLD)
        val earthAcceleration = sample.dynamics.earthAcceleration(nativeEarth)
        val earthEta =
            if (fromMoon) {
                null
            } else {
                sample.frames.profile.geometry.earthEnvelope.arrival(
                    nativeEarth.position,
                    nativeEarth.velocity,
                    earthAcceleration,
                    ascending,
                    30.0,
                )
            }
        if (fromMoon || (level.dimension() == FlatPlanetSpace.SPACE && moonEta != null && (earthEta == null || moonEta <= earthEta))) {
            val key = if (fromMoon) FlatPlanetSpace.SPACE else MoonWorld.KEY
            val destination = level.server.getLevel(key) ?: return false
            val lead = (budgets.getOrPut(destination, ::Budget).seconds * 2.0 + 1.0).coerceIn(2.0, 21.0)
            if (moonEta == null || moonEta > lead || (!allowNewTicket && !active)) {
                close()
                return false
            }
            val delay = moonEta
            val crossing = nativeMoon.position + nativeMoon.velocity * delay + lunarAcceleration * (delay * delay * 0.5)
            val future = CelestialFlight(SurfaceFlightFrames(sample.frames.profile, sample.frames.tick + (delay * 20.0).toLong()))
            forecastDestination(destination, if (fromMoon) future.point(crossing, MoonWorld.KEY, key) else crossing, observed.length())
            return true
        }
        val other = if (ascending) FlatPlanetSpace.SPACE else net.minecraft.world.level.Level.OVERWORLD
        val destination = level.server.getLevel(other) ?: return false
        val budget = budgets.getOrPut(destination, ::Budget)
        val lead = (budget.seconds * 2.0 + 1.0).coerceIn(2.0, 21.0)
        val eta = earthEta
        if (eta == null || eta > lead || (!allowNewTicket && !active)) {
            close()
            return false
        }
        val crossing = nativeEarth.position + nativeEarth.velocity * eta + earthAcceleration * (eta * eta * 0.5)
        val future = CelestialFlight(SurfaceFlightFrames(sample.frames.profile, sample.frames.tick + (eta * 20.0).toLong()))
        forecastDestination(
            destination,
            if (ascending) future.point(crossing, net.minecraft.world.level.Level.OVERWORLD, other) else crossing,
            observed.length(),
        )
        return true
    }

    private fun forecastDestination(
        level: ServerLevel,
        position: Vector3,
        speed: Double,
    ) {
        if (SpaceWorlds.objects(level)) {
            close()
            // Forecast readiness concerns fixed terrain only. Explicit update() leases are
            // still real entity/data loads, including recovery of world-space passengers.
            target = Target(level, ChunkPos(BlockPos.containing(position.x, position.y, position.z)), 0, level.gameTime)
        } else {
            update(level, position, speed)
        }
    }

    @JvmOverloads
    fun update(
        level: ServerLevel,
        position: Vector3,
        speed: Double = 0.0,
    ) {
        require(speed.isFinite() && speed >= 0.0)
        // Radius two entity-ticks only the centre and can strand a hull straddling a chunk
        // boundary. Cover the supported small hull plus a bounded high-speed ticking margin.
        val radius =
            4 +
                kotlin.math
                    .ceil((speed - 128.0).coerceAtLeast(0.0) * 0.2 / 16.0)
                    .coerceIn(0.0, 2.0)
                    .toInt()
        val chunk = ChunkPos(BlockPos.containing(position.x, position.y, position.z))
        if (target?.level !== level) close()
        if (target?.chunk != chunk || target?.radius != radius) {
            // Install the next centre before retiring old roots. A moving one-ticket lease
            // can repeatedly drop entity visibility while asynchronous chunk data is loading.
            level.chunkSource.addRegionTicket(TYPE, chunk, radius, token)
            recent.remove(chunk to radius)
            target?.let { previous ->
                previous.touched = level.gameTime
                recent[previous.chunk to previous.radius] = previous
            }
            target = Target(level, chunk, radius, level.gameTime)
            measured = false
            started = System.nanoTime()
        } else {
            level.chunkSource.addRegionTicket(TYPE, chunk, radius, token)
        }
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            val old = iterator.next().value
            if (recent.size <= 8 && level.gameTime - old.touched <= 20) break
            if (old.radius > 0) old.level.chunkSource.removeRegionTicket(TYPE, old.chunk, old.radius, token)
            iterator.remove()
        }
        if (!measured && ready) {
            val elapsed = ((System.nanoTime() - started) * 1e-9).coerceIn(0.25, 10.0)
            val budget = budgets.getOrPut(level, ::Budget)
            budget.seconds = budget.seconds * 0.75 + elapsed * 0.25
            measured = true
        }
    }

    override fun close() {
        target?.let { (level, chunk, radius) -> if (radius > 0) level.chunkSource.removeRegionTicket(TYPE, chunk, radius, token) }
        for (old in recent.values) if (old.radius > 0) old.level.chunkSource.removeRegionTicket(TYPE, old.chunk, old.radius, token)
        recent.clear()
        target = null
        measured = false
    }

    private class Budget(
        var seconds: Double = 2.0,
    )

    companion object {
        private val budgets = WeakHashMap<ServerLevel, Budget>()
        private val TYPE: TicketType<UUID> = TicketType.create("orbitalis_ship_preload", Comparator.naturalOrder<UUID>(), 200)
    }
}
