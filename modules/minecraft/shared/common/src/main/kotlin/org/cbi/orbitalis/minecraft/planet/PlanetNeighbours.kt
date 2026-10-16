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

package org.cbi.orbitalis.minecraft.planet

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import java.util.Collections
import java.util.WeakHashMap

/** Default planet-chart interactions with transient per-level overrides. No alternate block or inventory authority. */
object PlanetNeighbours {
    /** Direction is the source-to-neighbour vector expressed in the destination chart axes. */
    data class Edge(
        val position: BlockPos,
        val direction: Direction,
        val ready: Boolean,
    )

    data class Relation(
        val offset: BlockPos,
        val east: Direction,
        val up: Direction,
        val south: Direction,
    ) {
        fun toTarget(direction: Direction): Direction {
            val positive =
                when (direction.axis) {
                    Direction.Axis.X -> east
                    Direction.Axis.Y -> up
                    Direction.Axis.Z -> south
                }
            return if (direction.axisDirection == Direction.AxisDirection.POSITIVE) positive else positive.opposite
        }

        fun toSource(direction: Direction): Direction = Direction.entries.single { toTarget(it) == direction }
    }

    fun interface Adapter {
        fun neighbour(
            level: Level,
            source: BlockPos,
            direction: Direction,
        ): Edge?

        fun relation(
            level: Level,
            source: BlockPos,
            target: BlockPos,
        ): Relation? = null
    }

    var adapter: Adapter? = null
    private val overrides = Collections.synchronizedMap(WeakHashMap<Level, Boolean>())

    @JvmStatic fun enable(
        level: ServerLevel,
        value: Boolean,
    ) {
        check(level.server.isSameThread)
        overrides[level] = value
    }

    @JvmStatic fun reset(level: ServerLevel) {
        check(level.server.isSameThread)
        overrides.remove(level)
    }

    @JvmStatic fun active(level: Level): Boolean = overrides[level] ?: (!level.isClientSide && level.dimension() == PlanetLabCoordinates.DIMENSION)

    @JvmStatic fun neighbour(
        level: Level,
        source: BlockPos,
        direction: Direction,
    ): Edge? = if (active(level)) adapter?.neighbour(level, source, direction) else null

    @JvmStatic fun relation(
        level: Level,
        source: BlockPos,
        target: BlockPos,
    ): Relation? = if (active(level)) adapter?.relation(level, source, target) else null

    /** SignalGetter's direction points from the consumer towards the queried source. */
    @JvmStatic fun signal(
        level: Level,
        position: BlockPos,
        direction: Direction,
    ): Edge? = neighbour(level, position.relative(direction.opposite), direction)

    @JvmStatic fun notification(
        level: Level,
        position: BlockPos,
        source: BlockPos,
    ): Edge? {
        val delta = position.subtract(source)
        if (kotlin.math.abs(delta.x) + kotlin.math.abs(delta.y) + kotlin.math.abs(delta.z) != 1) return null
        val direction = Direction.fromDelta(delta.x, delta.y, delta.z) ?: return null
        return neighbour(level, source, direction)
    }
}
