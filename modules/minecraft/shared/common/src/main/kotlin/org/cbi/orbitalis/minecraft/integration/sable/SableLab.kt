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

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Unit
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.min

/** Optional adapter: Sable alone integrates these bodies. Only the dedicated laboratory is modified. */
class SableLab(
    val level: ServerLevel,
) {
    data class Reading(
        val position: Vector3d,
        val orientation: Quaterniond,
        val velocity: Vector3d,
        val angularVelocity: Vector3d,
        val mass: Double,
    )

    private data class Probe(
        val body: ServerSubLevel,
        val handle: RigidBodyHandle,
    )

    private val container = checkNotNull(SubLevelContainer.getContainer(level)) { "Sable container is unavailable" }
    private val physics = checkNotNull(container.physicsSystem()) { "Sable physics is unavailable" }
    private val probes = mutableListOf<Probe>()
    private var pulseSeconds = 0.0
    private var shift: Pair<Vec3, ServerPlayer?>? = null
    val frameOrigin = Vector3d()
    var physicsSeconds = 0.0
        private set
    var callbacks = 0
        private set
    var lastStepSeconds = 0.0
        private set
    var commandedDeltaV = 0.0
        private set
    var completedShifts = 0
        private set
    val size get() = probes.size
    val shiftPending get() = shift != null

    init {
        require(level.dimension() == PlanetLabCoordinates.DIMENSION) { "Sable diagnostics are confined to the planet laboratory" }
    }

    fun start() {
        clear()
        physicsSeconds = 0.0
        pulseSeconds = 0.0
        callbacks = 0
        commandedDeltaV = 0.0
        completedShifts = 0
        frameOrigin.zero()
        try {
            repeat(2) { index ->
                val pose = Pose3d()
                pose.position().set(index * 8.0, 80.0, 120.0)
                val body = container.allocateNewSubLevel(pose) as ServerSubLevel
                body.userDataTag = CompoundTag().apply { putBoolean(MARKER, true) }
                val plot = body.plot
                plot.newEmptyChunk(plot.centerChunk)
                val block = if (index == 0) Blocks.GOLD_BLOCK else Blocks.IRON_BLOCK
                check(plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, block.defaultBlockState(), 3))
                body.updateLastPose()
                container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                probes.add(Probe(body, physics.getPhysicsHandle(body)))
            }
        } catch (error: Exception) {
            clear()
            throw error
        }
    }

    /** Finite force pulse: integrate impulse as mass * acceleration * actual physics timestep. */
    fun beforePhysics(stepSeconds: Double) {
        require(stepSeconds.isFinite() && stepSeconds > 0)
        // A command may have queued an observer who left before this physics callback.
        if (shift?.second?.let { it.level() !== level || !it.isCreative || it.isPassenger || it.isRemoved } == true) shift = null
        shift?.let { (offset, player) ->
            val translation = Vector3d(-offset.x, -offset.y, -offset.z)
            probes.forEach { probe ->
                val pose = probe.body.logicalPose()
                probe.handle.teleport(Vector3d(pose.position()).add(translation), pose.orientation())
            }
            player?.let {
                val velocity = it.deltaMovement
                it.teleportTo(it.x - offset.x, it.y - offset.y, it.z - offset.z)
                it.deltaMovement = velocity
            }
            frameOrigin.add(offset.x, offset.y, offset.z)
            completedShifts++
            shift = null
        }
        if (probes.isEmpty()) return
        // The pinned Sable build loses an impulse submitted before its first completed step.
        // Apply the complete measured pulse only after that initialization boundary.
        if (callbacks == 0) return
        if (callbacks == 1) probes[1].handle.addLinearAndAngularVelocity(Vector3d(), Vector3d(0.0, 0.2, 0.0))
        lastStepSeconds = stepSeconds
        val forcedSeconds = min(stepSeconds, (0.5 - pulseSeconds).coerceAtLeast(0.0))
        if (forcedSeconds > 0) {
            val deltaV = 2.0 * forcedSeconds
            probes.forEach {
                val mass = it.body.massTracker.mass
                check(mass.isFinite() && mass > 0) { "Sable returned an invalid body mass" }
                it.handle.applyLinearImpulse(Vector3d(mass * deltaV, 0.0, 0.0))
            }
            commandedDeltaV += deltaV
            pulseSeconds += forcedSeconds
        }
    }

    fun afterPhysics(stepSeconds: Double) {
        if (probes.isEmpty()) return
        physicsSeconds += stepSeconds
        callbacks++
    }

    fun reading(index: Int): Reading {
        val probe = probes[index]
        check(!probe.body.isRemoved && probe.handle.isValid) { "Diagnostic construction was unloaded" }
        return Reading(
            Vector3d(probe.body.logicalPose().position()),
            Quaterniond(probe.body.logicalPose().orientation()),
            probe.handle.getLinearVelocity(Vector3d()),
            probe.handle.getAngularVelocity(Vector3d()),
            probe.body.massTracker.mass,
        )
    }

    fun requestShift(
        offset: Vec3,
        observer: ServerPlayer? = null,
    ) {
        require(offset.x.isFinite() && offset.y.isFinite() && offset.z.isFinite() && offset.length() <= 64.0)
        require(probes.size == 2 && shift == null) { "Start a scene and wait for the previous shift" }
        require(observer == null || (observer.level() === level && observer.isCreative && !observer.isPassenger)) {
            "The observer must be an unmounted creative player in the laboratory"
        }
        shift = offset to observer
    }

    fun clear() {
        shift = null
        // Recover only this adapter's explicitly marked constructions after a restart/crash, too.
        container.allSubLevels.toList().filter { it.userDataTag?.getBoolean(MARKER) == true }.forEach {
            container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
        }
        probes.clear()
    }

    companion object {
        private const val MARKER = "orbitalis_sable_diagnostic"
    }
}
