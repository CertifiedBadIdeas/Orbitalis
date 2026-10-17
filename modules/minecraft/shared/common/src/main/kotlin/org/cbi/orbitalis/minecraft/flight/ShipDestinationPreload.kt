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
import org.cbi.orbitalis.core.planet.AltitudeArrival
import org.cbi.orbitalis.core.planet.Vector3
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.abs

/** Bounded native terrain ticket with a measured time budget and an immutable arrival forecast. */
class ShipDestinationPreload : AutoCloseable {
    private val token = UUID.randomUUID()
    private var target: Pair<ServerLevel, ChunkPos>? = null
    private var started = 0L
    private var measured = false
    private var previousLevel: ServerLevel? = null
    private var previousTick = -1L
    private var previousPosition: Vector3? = null
    private var previousVelocity: Vector3? = null
    private var acceleration = 0.0
    val sampledVelocity: Vector3? get() = previousVelocity
    val active: Boolean get() = target != null
    val ready: Boolean get() =
        target?.let { (level, chunk) ->
            level.chunkSource.getChunkNow(chunk.x, chunk.z) != null && level.areEntitiesLoaded(chunk.toLong())
        } == true

    /** All positions are physical Overworld axes, velocities are m/s. Null velocity samples player movement. */
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
        if (dt > 0.0 && dt <= 1.0 && previousVelocity != null) {
            acceleration = acceleration * 0.5 + ((observed.y - checkNotNull(previousVelocity).y) / dt).coerceIn(-200.0, 200.0) * 0.5
        } else if (dt != 0.0 || previousLevel !== level) {
            acceleration = 0.0
        }
        previousLevel = level
        previousTick = tick
        previousPosition = physicalPosition
        previousVelocity = if (velocity != null || dt > 0.0) observed else null
        val ascending = level.dimension() == net.minecraft.world.level.Level.OVERWORLD
        val other = if (ascending) FlatPlanetSpace.SPACE else net.minecraft.world.level.Level.OVERWORLD
        val destination = level.server.getLevel(other) ?: return false
        val budget = budgets.getOrPut(destination, ::Budget)
        val lead = (budget.seconds * 2.0 + 1.0).coerceIn(2.0, 21.0)
        val boundary = FlatPlanetSpace.SEA_LEVEL + if (ascending) FlatPlanetSpace.ASCENT_HEIGHT else FlatPlanetSpace.DESCENT_HEIGHT
        val eta = AltitudeArrival.seconds(physicalPosition.y, observed.y, acceleration, boundary, ascending, 30.0)
        if (eta == null || eta > lead || (!allowNewTicket && !active)) {
            close()
            return false
        }
        val crossing = physicalPosition + observed * eta + Vector3(0.0, acceleration * eta * eta * 0.5, 0.0)
        if (abs(crossing.x) >= FlatPlanetSpace.HALF_SIZE || abs(crossing.z) >= FlatPlanetSpace.HALF_SIZE ||
            (!ascending && crossing.y < FlatPlanetSpace.SEA_LEVEL)
        ) {
            close()
            return false
        }
        update(destination, FlightSpaceCoordinates.localPosition(other, crossing))
        return true
    }

    fun update(
        level: ServerLevel,
        position: Vector3,
    ) {
        val chunk = ChunkPos(BlockPos.containing(position.x, position.y, position.z))
        if (target?.first !== level || target?.second != chunk) {
            close()
            target = level to chunk
            started = System.nanoTime()
        }
        level.chunkSource.addRegionTicket(TYPE, chunk, 2, token)
        if (!measured && ready) {
            val elapsed = ((System.nanoTime() - started) * 1e-9).coerceIn(0.25, 10.0)
            val budget = budgets.getOrPut(level, ::Budget)
            budget.seconds = budget.seconds * 0.75 + elapsed * 0.25
            measured = true
        }
    }

    override fun close() {
        target?.let { (level, chunk) -> level.chunkSource.removeRegionTicket(TYPE, chunk, 2, token) }
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
