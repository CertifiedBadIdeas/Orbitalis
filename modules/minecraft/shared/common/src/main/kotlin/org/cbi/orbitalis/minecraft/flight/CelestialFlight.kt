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

import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.orbit.CelestialDynamics
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.orbit.SurfaceFlightFrames
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation

/** Sample once on the server thread; workers receive only immutable domain values. */
class CelestialFlight(
    val frames: SurfaceFlightFrames,
) {
    val dynamics = CelestialDynamics(frames.profile, frames.tick)

    fun state(
        value: CartesianState,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): CartesianState = frames.state(value, world(from), world(to))

    fun point(
        value: Vector3,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): Vector3 = frames.point(value, world(from), world(to))

    fun direction(
        value: Vector3,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): Vector3 = frames.direction(value, world(from), world(to))

    fun angular(
        value: Vector3,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): Vector3 = frames.angularVelocity(value, world(from), world(to))

    fun destination(
        level: ServerLevel,
        state: CartesianState,
    ): ResourceKey<Level>? = frames.destination(state, world(level.dimension()))?.let(::dimension)

    companion object {
        private var clientProfile: SolarSystemProfile? = null
        var lunarSeed = 0L
            private set
        private var clientAdvancing = true
        private var clientClock = 0L
        private var clientReceivedAt = 0L

        fun accept(
            profile: SolarSystemProfile?,
            tick: Long,
            receivedAt: Long,
            seed: Long = 0,
            advancing: Boolean = true,
        ) {
            clientProfile = profile
            clientAdvancing = advancing
            lunarSeed = seed
            clientClock = tick
            clientReceivedAt = receivedAt
        }

        fun sample(level: Level): CelestialFlight {
            if (level is ServerLevel) return sample(level.server)
            val profile = checkNotNull(clientProfile) { "Celestial snapshot not received" }
            val elapsed = if (clientAdvancing) (level.gameTime - clientReceivedAt).coerceIn(0L, 10L) else 0L
            return CelestialFlight(SurfaceFlightFrames(profile, clientClock + elapsed))
        }

        fun available(level: Level): Boolean = if (level is ServerLevel) trySample(level.server) != null else clientProfile != null

        val dimensions = listOf(Level.OVERWORLD, FlatPlanetSpace.SPACE, MoonWorld.KEY)

        fun sample(server: MinecraftServer): CelestialFlight =
            checkNotNull(trySample(server)) { "Celestial metadata is unavailable or incompatible" }

        fun trySample(server: MinecraftServer): CelestialFlight? {
            val profile = OrbitalSimulation.profile(server) ?: return null
            val clock = OrbitalSimulation.data(server)?.clock ?: return null
            return CelestialFlight(SurfaceFlightFrames(profile, clock))
        }

        fun world(key: ResourceKey<Level>): SurfaceFlightFrames.World =
            when (key) {
                Level.OVERWORLD -> SurfaceFlightFrames.World.EARTH
                FlatPlanetSpace.SPACE -> SurfaceFlightFrames.World.SPACE
                MoonWorld.KEY -> SurfaceFlightFrames.World.MOON
                else -> throw IllegalArgumentException("Unsupported flight dimension: $key")
            }

        fun dimension(world: SurfaceFlightFrames.World): ResourceKey<Level> =
            when (world) {
                SurfaceFlightFrames.World.EARTH -> Level.OVERWORLD
                SurfaceFlightFrames.World.SPACE -> FlatPlanetSpace.SPACE
                SurfaceFlightFrames.World.MOON -> MoonWorld.KEY
            }
    }
}
