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

import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerLevel
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Vector3d

/** Native ambient forces sampled without changing Sable's dimension policy or actuators. */
internal object FlightEnvironment {
    fun linearDrag(level: ServerLevel): Double = DimensionPhysicsData.getUniversalDrag(level)

    fun nativeDrag(body: ServerSubLevel): Vector3 {
        val velocity =
            SubLevelPhysicsSystem
                .require(body.level)
                .getPhysicsHandle(body)
                .getLinearVelocity(Vector3d())
        return Vector3(velocity.x, velocity.y, velocity.z) * -linearDrag(body.level)
    }
}
