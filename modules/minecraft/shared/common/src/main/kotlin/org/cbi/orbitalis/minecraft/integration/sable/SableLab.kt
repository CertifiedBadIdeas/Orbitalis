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
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.SurfaceFlight
import org.cbi.orbitalis.core.planet.TwoBodyGravity
import org.cbi.orbitalis.core.planet.Vector3
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

    data class BurnReceipt(
        val probeIndex: Int,
        val seconds: Double,
        val deltaV: Vector3,
        val before: CartesianState,
        val after: CartesianState,
    )

    private data class Burn(
        val probeIndex: Int,
        val prograde: Boolean,
        val deltaV: Double,
    )

    private var burn: Burn? = null
    val burnPending get() = burn != null
    var completedBurns = 0
        private set
    var lastBurn: BurnReceipt? = null
        private set
    var burnRejection: String? = null
        private set

    private data class Probe(
        val body: ServerSubLevel,
        val handle: RigidBodyHandle,
    )

    private val container = checkNotNull(SubLevelContainer.getContainer(level)) { "Sable container is unavailable" }
    private val physics = checkNotNull(container.physicsSystem()) { "Sable physics is unavailable" }
    private val probes = mutableListOf<Probe>()
    private var pulseSeconds = 0.0
    private var sceneCreatedAt: Long? = null

    private enum class Mode { COAST, ORBIT, FLIGHT }

    private var flight: SurfaceFlight? = null
    val flightReport get() = flight
    private var flightInitialized = false
    private var flightPad: SableFlightPad? = null
    private var orbit: SableOrbitExperiment? = null
    val orbitMetrics get() = orbit?.metrics
    var failureReason: String? = null
        private set
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

    fun start() = startScene(Mode.COAST)

    fun startOrbit() = startScene(Mode.ORBIT)

    fun startFlight() = startScene(Mode.FLIGHT)

    private fun startScene(mode: Mode) {
        // The pinned integration fails block placement when a new scene is replaced in the same game tick.
        require(sceneCreatedAt != level.gameTime) { "Wait until the next game tick before replacing a newly created scene" }
        sceneCreatedAt = level.gameTime
        clear()
        orbit = if (mode == Mode.ORBIT) SableOrbitExperiment() else null
        flight = if (mode == Mode.FLIGHT) SurfaceFlight() else null
        flightInitialized = false
        physicsSeconds = 0.0
        pulseSeconds = 0.0
        callbacks = 0
        commandedDeltaV = 0.0
        completedShifts = 0
        frameOrigin.zero()
        try {
            if (mode == Mode.FLIGHT) {
                flightPad = SableFlightPad(level)
                flightPad!!.prepare()
            }
            repeat(if (mode == Mode.FLIGHT) 1 else 2) { index ->
                val pose = Pose3d()
                if (mode == Mode.FLIGHT) {
                    val start = FLIGHT_CENTRE + SurfaceFlight.LAUNCH
                    pose.position().set(start.x, start.y, start.z)
                } else if (mode == Mode.ORBIT) {
                    val centre = SableOrbitExperiment.CENTRE
                    val offset = if (index == 0) SableOrbitExperiment.RADIUS else -SableOrbitExperiment.RADIUS
                    pose.position().set(centre.x + offset, centre.y, centre.z)
                } else {
                    pose.position().set(index * 8.0, 80.0, 120.0)
                }
                val body = container.allocateNewSubLevel(pose) as ServerSubLevel
                body.userDataTag = CompoundTag().apply { putBoolean(MARKER, true) }
                val plot = body.plot
                plot.newEmptyChunk(plot.centerChunk)
                val block = if (index == 0) Blocks.GOLD_BLOCK else Blocks.IRON_BLOCK
                check(plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, block.defaultBlockState(), 3))
                if (mode == Mode.ORBIT && index == 1) {
                    check(plot.embeddedLevelAccessor.setBlock(BlockPos(0, 1, 0), block.defaultBlockState(), 3))
                }
                body.updateLastPose()
                container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                probes.add(Probe(body, physics.getPhysicsHandle(body)))
            }
        } catch (error: Exception) {
            clear()
            throw error
        }
    }

    /** Apply the selected force experiment using mass * acceleration * actual physics timestep. */
    fun beforePhysics(stepSeconds: Double) {
        require(stepSeconds.isFinite() && stepSeconds > 0)
        if (callbacks > 0 && probes.any { !validProbe(it) }) {
            clear()
            failureReason = "Diagnostic stopped: a construction became unavailable or has invalid mass."
            return
        }
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
        flight?.let { controller ->
            val probe = probes.single()
            if (!flightInitialized) {
                val pose = probe.body.logicalPose()
                val com = pose.transformPosition(checkNotNull(probe.body.massTracker.centerOfMass), Vector3d())
                val desired = FLIGHT_CENTRE + SurfaceFlight.LAUNCH
                val translation = Vector3d(desired.x, desired.y, desired.z).sub(com)
                probe.handle.teleport(Vector3d(pose.position()).add(translation), pose.orientation())
                probe.handle.addLinearAndAngularVelocity(
                    probe.handle.getLinearVelocity(Vector3d()).negate(),
                    probe.handle.getAngularVelocity(Vector3d()).negate(),
                )
                flightInitialized = true
            }
            val state = orbitalState(0)
            val thrust = controller.command(state, stepSeconds)
            if (controller.failure != null) {
                val reason = controller.failure
                clear()
                failureReason = reason
                return
            }
            val acceleration = controller.gravity.acceleration(state.position) + thrust
            val impulse = acceleration * (probe.body.massTracker.mass * stepSeconds)
            applyGlobalImpulse(probe, Vector3d(impulse.x, impulse.y, impulse.z))
            lastStepSeconds = stepSeconds
            return
        }
        orbit?.let { experiment ->
            if (!experiment.initialized) initializeOrbit(experiment)
            val states = checkedOrbitStates() ?: return
            burn?.let { request ->
                burn = null
                val before = states[request.probeIndex]
                val after = burnedState(before, request)
                // Revalidate at execution: the orbit can move after the command was queued.
                if (safeOrbit(experiment, after)) {
                    val deltaV = after.velocity - before.velocity
                    val impulse = deltaV * probes[request.probeIndex].body.massTracker.mass
                    applyGlobalImpulse(probes[request.probeIndex], Vector3d(impulse.x, impulse.y, impulse.z))
                    experiment.kick(request.probeIndex, deltaV)
                    lastBurn = BurnReceipt(request.probeIndex, experiment.metrics.seconds, deltaV, before, after)
                    completedBurns++
                } else {
                    burnRejection = "Burn canceled: predicted orbit left the 4–64 m diagnostic band."
                }
            }
            probes.forEachIndexed { index, probe ->
                val acceleration = experiment.gravity.acceleration(states[index].position)
                val impulse = acceleration * (probe.body.massTracker.mass * stepSeconds)
                applyGlobalImpulse(probe, Vector3d(impulse.x, impulse.y, impulse.z))
            }
            lastStepSeconds = stepSeconds
            return
        }
        if (callbacks == 1) probes[1].handle.addLinearAndAngularVelocity(Vector3d(), Vector3d(0.0, 0.2, 0.0))
        lastStepSeconds = stepSeconds
        val forcedSeconds = min(stepSeconds, (0.5 - pulseSeconds).coerceAtLeast(0.0))
        if (forcedSeconds > 0) {
            val deltaV = 2.0 * forcedSeconds
            probes.forEach {
                val mass = it.body.massTracker.mass
                check(mass.isFinite() && mass > 0) { "Sable returned an invalid body mass" }
                applyGlobalImpulse(it, Vector3d(mass * deltaV, 0.0, 0.0))
            }
            commandedDeltaV += deltaV
            pulseSeconds += forcedSeconds
        }
    }

    private fun applyGlobalImpulse(
        probe: Probe,
        impulse: Vector3d,
    ) {
        // Sable accepts a body-local impulse, while celestial acceleration uses inertial axes.
        probe.body
            .logicalPose()
            .orientation()
            .transformInverse(impulse)
        probe.handle.applyLinearImpulse(impulse)
    }

    fun afterPhysics(stepSeconds: Double) {
        if (probes.isEmpty()) return
        orbit?.takeIf { it.initialized }?.let { experiment ->
            val states = checkedOrbitStates() ?: return
            experiment.sample(states, stepSeconds)
        }
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

    /** COM position relative to the fixed central body [m], with native velocity [m/s]. */
    fun orbitalState(index: Int): CartesianState {
        val probe = probes[index]
        val pose = probe.body.logicalPose()
        val com = pose.transformPosition(checkNotNull(probe.body.massTracker.centerOfMass), Vector3d()).add(frameOrigin)
        val velocity = probe.handle.getLinearVelocity(Vector3d())
        return CartesianState(
            Vector3(com.x, com.y, com.z) - (if (flight != null) FLIGHT_CENTRE else SableOrbitExperiment.CENTRE),
            Vector3(velocity.x, velocity.y, velocity.z),
        )
    }

    private fun initializeOrbit(experiment: SableOrbitExperiment) {
        probes.forEachIndexed { index, probe ->
            val sign = if (index == 0) 1.0 else -1.0
            val pose = probe.body.logicalPose()
            val com = pose.transformPosition(checkNotNull(probe.body.massTracker.centerOfMass), Vector3d())
            val desired = SableOrbitExperiment.CENTRE + Vector3(sign * SableOrbitExperiment.RADIUS, 0.0, 0.0)
            val translation = Vector3d(desired.x, desired.y, desired.z).sub(frameOrigin).sub(com)
            probe.handle.teleport(Vector3d(pose.position()).add(translation), pose.orientation())
            val velocity = probe.handle.getLinearVelocity(Vector3d())
            probe.handle.addLinearAndAngularVelocity(
                Vector3d(0.0, 0.0, sign * SableOrbitExperiment.SPEED).sub(velocity),
                Vector3d(0.0, if (index == 1) 0.2 else 0.0, 0.0).sub(probe.handle.getAngularVelocity(Vector3d())),
            )
        }
        experiment.initialize(List(probes.size, ::orbitalState))
    }

    private fun validProbe(probe: Probe): Boolean {
        val mass = probe.body.massTracker.mass
        return !probe.body.isRemoved && probe.handle.isValid && mass.isFinite() && mass > 0 && probe.body.massTracker.centerOfMass != null
    }

    private fun checkedOrbitStates(): List<CartesianState>? {
        val states =
            if (probes.all(::validProbe)) {
                try {
                    List(probes.size, ::orbitalState)
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        // A bounded diagnostic scene must not feed a singularity/NaN into the native solver.
        if (states.size != 2 || states.any { it.position.length() !in 4.0..64.0 }) {
            clear()
            failureReason = "Orbit stopped: a probe became invalid or left the 4–64 m diagnostic radial bounds."
            return null
        }
        return states
    }

    fun orbitApsides(index: Int): TwoBodyGravity.Apsides? = checkNotNull(orbit).gravity.apsides(orbitalState(index))

    /** Queue one bounded diagnostic kick. Direction is evaluated in inertial axes at execution. */
    fun requestBurn(
        index: Int,
        prograde: Boolean,
        deltaV: Double,
    ) {
        require(index in 0..1 && deltaV.isFinite() && deltaV in 0.01..1.0) { "Choose probe 0/1 and delta-v 0.01–1 m/s" }
        val experiment = orbit
        require(experiment != null && experiment.initialized && probes.size == 2) { "Start an orbit and wait for physics initialization" }
        require(burn == null) { "Wait for the pending burn" }
        val request = Burn(index, prograde, deltaV)
        require(safeOrbit(experiment, burnedState(orbitalState(index), request))) { "Predicted orbit must stay inside 4–64 m" }
        burnRejection = null
        burn = request
    }

    private fun burnedState(
        state: CartesianState,
        request: Burn,
    ): CartesianState =
        state.copy(velocity = state.velocity + state.velocity.normalized() * (if (request.prograde) request.deltaV else -request.deltaV))

    private fun safeOrbit(
        experiment: SableOrbitExperiment,
        state: CartesianState,
    ): Boolean {
        val apsides = experiment.gravity.apsides(state) ?: return false
        return apsides.periapsis >= 4.0 && apsides.apoapsis <= 64.0
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
        flight = null
        flightInitialized = false
        flightPad?.clear()
        flightPad = null
        burn = null
        completedBurns = 0
        lastBurn = null
        burnRejection = null
        shift = null
        orbit = null
        failureReason = null
        // Recover only this adapter's explicitly marked constructions after a restart/crash, too.
        container.allSubLevels.toList().filter { it.userDataTag?.getBoolean(MARKER) == true }.forEach {
            container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
        }
        probes.clear()
    }

    companion object {
        val FLIGHT_CENTRE = Vector3(0.5, 96.0, 120.5)
        private const val MARKER = "orbitalis_sable_diagnostic"
    }
}
