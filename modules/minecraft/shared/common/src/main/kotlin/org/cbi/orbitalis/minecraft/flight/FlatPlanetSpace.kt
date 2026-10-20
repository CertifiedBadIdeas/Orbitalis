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
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.surface.FlatOverworld

/** Native surface/orbital membership and physical travel bounds. */
object FlatPlanetSpace {
    val SPACE: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("orbitalis", "space"))
    const val SEA_LEVEL = FlatPlanetDefinition.SEA_LEVEL
    const val HALF_SIZE = FlatOverworld.SIZE / 2.0
    const val LAUNCH_LIMIT = HALF_SIZE - 40.0
    const val ASCENT_HEIGHT = FlatPlanetDefinition.ASCENT_HEIGHT
    const val DESCENT_HEIGHT = FlatPlanetDefinition.DESCENT_HEIGHT
    const val ORBIT_HEIGHT = FlatPlanetDefinition.ORBIT_HEIGHT
    const val MAX_TRANSFER_DISTANCE = 100000.0

    fun transferRange(level: Level): Double =
        if (CelestialFlight.available(level)) {
            CelestialFlight
                .sample(level)
                .frames.profile.flightRange
        } else {
            MAX_TRANSFER_DISTANCE
        }

    fun validTransferPosition(
        position: Vector3,
        profile: org.cbi.orbitalis.core.orbit.SolarSystemProfile? = null,
    ): Boolean = position.length().isFinite() && position.length() <= 2.0e9

    fun gravityField(
        surfaceGravity: Double,
        width: Double = FlatPlanetDefinition.SIZE,
    ): SurfaceGravityLayer = FlatPlanetDefinition.gravity(surfaceGravity, width)

    fun contains(level: Level): Boolean = CelestialFlight.supported(level.dimension())
}

/** Geocentric nonrotating root coordinates; native surface charts rotate with their bodies. */
object FlightSpaceCoordinates {
    fun isOrbital(dimension: ResourceKey<Level>): Boolean =
        dimension == FlatPlanetSpace.SPACE ||
            (
                dimension.location().namespace == "orbitalis" &&
                    org.cbi.orbitalis.core.orbit.OrbitalSector
                        .parse(dimension.location().path) != null
            )

    fun hasIndependentBorder(level: Level): Boolean = level.dimension() != Level.OVERWORLD && CelestialFlight.supported(level.dimension())

    fun globalPosition(
        level: Level,
        position: Vector3,
    ): Vector3 =
        if (CelestialFlight.available(level)) {
            CelestialFlight.sample(level).rootPoint(position, level.dimension())
        } else {
            legacyRootPosition(level.dimension(), position)
        }

    fun localPosition(
        level: Level,
        position: Vector3,
    ): Vector3 =
        if (CelestialFlight.available(level)) {
            CelestialFlight.sample(level).nativePoint(position, level.dimension())
        } else {
            legacyLocalPosition(level.dimension(), position)
        }

    @Deprecated("Translation-only legacy contract; sample CelestialFlight or supply a Level")
    fun globalPosition(
        dimension: ResourceKey<Level>,
        position: Vector3,
    ): Vector3 = legacyRootPosition(dimension, position)

    @Deprecated("Translation-only legacy contract; sample CelestialFlight or supply a Level")
    fun localPosition(
        dimension: ResourceKey<Level>,
        position: Vector3,
    ): Vector3 = legacyLocalPosition(dimension, position)

    fun legacyRootPosition(
        dimension: ResourceKey<Level>,
        position: Vector3,
    ): Vector3 =
        when (dimension) {
            Level.OVERWORLD -> position
            FlatPlanetSpace.SPACE -> FlatPlanetFrame.toSurface(position)
            else -> throw IllegalArgumentException("Unsupported flight dimension: ${dimension.location()}")
        }

    fun legacyLocalPosition(
        dimension: ResourceKey<Level>,
        position: Vector3,
    ): Vector3 =
        when (dimension) {
            Level.OVERWORLD -> position
            FlatPlanetSpace.SPACE -> FlatPlanetFrame.toSpace(position)
            else -> throw IllegalArgumentException("Unsupported flight dimension: ${dimension.location()}")
        }
}
