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

import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle
import dev.ryanhcode.sable.api.physics.mass.MassTracker
import dev.ryanhcode.sable.api.sublevel.KinematicContraption
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.material.FluidState
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc

/** Planet plots retain native block coordinates and presentation, never dynamic rigid bodies.
 * Only their resident collision geometry is registered with the existing kinematic terrain path.
 */
class SablePlanetCollisionPipeline(
    private val level: ServerLevel,
    private val delegate: PhysicsPipeline,
) : PhysicsPipeline by delegate {
    private val frames = mutableSetOf<ServerSubLevel>()
    private val attachedContraptions = java.util.IdentityHashMap<KinematicContraption, KinematicContraption>()
    private val dirty = linkedSetOf<ChunkPos>()
    private val colliders = mutableMapOf<ChunkPos, TerrainChunk>()

    override fun add(
        body: ServerSubLevel,
        pose: Pose3dc,
    ) {
        if (isStatic(body)) frames.add(body) else delegate.add(body, pose)
    }

    override fun remove(body: ServerSubLevel) {
        if (frames.remove(body)) {
            colliders.entries.filter { it.value.body === body }.forEach { (key, value) ->
                delegate.remove(value)
                colliders.remove(key)
            }
            dirty.removeIf { SubLevelContainer.getContainer(level)?.getPlot(it)?.subLevel === body }
        } else {
            delegate.remove(body)
        }
    }

    override fun add(contraption: KinematicContraption) {
        val parent =
            dev.ryanhcode.sable.Sable.HELPER
                .getContaining(level, contraption.`sable$getPosition`()) as? ServerSubLevel
        val world = if (parent != null && isStatic(parent)) WorldContraption(parent, contraption) else contraption
        delegate.add(world)
        attachedContraptions[contraption] = world
    }

    override fun remove(contraption: KinematicContraption) {
        delegate.remove(attachedContraptions.remove(contraption) ?: contraption)
    }

    private class WorldContraption(
        private val parent: ServerSubLevel,
        private val source: KinematicContraption,
    ) : KinematicContraption by source {
        override fun `sable$getPosition`(partial: Double): Vector3dc =
            parent.logicalPose().transformPosition(Vector3d(source.`sable$getPosition`(partial)), Vector3d())

        override fun `sable$getPosition`(): Vector3dc = `sable$getPosition`(1.0)

        override fun `sable$getOrientation`(partial: Double): Quaterniond =
            Quaterniond(parent.logicalPose().orientation()).mul(source.`sable$getOrientation`(partial))

        override fun `sable$getOrientation`(): Quaterniond = `sable$getOrientation`(1.0)
    }

    override fun readPose(
        body: ServerSubLevel,
        pose: Pose3d,
    ): Pose3d = if (isStatic(body)) pose.set(body.logicalPose()) else delegate.readPose(body, pose)

    override fun handleChunkSectionAddition(
        section: LevelChunkSection,
        x: Int,
        y: Int,
        z: Int,
        force: Boolean,
    ) {
        if (isPlanetChunk(x, z)) dirty.add(ChunkPos(x, z)) else delegate.handleChunkSectionAddition(section, x, y, z, force)
    }

    override fun handleChunkSectionRemoval(
        x: Int,
        y: Int,
        z: Int,
    ) {
        if (isPlanetChunk(x, z)) dirty.add(ChunkPos(x, z)) else delegate.handleChunkSectionRemoval(x, y, z)
    }

    override fun handleBlockChange(
        pos: SectionPos,
        section: LevelChunkSection,
        x: Int,
        y: Int,
        z: Int,
        old: BlockState,
        next: BlockState,
    ) {
        if (isPlanetChunk(
                pos.x(),
                pos.z(),
            )
        ) {
            dirty.add(ChunkPos(pos.x(), pos.z()))
        } else {
            delegate.handleBlockChange(pos, section, x, y, z, old, next)
        }
    }

    private fun isPlanetChunk(
        x: Int,
        z: Int,
    ): Boolean =
        SubLevelContainer
            .getContainer(level)
            ?.getPlot(ChunkPos(x, z))
            ?.subLevel
            ?.let { it is ServerSubLevel && isStatic(it) } == true

    override fun prePhysicsTicks() {
        for (pos in dirty.toList()) {
            colliders.remove(pos)?.let(delegate::remove)
            val body = SubLevelContainer.getContainer(level)?.getPlot(pos)?.subLevel as? ServerSubLevel
            val chunk = body?.plot?.getChunk(body.plot.toLocal(pos))
            if (body != null && !body.isRemoved && chunk != null && !chunk.isEmpty) {
                val terrain = TerrainChunk(body, chunk)
                delegate.add(terrain)
                colliders[pos] = terrain
            }
            dirty.remove(pos)
        }
        delegate.prePhysicsTicks()
    }

    override fun onStatsChanged(body: ServerSubLevel) {
        if (!isStatic(body)) delegate.onStatsChanged(body)
    }

    override fun teleport(
        body: PhysicsPipelineBody,
        position: Vector3dc,
        orientation: Quaterniondc,
    ) {
        if (!isStatic(body)) delegate.teleport(body, position, orientation)
    }

    override fun applyImpulse(
        body: PhysicsPipelineBody,
        impulse: Vector3dc,
        point: Vector3dc,
    ) {
        if (!isStatic(body)) delegate.applyImpulse(body, impulse, point)
    }

    override fun applyLinearAndAngularImpulse(
        body: PhysicsPipelineBody,
        linear: Vector3dc,
        angular: Vector3dc,
        rotate: Boolean,
    ) {
        if (!isStatic(body)) delegate.applyLinearAndAngularImpulse(body, linear, angular, rotate)
    }

    override fun addLinearAndAngularVelocity(
        body: PhysicsPipelineBody,
        linear: Vector3dc,
        angular: Vector3dc,
    ) {
        if (!isStatic(body)) delegate.addLinearAndAngularVelocity(body, linear, angular)
    }

    override fun getLinearVelocity(
        body: PhysicsPipelineBody,
        result: Vector3d,
    ): Vector3d = if (isStatic(body)) result.zero() else delegate.getLinearVelocity(body, result)

    override fun getAngularVelocity(
        body: PhysicsPipelineBody,
        result: Vector3d,
    ): Vector3d = if (isStatic(body)) result.zero() else delegate.getAngularVelocity(body, result)

    override fun wakeUp(body: PhysicsPipelineBody) {
        if (!isStatic(body)) delegate.wakeUp(body)
    }

    override fun <T : PhysicsConstraintHandle> addConstraint(
        a: PhysicsPipelineBody?,
        b: PhysicsPipelineBody?,
        config: PhysicsConstraintConfiguration<T>,
    ): T? = if (isStatic(a) || isStatic(b)) null else delegate.addConstraint(a, b, config)

    fun residentColliderCount(): Int = colliders.size

    private class TerrainChunk(
        val body: ServerSubLevel,
        private val chunk: LevelChunk,
    ) : KinematicContraption {
        private val base = BlockPos(chunk.pos.minBlockX, chunk.minBuildHeight, chunk.pos.minBlockZ)
        private val bounds = checkNotNull(checkNotNull(body.plot.getChunkHolder(body.plot.toLocal(chunk.pos))).boundingBox)
        private val position =
            body.logicalPose().transformPosition(
                Vector3d(base.x.toDouble(), base.y.toDouble(), base.z.toDouble()),
                Vector3d(),
            )
        private val orientation = Quaterniond(body.logicalPose().orientation())
        private val mass =
            object : MassTracker() {
                private val origin = Vector3d()

                override fun getCenterOfMass(): Vector3dc = origin

                override fun isInvalid(): Boolean = false
            }
        private val floating = FloatingClusterContainer()
        private val blocks =
            object : BlockGetter {
                override fun getBlockState(pos: BlockPos): BlockState =
                    if (pos.x in 0..15 && pos.z in 0..15 &&
                        pos.y in 0 until chunk.height
                    ) {
                        chunk.getBlockState(base.offset(pos))
                    } else {
                        Blocks.AIR.defaultBlockState()
                    }

                override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

                override fun getBlockEntity(pos: BlockPos): BlockEntity? = chunk.getBlockEntity(base.offset(pos))

                override fun getHeight(): Int = chunk.height

                override fun getMinBuildHeight(): Int = 0
            }

        override fun `sable$getLocalBounds`(box: BoundingBox3i) {
            box.set(bounds.minX(), bounds.minY() - base.y, bounds.minZ(), bounds.maxX(), bounds.maxY() - base.y, bounds.maxZ())
        }

        override fun `sable$blockGetter`(): BlockGetter = blocks

        override fun `sable$getMassTracker`(): MassTracker = mass

        override fun `sable$getPosition`(partial: Double): Vector3dc = position

        override fun `sable$getOrientation`(partial: Double): Quaterniond = orientation

        override fun `sable$liftProviders`(): Map<BlockPos, dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider.LiftProviderContext> =
            emptyMap()

        override fun `sable$getFloatingClusterContainer`(): FloatingClusterContainer = floating

        override fun `sable$shouldCollide`(): Boolean = true

        override fun `sable$isValid`(): Boolean = !body.isRemoved && body.plot.getChunk(body.plot.toLocal(chunk.pos)) === chunk
    }

    companion object {
        @JvmStatic fun unwrap(pipeline: PhysicsPipeline): PhysicsPipeline =
            if (pipeline is SablePlanetCollisionPipeline) pipeline.delegate else pipeline

        private val constructing = ThreadLocal.withInitial { false }

        @JvmStatic fun isStatic(body: PhysicsPipelineBody?): Boolean =
            body is ServerSubLevel && body.level.dimension() == PlanetLabCoordinates.DIMENSION &&
                (constructing.get() || SableCubePlanet.isFrame(body))

        @JvmStatic fun constructing(): Boolean = constructing.get()

        @JvmStatic fun constructing(value: Boolean) {
            constructing.set(value)
        }

        @JvmStatic fun mass(body: ServerSubLevel): MassTracker =
            object : MassTracker() {
                override fun getCenterOfMass(): Vector3dc = body.logicalPose().rotationPoint()

                // A chart is valid without mass: native invalid-mass teardown applies only to dynamic ships.
                override fun isInvalid(): Boolean = false
            }
    }
}
