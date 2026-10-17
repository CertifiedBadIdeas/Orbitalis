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

package org.cbi.orbitalis.minecraft.flight

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.OrbitalTestFrame
import org.cbi.orbitalis.core.planet.SpaceTestFrame
import org.cbi.orbitalis.core.planet.SphericalGravity
import org.cbi.orbitalis.core.planet.Vector3

/** Shared diagnostic geometry for server forces and client reference rendering; no optional mod types. */
object FlightTestSpace {
    val SURFACE: ResourceKey<Level> = key("flight_test")
    val ORBIT: ResourceKey<Level> = key("orbit_test")
    val SPACE: ResourceKey<Level> = key("space_test")
    val CENTRE = Vector3(0.0, -5936.0, 0.0)
    val gravity =
        SphericalGravity(
            radius = 6000.0,
            surfaceAcceleration = 9.81,
        )

    fun contains(level: Level): Boolean = level.dimension() in setOf(SURFACE, ORBIT, SPACE)

    fun globalPosition(
        level: Level,
        position: Vector3,
    ): Vector3 = globalPosition(level.dimension(), position)

    fun globalPosition(
        dimension: ResourceKey<Level>,
        position: Vector3,
    ): Vector3 =
        when (dimension) {
            ORBIT -> OrbitalTestFrame.toSurface(position)
            SPACE -> SpaceTestFrame.toSurface(position)
            else -> position
        }

    fun localPosition(
        dimension: ResourceKey<Level>,
        position: Vector3,
    ): Vector3 =
        when (dimension) {
            ORBIT -> OrbitalTestFrame.toOrbit(position)
            SPACE -> SpaceTestFrame.toSpace(position)
            else -> position
        }

    private fun key(path: String): ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("orbitalis", path))
}
