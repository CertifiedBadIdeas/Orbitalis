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
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.joml.Vector3d

/** Dimension-scoped force adapter. It neither owns ships nor integrates their motion. */
object SableFlightGravity {
    fun isManaged(level: Level): Boolean = FlatPlanetSpace.contains(level)

    private val flatFields = mutableMapOf<Double, SurfaceGravityLayer>()

    /** Native configuration is the surface authority; changing it intentionally changes the mass model. */
    fun flatField(level: ServerLevel): SurfaceGravityLayer {
        val native = DimensionPhysicsData.getGravity(level.server.overworld())
        require(native.x() == 0.0 && native.z() == 0.0 && native.y() < 0.0) { "Flat planet requires vertical downward native gravity" }
        val g = -native.y()
        return flatFields.getOrPut(g) {
            FlatPlanetSpace.gravityField(g)
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
        return Reading(
            position,
            physics.getPhysicsHandle(body).getLinearVelocity(Vector3d()),
            acceleration(body.level, position),
            body.massTracker.mass,
        )
    }

    /** Ship adapter for the finite field; player exterior gravity shares its mass model. */
    fun acceleration(
        level: Level,
        position: Vector3,
    ): Vector3 {
        require(isManaged(level)) { "No Orbitalis construction gravity in this dimension" }
        return flatField(level as ServerLevel).acceleration(globalPosition(level, position))
    }

    /** Split the field around native drift while Sable owns body integration. */
    fun beforePhysics(
        level: ServerLevel,
        seconds: Double,
    ) = applyGravity(level, seconds)

    fun afterPhysics(
        level: ServerLevel,
        seconds: Double,
    ) {
        applyGravity(level, seconds)
    }

    private fun applyGravity(
        level: ServerLevel,
        seconds: Double,
    ) {
        if (!isManaged(level)) return
        require(seconds.isFinite() && seconds > 0.0)
        val container = SubLevelContainer.getContainer(level) ?: return
        val physics = container.physicsSystem() ?: return
        val fraction = 0.5
        val native = DimensionPhysicsData.getGravity(level)
        for (body in container.allSubLevels.filterIsInstance<ServerSubLevel>()) {
            val mass = body.massTracker.mass
            val localCom = body.massTracker.centerOfMass ?: continue
            if (!mass.isFinite() || mass <= 0.0) continue
            val pose = body.logicalPose()
            val com = pose.transformPosition(localCom, Vector3d())
            if (!com.isFinite) continue
            val acceleration = acceleration(level, Vector3(com.x, com.y, com.z))
            // Cancel only native gravity, retaining Sable's integration and every other force.
            val impulse =
                Vector3d(
                    acceleration.x - native.x(),
                    acceleration.y - native.y(),
                    acceleration.z - native.z(),
                ).mul(mass * seconds * fraction)
            // Native surface gravity already matches exactly; do not wake sleeping ships with a zero impulse.
            if (impulse.lengthSquared() == 0.0) continue
            pose.orientation().transformInverse(impulse)
            physics.getPhysicsHandle(body).applyLinearImpulse(impulse)
        }
    }
}
