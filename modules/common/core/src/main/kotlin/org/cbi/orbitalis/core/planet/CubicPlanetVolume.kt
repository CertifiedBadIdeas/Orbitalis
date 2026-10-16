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

package org.cbi.orbitalis.core.planet

import kotlin.math.abs
import kotlin.math.floor

/** Native chart axes and deterministic tie order; separate from the spherical chart convention. */
enum class CubicFace(
    val right: Vector3,
    val outward: Vector3,
    val forward: Vector3,
) {
    UP(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)),
    EAST(Vector3(0.0, -1.0, 0.0), Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0)),
    SOUTH(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0), Vector3(0.0, -1.0, 0.0)),
    WEST(Vector3(0.0, 1.0, 0.0), Vector3(-1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0)),
    DOWN(Vector3(1.0, 0.0, 0.0), Vector3(0.0, -1.0, 0.0), Vector3(0.0, 0.0, -1.0)),
    NORTH(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, -1.0), Vector3(0.0, 1.0, 0.0)),
}

data class PlanetCell(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun centre(): Vector3 = Vector3(x + 0.5, y + 0.5, z + 0.5)
}

data class PlanetChunk(
    val face: CubicFace,
    val x: Int,
    val z: Int,
)

/** One solid finite volume, with native +Y-outward coordinates on each of six faces. */
class CubicPlanetVolume(
    val halfExtent: Int = 256,
    val chunkOriginOffset: Int = 0,
) {
    init {
        require(halfExtent in 1..512)
        require(chunkOriginOffset in 0..15)
    }

    fun contains(cell: PlanetCell): Boolean =
        cell.x in -halfExtent until halfExtent && cell.y in -halfExtent until halfExtent && cell.z in -halfExtent until halfExtent

    fun owner(cell: PlanetCell): CubicFace {
        require(contains(cell))
        val p = cell.centre()
        return CubicFace.entries.maxBy { p.dot(it.outward) }
    }

    fun toLocal(
        face: CubicFace,
        position: Vector3,
    ): Vector3 = Vector3(position.dot(face.right), position.dot(face.outward) - halfExtent, position.dot(face.forward))

    fun toPlanet(
        face: CubicFace,
        position: Vector3,
    ): Vector3 = face.right * position.x + face.outward * (position.y + halfExtent) + face.forward * position.z

    fun toLocal(
        face: CubicFace,
        cell: PlanetCell,
    ): PlanetCell = cellAt(toLocal(face, cell.centre()))

    fun toPlanet(
        face: CubicFace,
        cell: PlanetCell,
    ): PlanetCell = cellAt(toPlanet(face, cell.centre()))

    fun owns(
        face: CubicFace,
        local: PlanetCell,
    ): Boolean {
        val cell = toPlanet(face, local)
        return contains(cell) && owner(cell) == face
    }

    /** Conservative world-box interest, including all owners at edges, corners and the centre.
     * Negative coordinates use floor division, and padding includes neighbour queries across chunks.
     */
    fun chunksNear(
        position: Vector3,
        radius: Double,
    ): Set<PlanetChunk> {
        require(radius.isFinite() && radius in 0.0..128.0)
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite())
        val extent = halfExtent.toDouble()
        if (maxOf(abs(position.x), abs(position.y), abs(position.z)) > extent + radius) return emptySet()
        val result = linkedSetOf<PlanetChunk>()
        for (face in CubicFace.entries) {
            val p = toLocal(face, position)
            val minX = maxOf(-halfExtent, floor(p.x - radius).toInt())
            val maxX = minOf(halfExtent - 1, floor(p.x + radius).toInt())
            val minZ = maxOf(-halfExtent, floor(p.z - radius).toInt())
            val maxZ = minOf(halfExtent - 1, floor(p.z + radius).toInt())
            if (minX > maxX || minZ > maxZ) continue
            for (x in Math.floorDiv(minX + chunkOriginOffset, 16)..Math.floorDiv(maxX + chunkOriginOffset, 16)) {
                for (z in Math.floorDiv(minZ + chunkOriginOffset, 16)..Math.floorDiv(maxZ + chunkOriginOffset, 16)) {
                    fun nearest(
                        lo: Int,
                        hi: Int,
                    ): Double =
                        when {
                            lo >= 0 -> lo + 0.5
                            hi < 0 -> -hi - 0.5
                            else -> 0.5
                        }
                    val lowX = maxOf(minX, x * 16 - chunkOriginOffset)
                    val highX = minOf(maxX, x * 16 + 15 - chunkOriginOffset)
                    val lowZ = maxOf(minZ, z * 16 - chunkOriginOffset)
                    val highZ = minOf(maxZ, z * 16 + 15 - chunkOriginOffset)
                    val firstDepth = maxOf(nearest(lowX, highX), nearest(lowZ, highZ)) - extent
                    if (p.y + radius >= firstDepth - 0.5 && p.y - radius <= 0.0) result.add(PlanetChunk(face, x, z))
                }
            }
        }
        return result
    }

    private fun cellAt(p: Vector3): PlanetCell = PlanetCell(floor(p.x).toInt(), floor(p.y).toInt(), floor(p.z).toInt())
}
