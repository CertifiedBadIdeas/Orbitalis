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
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** Coordinate-only boundary for native fluid algorithms; native blocks/ticks remain authoritative. */
object PlanetFluids {
    data class Frame(
        val physical: BlockPos,
        val east: Direction,
        val up: Direction,
        val south: Direction,
        val down: Direction,
    ) {
        fun toPhysical(d: Direction): Direction {
            val positive =
                when (d.axis) {
                    Direction.Axis.X -> east
                    Direction.Axis.Y -> up
                    Direction.Axis.Z -> south
                }
            return if (d.axisDirection == Direction.AxisDirection.POSITIVE) positive else positive.opposite
        }

        fun toLocal(d: Direction): Direction = Direction.entries.single { toPhysical(it) == d }

        fun sameAxes(other: Frame): Boolean = east == other.east && up == other.up && south == other.south
    }

    interface Adapter {
        fun frame(
            level: Level,
            position: BlockPos,
        ): Frame?

        fun fluidNeighbour(
            level: Level,
            position: BlockPos,
            direction: Direction,
        ): PlanetNeighbours.Edge?

        fun ready(
            level: Level,
            position: BlockPos,
            radius: Int,
        ): Boolean
    }

    var adapter: Adapter? = null

    /** Caught only by the scoped native fluid/interaction dispatch, which retries a cold tick. */
    class Unavailable : RuntimeException(null, null, false, false)

    @JvmStatic fun frame(
        world: BlockGetter,
        position: BlockPos,
    ): Frame? = if (world is Level && PlanetNeighbours.active(world)) adapter?.frame(world, position) else null

    @JvmStatic fun ready(
        world: Level,
        position: BlockPos,
        radius: Int,
    ): Boolean = adapter?.ready(world, position, radius) == true

    @JvmStatic fun down(
        world: BlockGetter,
        position: BlockPos,
    ): Direction = frame(world, position)?.down ?: Direction.DOWN

    @JvmStatic fun sides(
        world: BlockGetter,
        position: BlockPos,
    ): List<Direction> = Direction.entries.filter { it.axis != down(world, position).axis }

    @JvmStatic fun offset(
        world: BlockGetter,
        position: BlockPos,
        direction: Direction,
    ): BlockPos {
        if (world !is Level || !PlanetNeighbours.active(world)) return position.relative(direction)
        val edge = adapter?.fluidNeighbour(world, position, direction) ?: return position.relative(direction)
        if (!edge.ready) throw Unavailable()
        return edge.position
    }

    private data class Incoming(
        val world: BlockGetter,
        val target: BlockPos,
        val down: Direction,
    )

    private val incoming = ThreadLocal<Incoming?>()

    /** Direction-sensitive native callbacks keep the falling cell's basis across a gravity seam. */
    @JvmStatic fun <T> withIncomingDown(
        world: BlockGetter,
        source: BlockPos,
        target: BlockPos,
        action: java.util.function.Supplier<T>,
    ): T {
        val a = frame(world, source)
        val b = frame(world, target)
        if (a == null || b == null) return action.get()
        val previous = incoming.get()
        incoming.set(Incoming(world, target, b.toLocal(a.toPhysical(a.down))))
        try {
            return action.get()
        } finally {
            if (previous == null) incoming.remove() else incoming.set(previous)
        }
    }

    @JvmStatic fun incomingDown(
        world: BlockGetter,
        target: BlockPos,
    ): Direction {
        val context = incoming.get()
        return if (context != null && context.world === world && context.target == target) context.down else down(world, target)
    }

    @JvmStatic fun arrival(
        world: BlockGetter,
        source: BlockPos,
        target: BlockPos,
        fallback: Direction,
    ): Direction {
        val a = frame(world, source) ?: return fallback
        val b = frame(world, target) ?: return fallback
        val delta = b.physical.subtract(a.physical)
        val d = Direction.fromDelta(delta.x, delta.y, delta.z) ?: throw Unavailable()
        return b.toLocal(d)
    }

    @JvmStatic fun recursiveDirection(
        world: BlockGetter,
        source: BlockPos,
        target: BlockPos,
        d: Direction,
    ): Direction {
        val a = frame(world, source) ?: return d
        val b = frame(world, target) ?: return d
        return b.toLocal(a.toPhysical(d))
    }

    /** Native XZ cache keys alias chart coordinates and folded planes. Use bounded physical XYZ. */
    @JvmStatic fun cacheKey(
        world: BlockGetter,
        source: BlockPos,
        target: BlockPos,
        fallback: Short,
    ): Short {
        val a = frame(world, source) ?: return fallback
        val b = frame(world, target) ?: return fallback
        val d = b.physical.subtract(a.physical)
        if (d.x !in -16..15 || d.y !in -16..15 || d.z !in -16..15) throw Unavailable()
        return (((d.x and 31) shl 10) or ((d.y and 31) shl 5) or (d.z and 31)).toShort()
    }

    @JvmStatic fun wallDirection(
        world: BlockGetter,
        source: BlockPos,
        target: BlockPos,
        fallback: Direction,
    ): Direction {
        val a = frame(world, source) ?: return fallback
        val b = frame(world, target) ?: return fallback
        val d = b.physical.subtract(a.physical)
        return a.toLocal(Direction.fromDelta(d.x, d.y, d.z) ?: throw Unavailable())
    }

    @JvmStatic fun passWall(
        world: BlockGetter,
        source: BlockPos,
        a: BlockState,
        target: BlockPos,
        b: BlockState,
    ): Boolean? {
        val af = frame(world, source) ?: return null
        val bf = frame(world, target) ?: return null
        if (af.sameAxes(bf)) return null
        var transformed: VoxelShape = Shapes.empty()
        val mapping = listOf(Direction.EAST, Direction.UP, Direction.SOUTH).map { af.toLocal(bf.toPhysical(it)) }
        for (box in b.getCollisionShape(world, target).toAabbs()) {
            val corners =
                listOf(box.minX, box.maxX).flatMap { x ->
                    listOf(box.minY, box.maxY).flatMap { y ->
                        listOf(box.minZ, box.maxZ).map { z ->
                            val v = listOf(x - .5, y - .5, z - .5)
                            doubleArrayOf(
                                .5 + mapping.indices.sumOf { v[it] * mapping[it].stepX },
                                .5 + mapping.indices.sumOf { v[it] * mapping[it].stepY },
                                .5 + mapping.indices.sumOf { v[it] * mapping[it].stepZ },
                            )
                        }
                    }
                }
            transformed =
                Shapes.or(
                    transformed,
                    Shapes.box(
                        corners.minOf { it[0] },
                        corners.minOf { it[1] },
                        corners.minOf { it[2] },
                        corners.maxOf { it[0] },
                        corners.maxOf { it[1] },
                        corners.maxOf { it[2] },
                    ),
                )
        }
        return !Shapes.mergedFaceOccludes(
            a.getCollisionShape(world, source),
            transformed,
            wallDirection(world, source, target, Direction.DOWN),
        )
    }
}
