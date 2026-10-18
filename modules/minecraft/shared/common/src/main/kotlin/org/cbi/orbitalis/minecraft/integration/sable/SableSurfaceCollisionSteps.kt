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
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.MinecraftServer
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.joml.Vector3d
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.max

/** Bound travel per native contact step near terrain; Sable remains the collision/integration owner. */
object SableSurfaceCollisionSteps {
    private data class Lease(
        val original: Int,
        val applied: Int,
    )

    private val leases = IdentityHashMap<SubLevelPhysicsSystem, Lease>()

    /** Call before the entire native tick, never from a per-substep callback. */
    fun begin(server: MinecraftServer) {
        end(server)
        for (key in CelestialFlight.dimensions) {
            val level = server.getLevel(key) ?: continue
            if (DimensionPhysicsData.of(level).ignoreChunks()) continue
            val container = SubLevelContainer.getContainer(level) ?: continue
            val physics = container.physicsSystem() ?: continue
            if (physics.paused) continue
            val baseline = physics.config.substepsPerTick
            var required = baseline
            for (body in container.allSubLevels.filterIsInstance<ServerSubLevel>()) {
                if (body.isRemoved || body.massTracker.centerOfMass == null) continue
                val handle = physics.getPhysicsHandle(body)
                val speed = handle.getLinearVelocity(Vector3d()).length()
                val bounds = body.boundingBox()
                // Terrain cannot exist outside native build heights. Arm before the swept hull enters.
                val reach = speed * 0.05 + 16.0
                if (bounds.minY() > level.maxBuildHeight + reach || bounds.maxY() < level.minBuildHeight - reach) continue
                val radius =
                    Vector3d(bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ()).length() * 0.5
                val sweepSpeed = speed + handle.getAngularVelocity(Vector3d()).length() * radius
                if (!sweepSpeed.isFinite()) continue
                required = max(required, ceil(sweepSpeed * 0.05 / 0.25).toInt().coerceIn(1, 64))
            }
            if (required > baseline) {
                leases[physics] = Lease(baseline, required)
                physics.config.substepsPerTick = required
            }
        }
    }

    /** Restore only our temporary value; a native/user configuration change keeps its authority. */
    fun end(server: MinecraftServer) {
        val iterator = leases.entries.iterator()
        while (iterator.hasNext()) {
            val (physics, lease) = iterator.next()
            if (physics.level.server !== server) continue
            if (physics.config.substepsPerTick == lease.applied) physics.config.substepsPerTick = lease.original
            iterator.remove()
        }
    }
}
