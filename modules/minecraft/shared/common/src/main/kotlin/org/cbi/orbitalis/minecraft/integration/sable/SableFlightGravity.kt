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
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.joml.Vector3d

/** Dimension-scoped force adapter. It neither owns ships nor integrates their motion. */
object SableFlightGravity {
    fun isManaged(level: Level): Boolean = FlatPlanetSpace.contains(level)

    private val flatFields = mutableMapOf<Pair<Double, Double>, SurfaceGravityLayer>()

    /** Native configuration is the surface authority; changing it intentionally changes the mass model. */
    fun flatField(level: ServerLevel): SurfaceGravityLayer {
        val native = DimensionPhysicsData.getGravity(level.server.overworld())
        require(native.x() == 0.0 && native.z() == 0.0 && native.y() < 0.0) { "Flat planet requires vertical downward native gravity" }
        val selected =
            org.cbi.orbitalis.minecraft.world.WorldSystemSettings
                .get(level.server)
        val g = if (selected == null) -native.y() else 11.0
        val width = selected?.earthWidth?.toDouble() ?: org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SIZE
        return flatFields.getOrPut(g to width) {
            FlatPlanetSpace.gravityField(g, width)
        }
    }

    fun globalPosition(
        level: Level,
        position: Vector3,
    ): Vector3 = FlightSpaceCoordinates.globalPosition(level, position)

    data class Reading(
        val position: Vector3,
        val velocity: Vector3d,
        val acceleration: Vector3,
        val mass: Double,
    )

    fun reading(body: ServerSubLevel): Reading {
        val com =
            body.logicalPose().transformPosition(
                requireNotNull(body.massTracker.centerOfMass) {
                    "Wait for ship mass initialization"
                },
                Vector3d(),
            )
        val position = Vector3(com.x, com.y, com.z)
        val physics = checkNotNull(SubLevelContainer.getContainer(body.level)?.physicsSystem())
        val velocity = physics.getPhysicsHandle(body).getLinearVelocity(Vector3d())
        return Reading(
            position,
            velocity,
            acceleration(body.level, position, Vector3(velocity.x, velocity.y, velocity.z)),
            body.massTracker.mass,
        )
    }

    /** Ship adapter for the finite field; player exterior gravity shares its mass model. */
    @JvmOverloads
    fun acceleration(
        level: Level,
        position: Vector3,
        velocity: Vector3 = Vector3.ZERO,
        offsetSeconds: Double = 0.0,
    ): Vector3 {
        require(isManaged(level)) { "No Orbitalis construction gravity in this dimension" }
        if (!CelestialFlight.available(level)) {
            if (level.dimension() == MoonWorld.KEY) {
                val native = DimensionPhysicsData.getGravity(level as ServerLevel)
                return Vector3(native.x(), native.y(), native.z())
            }
            return flatField(level as ServerLevel).acceleration(globalPosition(level, position))
        }
        val sample = CelestialFlight.sample(level)
        return if (level.dimension() == MoonWorld.KEY) {
            sample.dynamics.lunarAcceleration(CartesianState(position, velocity), offsetSeconds)
        } else if (level.dimension() == Level.OVERWORLD) {
            sample.dynamics.earthAcceleration(CartesianState(position, velocity), offsetSeconds)
        } else {
            sample.dynamics.acceleration(globalPosition(level, position), offsetSeconds)
        }
    }

    /** Split the field around native drift while Sable owns body integration. */
    fun beforePhysics(
        level: ServerLevel,
        seconds: Double,
    ) = applyGravity(level, seconds, false)

    fun afterPhysics(
        level: ServerLevel,
        seconds: Double,
    ) {
        applyGravity(level, seconds, true)
    }

    private fun applyGravity(
        level: ServerLevel,
        seconds: Double,
        after: Boolean,
    ) {
        if (!isManaged(level)) return
        require(seconds.isFinite() && seconds > 0.0)
        val container = SubLevelContainer.getContainer(level) ?: return
        val physics = container.physicsSystem() ?: return
        val fraction = 0.5
        // The pinned Sable event is emitted for each substep; partialPhysicsTick is its end fraction.
        val endTime = (physics.partialPhysicsTick * 0.05).coerceIn(seconds, 0.05)
        val sampleTime = if (after) endTime else endTime - seconds
        val native = DimensionPhysicsData.getGravity(level)
        for (body in container.allSubLevels.filterIsInstance<ServerSubLevel>()) {
            val mass = body.massTracker.mass
            val localCom = body.massTracker.centerOfMass ?: continue
            if (!mass.isFinite() || mass <= 0.0) continue
            val pose = body.logicalPose()
            val com = pose.transformPosition(localCom, Vector3d())
            if (!com.isFinite) continue
            val v = physics.getPhysicsHandle(body).getLinearVelocity(Vector3d())
            val acceleration = acceleration(level, Vector3(com.x, com.y, com.z), Vector3(v.x, v.y, v.z), sampleTime)
            // Cancel only native gravity, retaining Sable's integration and every other force.
            val impulse =
                Vector3d(
                    acceleration.x - native.x(),
                    acceleration.y - native.y(),
                    acceleration.z - native.z(),
                ).mul(mass * seconds * fraction)
            pose.orientation().transformInverse(impulse)
            val handle = physics.getPhysicsHandle(body)
            if (impulse.lengthSquared() > 0.0) handle.applyLinearImpulse(impulse)
            // Euler's rigid-body equation in rotating terrain axes. Without this transport
            // torque a freely spinning asymmetric hull acquires an artificial inertial rotation.
            if (CelestialFlight.available(level)) {
                val frame = CelestialFlight.sample(level).frames.frame(CelestialFlight.world(level.dimension()))
                val frameSpin = frame.orientation.inverse(frame.angularVelocity)
                val frameAcceleration = frame.orientation.inverse(frame.angularAcceleration)
                if (frameSpin.length() > 0.0 || frameAcceleration.length() > 0.0) {
                    val w = pose.orientation().transformInverse(handle.getAngularVelocity(Vector3d()), Vector3d())
                    val spin = pose.orientation().transformInverse(Vector3d(frameSpin.x, frameSpin.y, frameSpin.z))
                    val alpha = pose.orientation().transformInverse(Vector3d(frameAcceleration.x, frameAcceleration.y, frameAcceleration.z))
                    val physical = Vector3d(w).add(spin)
                    val inertia = body.massTracker.inertiaTensor
                    val correction =
                        inertia
                            .transform(Vector3d(w).cross(spin).sub(alpha), Vector3d())
                            .sub(Vector3d(physical).cross(inertia.transform(physical, Vector3d())))
                            .add(Vector3d(w).cross(inertia.transform(w, Vector3d())))
                    if (correction.lengthSquared() > 1e-20) handle.applyTorqueImpulse(correction.mul(seconds * fraction))
                }
            }
        }
    }
}
