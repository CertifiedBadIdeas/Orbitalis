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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.CubicFace
import org.cbi.orbitalis.core.planet.CubicPlanetVolume
import org.cbi.orbitalis.core.planet.PlanetCell
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetFluids
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

/** Integer signed-permutation addressing for fixed planet charts; never loads a native chunk. */
object SablePlanetNeighbours : PlanetNeighbours.Adapter, PlanetFluids.Adapter {
    private val small = CubicPlanetVolume(16)

    override fun neighbour(
        level: Level,
        source: BlockPos,
        direction: Direction,
    ): PlanetNeighbours.Edge? = resolve(level, source, direction, false)

    override fun fluidNeighbour(
        level: Level,
        position: BlockPos,
        direction: Direction,
    ): PlanetNeighbours.Edge? = resolve(level, position, direction, true)

    private fun resolve(
        level: Level,
        source: BlockPos,
        direction: Direction,
        outside: Boolean,
    ): PlanetNeighbours.Edge? {
        if (level.dimension() != PlanetLabCoordinates.DIMENSION || level.isClientSide) return null
        val container = SubLevelContainer.getContainer(level) ?: return null
        val plot = container.getPlot(ChunkPos(source)) ?: return null
        val body = plot.subLevel as? ServerSubLevel ?: return null
        if (!participates(body)) return null
        val face = face(body) ?: return null
        val volume = small
        val origin = plot.centerBlock
        val sourceCell = volume.toPlanet(face, PlanetCell(source.x - origin.x, source.y - origin.y, source.z - origin.z))
        val step =
            face.right * direction.stepX.toDouble() + face.outward * direction.stepY.toDouble() + face.forward * direction.stepZ.toDouble()
        val target = PlanetCell(sourceCell.x + step.x.toInt(), sourceCell.y + step.y.toInt(), sourceCell.z + step.z.toInt())
        val charts = container.allSubLevels.filterIsInstance<ServerSubLevel>().filter(::participates)

        fun blocked() = PlanetNeighbours.Edge(source.relative(direction), direction, false)

        fun address(destination: ServerSubLevel): PlanetNeighbours.Edge {
            val destinationFace = checkNotNull(face(destination))
            val local = volume.toLocal(destinationFace, target)
            val p = destination.plot.centerBlock.offset(local.x, local.y, local.z)
            val d =
                Direction.fromDelta(
                    step.dot(destinationFace.right).toInt(),
                    step.dot(destinationFace.outward).toInt(),
                    step.dot(destinationFace.forward).toInt(),
                )!!
            val ready = !SablePlanetChunks.isCold(destination, destination.plot.toLocal(ChunkPos(p)))
            return PlanetNeighbours.Edge(p, d, ready)
        }
        // A fully loaded plot's absent column is native empty space. Only an explicitly evicted
        // column has unknown blocks. Do not allocate empty columns just to prove uniqueness.
        // Storage provenance is independent of geometric ownership: authored blocks can extend
        // from any chart deep into another face. Resolve actual occupancy before canonical air.
        if (charts.size != 6 || charts.mapNotNull(::face).toSet().size != 6) return blocked()
        val candidates = charts.map(::address)
        if (candidates.any { !it.ready }) return blocked()
        val occupied =
            candidates.filter { candidate ->
                val destination = container.getPlot(ChunkPos(candidate.position)) ?: return@filter false
                destination.getChunkHolder(destination.toLocal(ChunkPos(candidate.position))) != null &&
                    !level.getBlockState(candidate.position).isAir
            }
        if (occupied.size > 1) return blocked()
        val edge =
            occupied.singleOrNull()
                ?: if (volume.contains(target)) {
                    address(charts.single { face(it) == volume.owner(target) })
                } else if (outside) {
                    address(
                        charts.single {
                            face(it) ==
                                geometricFace(target)
                        },
                    )
                } else {
                    return null
                }
        return edge.takeUnless { it.position == source.relative(direction) }
    }

    private fun geometricFace(cell: PlanetCell): CubicFace = CubicFace.entries.maxBy { cell.centre().dot(it.outward) }

    override fun frame(
        level: Level,
        position: BlockPos,
    ): PlanetFluids.Frame? {
        if (level.dimension() != PlanetLabCoordinates.DIMENSION || level.isClientSide) return null
        val body = SubLevelContainer.getContainer(level)?.getPlot(ChunkPos(position))?.subLevel as? ServerSubLevel ?: return null
        if (!participates(body)) return null
        val axes = checkNotNull(face(body))
        val origin = body.plot.centerBlock
        val cell = small.toPlanet(axes, PlanetCell(position.x - origin.x, position.y - origin.y, position.z - origin.z))

        fun cardinal(v: org.cbi.orbitalis.core.planet.Vector3): Direction =
            checkNotNull(Direction.fromDelta(v.x.toInt(), v.y.toInt(), v.z.toInt()))
        val physicalDown = geometricFace(cell).outward * -1.0
        val down =
            checkNotNull(
                Direction.fromDelta(
                    physicalDown.dot(axes.right).toInt(),
                    physicalDown.dot(axes.outward).toInt(),
                    physicalDown.dot(axes.forward).toInt(),
                ),
            )
        return PlanetFluids.Frame(
            BlockPos(cell.x, cell.y, cell.z),
            cardinal(axes.right),
            cardinal(axes.outward),
            cardinal(axes.forward),
            down,
        )
    }

    override fun ready(
        level: Level,
        position: BlockPos,
        radius: Int,
    ): Boolean {
        val f = frame(level, position) ?: return false
        if (radius !in 1..15) return false
        val container = SubLevelContainer.getContainer(level) ?: return false
        val charts = container.allSubLevels.filterIsInstance<ServerSubLevel>().filter(::participates)
        if (charts.size != 6 || charts.mapNotNull(::face).toSet().size != 6) return false
        for (body in charts) {
            val projected =
                listOf(-radius, radius).flatMap { x ->
                    listOf(-radius, radius).flatMap { y ->
                        listOf(-radius, radius).map { z ->
                            nativePosition(body, PlanetCell(f.physical.x + x, f.physical.y + y, f.physical.z + z))
                        }
                    }
                }
            for (x in (projected.minOf { it.x } shr 4)..(projected.maxOf { it.x } shr 4)) {
                for (z in (projected.minOf { it.z } shr 4)..(projected.maxOf { it.z } shr 4)) {
                    if (SablePlanetChunks.isCold(body, body.plot.toLocal(ChunkPos(x, z)))) return false
                }
            }
        }
        return true
    }

    override fun relation(
        level: Level,
        source: BlockPos,
        target: BlockPos,
    ): PlanetNeighbours.Relation? {
        if (level.dimension() != PlanetLabCoordinates.DIMENSION || level.isClientSide) return null
        val container = SubLevelContainer.getContainer(level) ?: return null
        val a = container.getPlot(ChunkPos(source))?.subLevel as? ServerSubLevel ?: return null
        val b = container.getPlot(ChunkPos(target))?.subLevel as? ServerSubLevel ?: return null
        if (a === b || !participates(a) || !participates(b)) return null
        val af = checkNotNull(face(a))
        val bf = checkNotNull(face(b))

        fun physical(
            body: ServerSubLevel,
            p: BlockPos,
        ): PlanetCell {
            val origin = body.plot.centerBlock
            return small.toPlanet(checkNotNull(face(body)), PlanetCell(p.x - origin.x, p.y - origin.y, p.z - origin.z))
        }
        val pa = physical(a, source)
        val pb = physical(b, target)
        val delta =
            org.cbi.orbitalis.core.planet
                .Vector3((pb.x - pa.x).toDouble(), (pb.y - pa.y).toDouble(), (pb.z - pa.z).toDouble())

        fun mapped(v: org.cbi.orbitalis.core.planet.Vector3) =
            Direction.fromDelta(v.dot(bf.right).toInt(), v.dot(bf.outward).toInt(), v.dot(bf.forward).toInt())!!
        return PlanetNeighbours.Relation(
            BlockPos(delta.dot(af.right).toInt(), delta.dot(af.outward).toInt(), delta.dot(af.forward).toInt()),
            mapped(af.right),
            mapped(af.outward),
            mapped(af.forward),
        )
    }

    private fun participates(body: ServerSubLevel): Boolean {
        if (!SableCubePlanet.isFrame(body)) return false
        val direction = SableCubePlanet.direction(body) ?: return false
        if (body.level.dimension() != PlanetLabCoordinates.DIMENSION) return false
        val expected = PlanetLabCoordinates.CENTRE.add(SableCubePlanet.normal(direction).scale(16.0))
        val pose = body.logicalPose()
        val pivot = body.plot.centerBlock
        return pose.position().distance(expected.x, expected.y, expected.z) < 1e-6 &&
            pose.rotationPoint().distance(pivot.x.toDouble(), pivot.y.toDouble(), pivot.z.toDouble()) < 1e-6 &&
            kotlin.math.abs(pose.orientation().dot(SableCubePlanet.rotation(direction))) > 1.0 - 1e-12
    }

    fun face(body: ServerSubLevel): CubicFace? = SableCubePlanet.direction(body)?.let { direction -> CubicFace.valueOf(direction.name) }

    fun nativePosition(
        body: ServerSubLevel,
        cell: PlanetCell,
    ): BlockPos {
        require(body.level.dimension() == PlanetLabCoordinates.DIMENSION)
        val volume = small
        val local = volume.toLocal(checkNotNull(face(body)), cell)
        return body.plot.centerBlock.offset(local.x, local.y, local.z)
    }
}
