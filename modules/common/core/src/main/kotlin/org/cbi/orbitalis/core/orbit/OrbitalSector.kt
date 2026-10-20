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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.floor

/** Immutable heliocentric grid address. Native coordinates remain independent of astronomical distance. */
data class OrbitalSector(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    init {
        require(listOf(x, y, z).all { it in -LIMIT..LIMIT })
    }

    val path: String get() = "sector/${x}_${y}_$z"
    val origin: Vector3 get() = Vector3(x * WIDTH, y * WIDTH, z * WIDTH)

    /** +Y offset keeps the complete working volume above Minecraft's surface-oriented void floor. */
    fun frame(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double = 0.0,
    ): FrameKinematics {
        val earth = profile.earthOrbit.sample(tick, seconds)
        return FrameKinematics(
            CartesianState(origin - earth.origin.position - NATIVE_CENTRE, earth.origin.velocity * -1.0),
            originAcceleration = earth.originAcceleration * -1.0,
        )
    }

    fun retains(heliocentric: Vector3): Boolean {
        val delta = heliocentric - origin
        return listOf(delta.x, delta.y, delta.z).all { abs(it) <= WIDTH * 0.5 + HYSTERESIS }
    }

    companion object {
        const val WIDTH = 1048576.0
        const val HYSTERESIS = 32768.0
        const val LIMIT = 1000000
        val NATIVE_CENTRE = Vector3(0.0, WIDTH, 0.0)
        private val pattern = Regex("sector/(-?[0-9]+)_(-?[0-9]+)_(-?[0-9]+)")

        fun containing(heliocentric: Vector3): OrbitalSector {
            fun coordinate(value: Double): Int {
                require(value.isFinite())
                val index = floor(value / WIDTH + 0.5)
                require(index >= -LIMIT && index <= LIMIT)
                return index.toInt()
            }
            return OrbitalSector(coordinate(heliocentric.x), coordinate(heliocentric.y), coordinate(heliocentric.z))
        }

        /** Accept canonical addresses only; aliases must never open two directories for one chart. */
        fun parse(path: String): OrbitalSector? {
            val match = pattern.matchEntire(path) ?: return null
            val coordinates = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            if (coordinates.any { it !in -LIMIT..LIMIT }) return null
            return OrbitalSector(coordinates[0], coordinates[1], coordinates[2]).takeIf { it.path == path }
        }
    }
}

/** Serializable identity is separate from the time-dependent rigid transform sampled for travel. */
sealed interface FlightFrame {
    data class Surface(
        val body: String,
    ) : FlightFrame

    data object LegacySpace : FlightFrame

    data class Sector(
        val sector: OrbitalSector,
    ) : FlightFrame
}
