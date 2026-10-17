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

package org.cbi.orbitalis.consumer.flight

import dev.propulsionteam.propulsionsimulated.PropulsionConfig
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.ThrusterForceProvider
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/** Ephemeral consumer-only owner of real engine inputs; native engines alone apply propulsion. */
internal class AutopilotSession(
    val body: ServerSubLevel,
    val target: Vector3,
    desiredAttitude: Quaterniond = Quaterniond(body.logicalPose().orientation()),
    mission: Boolean = false,
) : AutoCloseable {
    private val attitude = Quaterniond(desiredAttitude)
    private val engines = mutableListOf<Engine>()
    private var allocation: ThrusterAllocation.Result
    private var closed = false

    private class Engine(
        val block: CreativeThrusterBlockEntity,
    ) {
        var capacity = block.targetThrustNewtons.toDouble()
    }

    init {
        require(SableFlightGravity.isManaged(body.level)) { "Use the Overworld or a supported flight space" }
        require(body.massTracker.mass > 0 && body.massTracker.centerOfMass != null) { "Wait for native mass initialization" }
        require(body.plot.loadedChunks.size <= 4) { "Autopilot supports at most four native plot chunks" }
        for (holder in body.plot.loadedChunks) {
            for (block in holder.chunk.blockEntities.values) {
                if (block !is AbstractThrusterBlockEntity) continue
                require(block is CreativeThrusterBlockEntity) { "This test adapter supports creative Propulsion engines only" }
                if (!block.isController) continue
                require(block.power == 0.0f) { "Turn the linked controller and all engine inputs off first" }
                require(
                    block.saveWithFullMetadata(body.level.registryAccess()).getInt("ControlMode") ==
                        AbstractThrusterBlockEntity.ControlMode.NORMAL.ordinal,
                ) { "An engine already has a peripheral owner" }
                engines.add(Engine(block))
            }
        }
        engines.sortBy { it.block.blockPos.asLong() }
        require(engines.isNotEmpty() && engines.size <= 64) { "Autopilot requires 1..64 creative engine controllers" }
        require(engines.all { it.capacity.isFinite() && it.capacity > 0 }) { "An engine has no configured thrust" }
        validateBounds()
        val gravity = gravity()
        val localForce = attitude.transformInverse(joml(gravity).mul(-body.massTracker.mass))
        val hover = ThrusterAllocation.solve(actuators(), vector(localForce), Vector3.ZERO, lever())
        require(hover.forceResidual.length() / body.massTracker.mass < 0.25) {
            String.format(
                Locale.ROOT,
                "Installed engines cannot balance gravity at this attitude (mass=%.2f; total capacity=%.2f; residual=%.2f m/s²); add/reorient engines or increase their configured thrust",
                body.massTracker.mass,
                engines.sumOf { it.capacity },
                hover.forceResidual.length() / body.massTracker.mass,
            )
        }
        if (mission) {
            for (axis in listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0))) {
                for (sign in listOf(-1.0, 1.0)) {
                    val force =
                        attitude.transformInverse(
                            joml(axis * (sign * OrbitalGuidance.MAX_ACCELERATION) - gravity)
                                .mul(body.massTracker.mass),
                        )
                    val margin = ThrusterAllocation.solve(actuators(), vector(force), Vector3.ZERO, lever())
                    require(margin.forceResidual.length() / body.massTracker.mass < 0.25) {
                        "Mission needs balanced thrust authority for 3 m/s² in all six directions in addition to gravity"
                    }
                }
            }
        }
        allocation = hover
        // No engine is touched until every preflight check succeeds.
        for (e in engines) {
            e.block.setDigitalInput(0.0f)
            e.block.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL)
        }
    }

    private fun lever(): Double {
        var length = 1.0
        val com = body.massTracker.centerOfMass
        for (e in engines) length = max(length, ThrusterForceProvider.createSample(e.block, 1.0).pointLocal().distance(com))
        return length
    }

    private fun actuators(): List<ThrusterAllocation.Actuator> {
        val com = body.massTracker.centerOfMass
        return engines.map { e ->
            val sample = ThrusterForceProvider.createSample(e.block, 1.0)
            val force = Vector3d(e.block.thrustDirectionLocal).mul(e.capacity)
            val arm = Vector3d(sample.pointLocal()).sub(com)
            ThrusterAllocation.Actuator(vector(force), vector(arm.cross(force, Vector3d())))
        }
    }

    private fun gravity(): Vector3 = SableFlightGravity.acceleration(body.level, position(body))

    private fun validateBounds() {
        val minimum =
            if (body.level.dimension() ==
                net.minecraft.world.level.Level.OVERWORLD
            ) {
                body.level.minBuildHeight.toDouble()
            } else {
                64.0
            }
        require(position(body).y >= minimum && target.y >= minimum) {
            "Keep ship and target inside the supported surface/space height range"
        }
        val global = FlightSpaceCoordinates.globalPosition(body.level, position(body))
        val goal = FlightSpaceCoordinates.globalPosition(body.level, target)
        for (p in listOf(global, goal)) {
            require(
                FlatPlanetSpace.contains(body.level) && p.length() < 10000 &&
                    (body.level.dimension() == FlatPlanetSpace.SPACE || p.y >= minimum),
            ) { "Outside finite-body flight envelope" }
        }
    }

    /** Call once per server tick before native block-entity ticking; missions supply net inertial acceleration. */
    fun update(netAcceleration: Vector3? = null) {
        require(!closed && !body.isRemoved) { "Ship/controller is no longer available" }
        validateBounds()
        for (e in engines) {
            require(!e.block.isRemoved && e.block.isController && body.level.getBlockEntity(e.block.blockPos) === e.block) {
                "Engine layout changed; engage again after assembly settles"
            }
            // Native measured steady capacity includes atmospheric/configuration multipliers.
            // Do not recreate Propulsion's private thrust formula or overwrite its power setting.
            val effective = e.block.effectiveThrottle
            if (effective > 1e-5 && e.block.currentThrust > 0) {
                e.capacity = e.block.currentThrust / effective / PropulsionConfig.getThrustUnitsPerKnOrDefault()
            }
        }
        val physics = SubLevelPhysicsSystem.require(body.level)
        val handle = physics.getPhysicsHandle(body)
        val pose = body.logicalPose()
        val velocity = handle.getLinearVelocity(Vector3d())
        val omega = pose.orientation().transformInverse(handle.getAngularVelocity(Vector3d()), Vector3d())
        require(velocity.length() <= 220.0 && omega.length() <= 2.0) {
            "Speed/rotation exceeded test autopilot limits"
        }
        val error = Quaterniond(pose.orientation()).conjugate().mul(attitude).normalize()
        if (error.w < 0) error.mul(-1.0)
        val sine = sqrt(error.x * error.x + error.y * error.y + error.z * error.z)
        val factor = if (sine > 1e-8) 2 * atan2(sine, error.w) / sine else 2.0
        val demand =
            FlightHoldGuidance.command(
                CartesianState(position(body), vector(velocity)),
                target,
                gravity(),
                Vector3(error.x * factor, error.y * factor, error.z * factor),
                vector(omega),
            )
        val acceleration = netAcceleration?.minus(gravity()) ?: demand.thrustAcceleration
        val force = pose.orientation().transformInverse(joml(acceleration).mul(body.massTracker.mass))
        val inertia = body.massTracker.inertiaTensor
        val torque = inertia.transform(joml(demand.angularAcceleration), Vector3d())
        torque.add(Vector3d(omega).cross(inertia.transform(omega, Vector3d())))
        allocation = ThrusterAllocation.solve(actuators(), vector(force), vector(torque), lever())
        for (i in engines.indices) engines[i].block.setDigitalInput(allocation.throttle[i].toFloat())
    }

    fun status(): String =
        String.format(
            Locale.ROOT,
            "Autopilot: %d engines; target COM=(%.2f,%.2f,%.2f); error=%.2f m; predicted force residual=%.3f m/s²; torque residual=%.3f. Missing authority is not replaced by virtual forces.",
            engines.size,
            target.x,
            target.y,
            target.z,
            (position(body) - target).length(),
            allocation.forceResidual.length() / body.massTracker.mass,
            allocation.torqueResidual.length(),
        )

    override fun close() {
        if (closed) return
        closed = true
        for (e in engines) {
            if (e.block.isRemoved) continue
            e.block.setDigitalInput(0.0f)
            e.block.setRedstoneInput(0)
            e.block.setControlMode(AbstractThrusterBlockEntity.ControlMode.NORMAL)
            e.block.dirtyThrust()
            e.block.notifyUpdate()
        }
    }

    companion object {
        fun vector(v: Vector3dc) = Vector3(v.x(), v.y(), v.z())

        private fun joml(v: Vector3) = Vector3d(v.x, v.y, v.z)

        fun position(body: ServerSubLevel): Vector3 =
            vector(body.logicalPose().transformPosition(body.massTracker.centerOfMass, Vector3d()))

        fun enginesFaded(body: ServerSubLevel): Boolean =
            body.plot.loadedChunks.all { holder ->
                holder.chunk.blockEntities.values.none { engine ->
                    engine is AbstractThrusterBlockEntity && engine.isController && (engine.power != 0.0f || engine.currentThrust != 0.0f)
                }
            }
    }
}
