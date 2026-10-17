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

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import dev.propulsionteam.propulsionsimulated.PropulsionConfig
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.ThrusterForceProvider
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterPowerScrollValueBehaviour
import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.creative_vector_thruster.CreativeVectorThrusterBlockEntity
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.Direction
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.atan2
import kotlin.math.sqrt

/** Consumer-only throttle/steering owner for a rocket with real bottom-mounted vector engines. */
internal class RocketDrive(
    val body: ServerSubLevel,
    requireUpright: Boolean = false,
) : AutoCloseable {
    private class Engine(
        val block: AbstractThrusterBlockEntity,
    ) {
        val main = block is CreativeVectorThrusterBlockEntity
        var capacity =
            if (block is CreativeThrusterBlockEntity) {
                block.targetThrustNewtons.toDouble()
            } else {
                (
                    checkNotNull(
                        block.getBehaviour(ScrollValueBehaviour.TYPE),
                    ) as CreativeThrusterPowerScrollValueBehaviour
                ).targetThrust.toDouble()
            }
    }

    private val engines = mutableListOf<Engine>()
    private var closed = false
    private var nose = Vector3(0.0, 1.0, 0.0)
    private var allocation: RocketGimbalAllocation.Result? = null
    private var ticks = 0

    init {
        require(SableFlightGravity.isManaged(body.level) && body.massTracker.mass > 0.0)
        require(body.plot.loadedChunks.size <= 4) { "Rocket exceeds four native plot chunks" }
        for (holder in body.plot.loadedChunks) {
            for (block in holder.chunk.blockEntities.values) {
                if (block !is AbstractThrusterBlockEntity) continue
                require(
                    block.isController && (
                        (block is CreativeVectorThrusterBlockEntity && block.facing == Direction.UP) ||
                            (block is CreativeThrusterBlockEntity && block.facing.axis != Direction.Axis.Y)
                    ),
                ) {
                    "Rocket requires independent UP-facing creative vector engines underneath the hull"
                }
                require(
                    block.power == 0.0f && (
                        block !is CreativeVectorThrusterBlockEntity ||
                            (block.targetVectorX == 0.0f && block.targetVectorY == 0.0f)
                    ),
                ) {
                    "Turn off all manual throttle and steering inputs before rocket control"
                }
                require(
                    block.saveWithFullMetadata(body.level.registryAccess()).getInt("ControlMode") ==
                        AbstractThrusterBlockEntity.ControlMode.NORMAL.ordinal &&
                        (block !is CreativeVectorThrusterBlockEntity || !block.hasPeripheralThrustOverride()),
                ) {
                    "A rocket engine already has a peripheral owner"
                }
                require(
                    block !is CreativeVectorThrusterBlockEntity || block is RocketSteering,
                ) { "Consumer vector-steering adapter is not installed" }
                engines.add(Engine(block))
            }
        }
        engines.sortBy { it.block.blockPos.asLong() }
        require(engines.size <= 16 && engines.count { it.main } in 4..16 && engines.all { it.capacity.isFinite() && it.capacity > 0.0 }) {
            "Rocket requires 4..16 configured creative vector engines"
        }
        require(layout(engines.filter { it.main }).all { it.arm.y < -2.0 }) { "Install vector engines below the centre of mass" }
        val up = body.logicalPose().orientation().transform(Vector3d(0.0, 1.0, 0.0))
        require(!requireUpright || up.y > 0.98) { "Start rocket control with an upright hull" }
        require(maximumAcceleration >= 25.0) {
            "Rocket needs at least 25 m/s² of configured native thrust authority; mass=${body.massTracker.mass}, capacity=${engines.sumOf {
                it.capacity
            }}"
        }
        // Ownership changes only after the entire layout and every manual input passed preflight.
        for (engine in engines) {
            engine.block.setDigitalInput(0.0f)
            engine.block.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL)
        }
    }

    val maximumAcceleration: Double get() = engines.filter { it.main }.sumOf { it.capacity } / body.massTracker.mass

    private fun layout(selected: List<Engine>): List<RocketGimbalAllocation.Engine> =
        selected.map { engine ->
            val sample = ThrusterForceProvider.createSample(engine.block, 1.0)
            RocketGimbalAllocation.Engine(
                AutopilotSession.vector(Vector3d(sample.pointLocal()).sub(body.massTracker.centerOfMass)),
                engine.capacity,
                if (engine.main) null else AutopilotSession.vector(engine.block.thrustDirectionLocal),
            )
        }

    fun update(
        netAcceleration: Vector3,
        desiredNose: Vector3? = null,
    ) {
        val gravity = SableFlightGravity.acceleration(body.level, AutopilotSession.position(body))
        val thrust = netAcceleration - gravity
        if (desiredNose != null) {
            nose = desiredNose.normalized()
        } else if (thrust.length() >= 0.3) {
            nose = thrust.normalized()
        }
        apply(thrust, nose)
    }

    /** Balanced native RCS couples turn the hull while all main-engine throttles stay zero. */
    fun turn(direction: Vector3) {
        require(direction.length() > 1e-8 && engines.any { !it.main }) { "Rocket has no RCS authority" }
        nose = direction.normalized()
        apply(Vector3.ZERO, nose, true)
    }

    private fun apply(
        thrust: Vector3,
        direction: Vector3,
        orientationOnly: Boolean = false,
    ) {
        require(!closed && !body.isRemoved) { "Rocket/controller unavailable" }
        val physics = SubLevelPhysicsSystem.require(body.level)
        require(!physics.paused) { "Rocket physics paused" }
        val handle = physics.getPhysicsHandle(body)
        val pose = body.logicalPose()
        val omega = pose.orientation().transformInverse(handle.getAngularVelocity(Vector3d()), Vector3d())
        val global = FlightSpaceCoordinates.globalPosition(body.level, AutopilotSession.position(body))
        require(global.length() < 10000.0 && handle.getLinearVelocity(Vector3d()).length() < 220.0 && omega.length() < 2.0) {
            "Rocket left the tested flight envelope"
        }
        for (engine in engines) {
            val block = engine.block
            require(!block.isRemoved && body.level.getBlockEntity(block.blockPos) === block) { "Rocket engine layout changed" }
            val effective = block.effectiveThrottle
            if (effective > 1e-5 && block.currentThrust > 0.0f) {
                engine.capacity = block.currentThrust / effective / PropulsionConfig.getThrustUnitsPerKnOrDefault()
            }
        }
        val desired = Quaterniond().rotationTo(Vector3d(0.0, 1.0, 0.0), Vector3d(direction.x, direction.y, direction.z))
        val error = Quaterniond(pose.orientation()).conjugate().mul(desired).normalize()
        if (error.w < 0.0) error.mul(-1.0)
        val sine = sqrt(error.x * error.x + error.y * error.y + error.z * error.z)
        val factor = if (sine > 1e-8) 2.0 * atan2(sine, error.w) / sine else 2.0
        val angular =
            OrbitalGuidance.limited(
                Vector3(error.x * factor, error.y * factor, error.z * factor) * 0.8 -
                    AutopilotSession.vector(omega) * 1.8,
                0.45,
            )
        val inertia = body.massTracker.inertiaTensor
        val torque = inertia.transform(Vector3d(angular.x, angular.y, angular.z), Vector3d())
        torque.add(Vector3d(omega).cross(inertia.transform(omega, Vector3d())))
        val localForce = pose.orientation().transformInverse(Vector3d(thrust.x, thrust.y, thrust.z).mul(body.massTracker.mass))
        val selected = if (orientationOnly) engines.filter { !it.main } else engines
        val result = RocketGimbalAllocation.solve(layout(selected), AutopilotSession.vector(localForce), AutopilotSession.vector(torque))
        allocation = result
        ticks++
        if (orientationOnly) engines.filter { it.main }.forEach { it.block.setDigitalInput(0.0f) }
        selected.forEachIndexed { i, engine ->
            val input = result.inputs[i]
            if (engine.main) (engine.block as RocketSteering).setRocketVector(input.vectorX.toFloat(), input.vectorY.toFloat())
            engine.block.setDigitalInput(input.throttle.toFloat())
            if (ticks % 5 == 0) engine.block.notifyUpdate()
        }
    }

    fun alignment(direction: Vector3): Double {
        val up = body.logicalPose().orientation().transform(Vector3d(0.0, 1.0, 0.0))
        return AutopilotSession.vector(up).dot(direction.normalized())
    }

    fun status(): String =
        "Rocket: ${engines.size} vector engines; force residual=%.3f m/s²".format(
            java.util.Locale.ROOT,
            (allocation?.forceResidual?.length() ?: 0.0) / body.massTracker.mass,
        )

    override fun close() {
        if (closed) return
        closed = true
        for (engine in engines) {
            val block = engine.block
            if (block.isRemoved) continue
            block.setDigitalInput(0.0f)
            if (engine.main) (block as RocketSteering).clearRocketVector()
            block.setControlMode(AbstractThrusterBlockEntity.ControlMode.NORMAL)
            block.dirtyThrust()
            block.notifyUpdate()
        }
    }
}
