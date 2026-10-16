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
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

/** Integer signed-permutation addressing for fixed planet charts; never loads a native chunk. */
object SablePlanetNeighbours : PlanetNeighbours.Adapter {
    private val small = CubicPlanetVolume(16)

    override fun neighbour(
        level: Level,
        source: BlockPos,
        direction: Direction,
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
        // Do not alias a non-owning chart's air inside the solid volume.
        if (volume.contains(sourceCell) && volume.owner(sourceCell) != face) return null
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
            val ready = destination.plot.getChunkHolder(destination.plot.toLocal(ChunkPos(p))) != null
            return PlanetNeighbours.Edge(p, d, ready)
        }
        if (volume.contains(target)) {
            val owner = volume.owner(target)
            if (owner == face) return null
            val destination = charts.singleOrNull { face(it) == owner } ?: return blocked()
            return address(destination)
        }
        // Authored blocks outside the reference cube: require unique, resident physical occupancy.
        // Unknown foreign columns and overlapping authored blocks fail closed; no guessed air.
        if (charts.size != 6) return blocked()
        val candidates = charts.map(::address)
        if (candidates.any { !it.ready }) return blocked()
        val occupied = candidates.filter { !level.getBlockState(it.position).isAir }
        if (occupied.size > 1) return blocked()
        val edge = occupied.singleOrNull() ?: return null
        return edge.takeUnless { it.position == source.relative(direction) }
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
