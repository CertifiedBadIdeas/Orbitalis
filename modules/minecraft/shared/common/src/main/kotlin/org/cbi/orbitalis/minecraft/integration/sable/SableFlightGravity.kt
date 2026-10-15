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
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.SphericalGravity
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Vector3d

/** Dimension-scoped force adapter. It neither owns ships nor integrates their motion. */
object SableFlightGravity {
    val DIMENSION: ResourceKey<Level> =
        ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("orbitalis", "flight_test"),
        )
    val CENTRE = Vector3(0.0, -5936.0, 0.0)
    val field = SphericalGravity(6000.0, 9.81)

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
            field.acceleration(position - CENTRE),
            body.massTracker.mass,
        )
    }

    /** Called on the server at each native physics substep, also for newly assembled/reloaded ships. */
    fun beforePhysics(
        level: ServerLevel,
        seconds: Double,
    ) {
        if (level.dimension() != DIMENSION) return
        require(seconds.isFinite() && seconds > 0.0)
        val container = SubLevelContainer.getContainer(level) ?: return
        val physics = container.physicsSystem() ?: return
        for (body in container.allSubLevels.filterIsInstance<ServerSubLevel>()) {
            val mass = body.massTracker.mass
            val localCom = body.massTracker.centerOfMass ?: continue
            if (!mass.isFinite() || mass <= 0.0) continue
            val pose = body.logicalPose()
            val com = pose.transformPosition(localCom, Vector3d())
            if (!com.isFinite) continue
            val acceleration = field.acceleration(Vector3(com.x, com.y, com.z) - CENTRE)
            val impulse = Vector3d(acceleration.x, acceleration.y, acceleration.z).mul(mass * seconds)
            pose.orientation().transformInverse(impulse)
            physics.getPhysicsHandle(body).applyLinearImpulse(impulse)
        }
    }
}
