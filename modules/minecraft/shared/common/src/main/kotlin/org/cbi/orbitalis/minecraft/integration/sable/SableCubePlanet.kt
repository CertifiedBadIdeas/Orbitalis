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

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.CubicPlanetFixture
import org.cbi.orbitalis.minecraft.lab.CubePlanetTerrain
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.floor

/** Six native block-coordinate charts. Sable owns block, entity and inventory persistence. */
class SableCubePlanet(
    val level: ServerLevel,
) : CubePlanetTerrain {
    private val container = checkNotNull(SubLevelContainer.getContainer(level))
    val bodies: List<ServerSubLevel>
        get() = faces.mapNotNull { face -> container.allSubLevels.singleOrNull { direction(it) == face } }

    fun body(face: Direction): ServerSubLevel = bodies.single { direction(it) == face }

    override fun prepare(): Int {
        val existing = bodies
        if (existing.isNotEmpty()) {
            check(existing.size == 6) { "Incomplete local-frame cube; restore the save or explicitly rebuild the disposable fixture." }
            restoreAnchors(level)
            return 0
        }
        val c = PlanetLabCoordinates.CENTRE
        for (x in -20..19) {
            for (y in -20..19) {
                for (z in -20..19) {
                    check(level.getBlockState(BlockPos.containing(c.add(x.toDouble(), y.toDouble(), z.toDouble()))).isAir) {
                        "Cube site contains old terrain or builds. Leave the cube and use /orbitalis lab cube rebuild to replace the disposable fixture."
                    }
                }
            }
        }
        check(
            !container.queryIntersecting(BoundingBox3d(c.x - 21, c.y - 21, c.z - 21, c.x + 21, c.y + 21, c.z + 21)).iterator().hasNext(),
        ) {
            "A construction occupies the cube site"
        }
        val cells = faces.associateWith { mutableListOf<BlockPos>() }
        for (x in -20..19) {
            for (y in -20..19) {
                for (z in -20..19) {
                    if (!CubicPlanetFixture.solid(x, y, z)) continue
                    val v = Vec3(x + .5, y + .5, z + .5)
                    val face = faces.maxBy { v.dot(normal(it)) }
                    val local =
                        rotation(
                            face,
                        ).transformInverse(Vector3d(v.x, v.y, v.z).sub(Vector3d(normal(face).x, normal(face).y, normal(face).z).mul(16.0)))
                    cells.getValue(face).add(BlockPos(floor(local.x).toInt(), floor(local.y).toInt(), floor(local.z).toInt()))
                }
            }
        }
        val created = mutableListOf<ServerSubLevel>()
        try {
            for (face in faces) created.add(makeFace(face, cells.getValue(face)))
        } catch (error: RuntimeException) {
            created.forEach(::remove)
            throw error
        }
        restoreAnchors(level)
        return cells.values.sumOf { it.size }
    }

    private fun makeFace(
        face: Direction,
        cells: List<BlockPos>,
    ): ServerSubLevel {
        val origin = origin(face)
        val q = rotation(face)
        val pose = Pose3d()
        pose.position().set(origin.x, origin.y, origin.z)
        pose.orientation().set(q)
        val body = container.allocateNewSubLevel(pose) as ServerSubLevel
        try {
            body.name = "Orbitalis cube " + face.serializedName
            body.userDataTag = CompoundTag()
            body.userDataTag!!.putInt("orbitalis_cube_frame_version", 1)
            body.userDataTag!!.putString("orbitalis_cube_face", face.serializedName)
            val centre = body.plot.centerBlock
            body.logicalPose().rotationPoint().set(centre.x.toDouble(), centre.y.toDouble(), centre.z.toDouble())
            cells.map { ChunkPos(centre.offset(it)) }.toSet().forEach { body.plot.newEmptyChunk(it) }
            val material =
                when (face) {
                    Direction.UP -> Blocks.GREEN_CONCRETE
                    Direction.DOWN -> Blocks.PURPLE_CONCRETE
                    Direction.EAST -> Blocks.ORANGE_CONCRETE
                    Direction.WEST -> Blocks.RED_CONCRETE
                    Direction.SOUTH -> Blocks.BLUE_CONCRETE
                    Direction.NORTH -> Blocks.YELLOW_CONCRETE
                }
            for (p in cells) check(body.plot.embeddedLevelAccessor.setBlock(p, material.defaultBlockState(), 3))
            // Filling changes native COM. Arrange once; the native joint owns stationarity afterwards.
            body.updateMergedMassData(1f)
            val com = checkNotNull(body.massTracker.centerOfMass)
            val offset = Vector3d(com).sub(centre.x.toDouble(), centre.y.toDouble(), centre.z.toDouble())
            q.transform(offset).add(origin.x, origin.y, origin.z)
            body.logicalPose().rotationPoint().set(com)
            body.logicalPose().position().set(offset)
            body.logicalPose().orientation().set(q)
            SubLevelPhysicsSystem.require(level).getPhysicsHandle(body).teleport(offset, q)
            body.updateLastPose()
            body.forceUpdateGlobalBounds()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            anchor(body)
            return body
        } catch (error: RuntimeException) {
            remove(body)
            throw error
        }
    }

    fun world(
        face: Direction,
        x: Double,
        y: Double,
        z: Double,
    ): Vec3 {
        val body = body(face)
        val c = body.plot.centerBlock
        val p = body.logicalPose().transformPosition(Vector3d(c.x + x, c.y + y, c.z + z), Vector3d())
        return Vec3(p.x, p.y, p.z)
    }

    override fun clearForEntry(bounds: net.minecraft.world.phys.AABB): Boolean {
        // Entry may run in the same server tick as a block edit. Refresh the native query cache.
        bodies.forEach {
            it.updateBoundingBox()
            it.forceUpdateGlobalBounds()
        }
        return SableCubePlayerContacts.clear(level, bounds)
    }

    override fun clear() {
        bodies.forEach(::remove)
    }

    private fun remove(body: ServerSubLevel) {
        exclusions.keys.filter { body.uniqueId in it }.toList().forEach { key ->
            exclusions.remove(key)?.let { if (it.isValid) it.remove() }
        }
        anchors.remove(body.uniqueId)?.let { if (it.isValid) it.remove() }
        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        if (!body.isRemoved) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
    }

    companion object {
        val faces = listOf(Direction.UP, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN, Direction.NORTH)
        private val exclusions = mutableMapOf<Set<UUID>, PhysicsConstraintHandle>()
        private val anchors = mutableMapOf<UUID, PhysicsConstraintHandle>()

        fun normal(face: Direction): Vec3 = Vec3.atLowerCornerOf(face.normal)

        fun origin(face: Direction): Vec3 = PlanetLabCoordinates.CENTRE.add(normal(face).scale(16.0))

        // JOML 1.10.5 rotationX stores its cosine in z; explicit axis-angle avoids that helper.
        fun rotation(face: Direction): Quaterniond =
            when (face) {
                Direction.UP -> Quaterniond()
                Direction.DOWN -> Quaterniond().rotationAxis(Math.PI, 1.0, 0.0, 0.0)
                Direction.EAST -> Quaterniond().rotationZ(-Math.PI / 2)
                Direction.WEST -> Quaterniond().rotationZ(Math.PI / 2)
                Direction.SOUTH -> Quaterniond().rotationAxis(Math.PI / 2, 1.0, 0.0, 0.0)
                Direction.NORTH -> Quaterniond().rotationAxis(-Math.PI / 2, 1.0, 0.0, 0.0)
            }

        fun direction(body: ServerSubLevel): Direction? {
            val data = body.userDataTag ?: return null
            if (data.getInt("orbitalis_cube_frame_version") != 1) return null
            return Direction.byName(data.getString("orbitalis_cube_face"))
        }

        fun isFrame(body: ServerSubLevel): Boolean = direction(body) != null && body.level.dimension() == PlanetLabCoordinates.DIMENSION

        /** Force-load tickets and chart tags persist natively. Rebind before the first physics step. */
        fun restoreAnchors(level: ServerLevel) {
            if (level.dimension() != PlanetLabCoordinates.DIMENSION) return
            val frames = SubLevelContainer.getContainer(level)?.allSubLevels?.filter(::isFrame) ?: return
            frames.forEach(::anchor)
            // Native collision margins at coincident voxel boundaries must not push charts apart.
            // Unlocked joints exclude only chart/chart contact; ships still collide.
            for (i in frames.indices) {
                for (j in i + 1 until frames.size) {
                    val a = frames[i]
                    val b = frames[j]
                    val key = setOf(a.uniqueId, b.uniqueId)
                    if (exclusions[key]?.isValid == true) continue
                    val joint =
                        SubLevelPhysicsSystem.require(level).pipeline.addConstraint(
                            a,
                            b,
                            GenericConstraintConfiguration(
                                Vector3d(
                                    a.plot.centerBlock.x
                                        .toDouble(),
                                    a.plot.centerBlock.y
                                        .toDouble(),
                                    a.plot.centerBlock.z
                                        .toDouble(),
                                ),
                                Vector3d(
                                    b.plot.centerBlock.x
                                        .toDouble(),
                                    b.plot.centerBlock.y
                                        .toDouble(),
                                    b.plot.centerBlock.z
                                        .toDouble(),
                                ),
                                Quaterniond(),
                                Quaterniond(),
                                emptySet(),
                            ),
                        )
                    checkNotNull(joint).setContactsEnabled(false)
                    exclusions[key] = joint
                }
            }
        }

        private fun anchor(body: ServerSubLevel) {
            if (anchors[body.uniqueId]?.isValid == true) return
            val face = checkNotNull(direction(body))
            val c = body.plot.centerBlock
            val o = origin(face)
            val joint =
                SubLevelPhysicsSystem.require(body.level).pipeline.addConstraint(
                    null,
                    body,
                    GenericConstraintConfiguration(
                        Vector3d(o.x, o.y, o.z),
                        Vector3d(c.x.toDouble(), c.y.toDouble(), c.z.toDouble()),
                        rotation(face),
                        Quaterniond(),
                        ConstraintJointAxis.entries.toSet(),
                    ),
                )
            checkNotNull(joint) { "Native physics does not support anchored planet charts" }.setContactsEnabled(false)
            anchors[body.uniqueId] = joint
        }

        fun forget() {
            exclusions.clear()
            anchors.clear()
        }
    }
}
