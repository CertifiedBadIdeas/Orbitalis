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
import org.cbi.orbitalis.core.orbit.FlightFrame
import org.cbi.orbitalis.core.orbit.OrbitalSector
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

    fun rootState(
        value: CartesianState,
        from: ResourceKey<Level>,
    ): CartesianState = frames.frame(chart(from)).toParent(value)

    fun nativeState(
        value: CartesianState,
        to: ResourceKey<Level>,
    ): CartesianState = frames.frame(chart(to)).fromParent(value)

    fun rootPoint(
        value: Vector3,
        from: ResourceKey<Level>,
    ): Vector3 =
        frames.frame(chart(from)).let {
            it.origin.position +
                it.orientation.apply(value)
        }

    fun nativePoint(
        value: Vector3,
        to: ResourceKey<Level>,
    ): Vector3 =
        frames.frame(chart(to)).let {
            it.orientation.inverse(value - it.origin.position)
        }

    fun rootDirection(
        value: Vector3,
        from: ResourceKey<Level>,
    ): Vector3 = frames.frame(chart(from)).orientation.apply(value)

    fun nativeDirection(
        value: Vector3,
        to: ResourceKey<Level>,
    ): Vector3 = frames.frame(chart(to)).orientation.inverse(value)

    fun rootAngular(
        value: Vector3,
        from: ResourceKey<Level>,
    ): Vector3 = frames.frame(chart(from)).toParentAngularVelocity(value)

    fun nativeAngular(
        value: Vector3,
        to: ResourceKey<Level>,
    ): Vector3 = frames.frame(chart(to)).fromParentAngularVelocity(value)

    fun state(
        value: CartesianState,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): CartesianState = nativeState(rootState(value, from), to)

    fun point(
        value: Vector3,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): Vector3 = nativePoint(rootPoint(value, from), to)

    fun direction(
        value: Vector3,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): Vector3 = nativeDirection(rootDirection(value, from), to)

    fun angular(
        value: Vector3,
        from: ResourceKey<Level>,
        to: ResourceKey<Level>,
    ): Vector3 = nativeAngular(rootAngular(value, from), to)

    fun destination(
        level: ServerLevel,
        state: CartesianState,
    ): ResourceKey<Level>? {
        val current = chart(level.dimension())
        val root = rootState(state, level.dimension())
        if (current is FlightFrame.Surface) {
            val envelope =
                when (current.body) {
                    SolarSystemProfile.EARTH -> {
                        frames.profile.geometry.earthEnvelope
                    }

                    SolarSystemProfile.MOON -> {
                        frames.profile.geometry.moonEnvelope
                    }

                    else -> {
                        frames.profile.innerPlanets
                            .single { it.id == current.body }
                            .envelope
                    }
                }
            if (envelope.distance(state.position) < envelope.ascentDistance) return null
        } else {
            for (body in listOf(SolarSystemProfile.EARTH, SolarSystemProfile.MOON) + frames.profile.innerPlanets.map { it.id }) {
                val surface = FlightFrame.Surface(body)
                val native = frames.frame(surface).fromParent(root)
                val envelope =
                    when (body) {
                        SolarSystemProfile.EARTH -> {
                            frames.profile.geometry.earthEnvelope
                        }

                        SolarSystemProfile.MOON -> {
                            frames.profile.geometry.moonEnvelope
                        }

                        else -> {
                            frames.profile.innerPlanets
                                .single { it.id == body }
                                .envelope
                        }
                    }
                if (envelope.distance(native.position) <= envelope.descentDistance) return dimension(surface)
            }
        }
        val destination = frames.orbitalDestination(root, current)
        return dimension(destination).takeUnless { it == level.dimension() }
    }

    companion object {
        private var clientProfile: SolarSystemProfile? = null
        val clientProfileSnapshot get() = clientProfile
        var innerPlanetSeeds: Map<String, Long> = emptyMap()
            private set
        var lunarSeed = 0L
            private set
        private val clientClock = ClientEphemerisClock()

        fun accept(
            profile: SolarSystemProfile?,
            tick: Long,
            seed: Long = 0,
            advancing: Boolean = true,
            innerSeeds: Map<String, Long> = emptyMap(),
        ) {
            clientProfile = profile
            innerPlanetSeeds = innerSeeds.toMap()
            lunarSeed = seed
            clientClock.accept(tick, advancing)
        }

        fun sample(level: Level): CelestialFlight {
            if (level is ServerLevel) return sample(level.server)
            val profile = checkNotNull(clientProfile) { "Celestial snapshot not received" }
            return CelestialFlight(SurfaceFlightFrames(profile, clientClock.tick))
        }

        fun sampleClient(partialTick: Float): CelestialFlight =
            CelestialFlight(
                SurfaceFlightFrames(checkNotNull(clientProfile), clientClock.tick, clientClock.renderSeconds(partialTick)),
            )

        fun advanceClient() = clientClock.advance()

        fun available(level: Level): Boolean = if (level is ServerLevel) trySample(level.server) != null else clientProfile != null

        val dimensions = listOf(Level.OVERWORLD, FlatPlanetSpace.SPACE, MoonWorld.KEY)

        fun sample(server: MinecraftServer): CelestialFlight =
            checkNotNull(trySample(server)) { "Celestial metadata is unavailable or incompatible" }

        fun trySample(server: MinecraftServer): CelestialFlight? {
            val profile = OrbitalSimulation.profile(server) ?: return null
            val clock = OrbitalSimulation.data(server)?.clock ?: return null
            return CelestialFlight(SurfaceFlightFrames(profile, clock))
        }

        fun chart(key: ResourceKey<Level>): FlightFrame =
            when (key) {
                Level.OVERWORLD -> {
                    FlightFrame.Surface(SolarSystemProfile.EARTH)
                }

                FlatPlanetSpace.SPACE -> {
                    FlightFrame.LegacySpace
                }

                MoonWorld.KEY -> {
                    FlightFrame.Surface(SolarSystemProfile.MOON)
                }

                else -> {
                    val id = key.location()
                    val sector = if (id.namespace == "orbitalis") OrbitalSector.parse(id.path) else null
                    when {
                        sector != null -> FlightFrame.Sector(sector)
                        id.toString() in setOf("orbitalis:mercury", "orbitalis:venus") -> FlightFrame.Surface(id.toString())
                        else -> throw IllegalArgumentException("Unsupported flight dimension: $key")
                    }
                }
            }

        fun supported(key: ResourceKey<Level>): Boolean = runCatching { chart(key) }.isSuccess

        fun dimension(frame: FlightFrame): ResourceKey<Level> =
            when (frame) {
                FlightFrame.LegacySpace -> {
                    FlatPlanetSpace.SPACE
                }

                is FlightFrame.Sector -> {
                    ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation
                            .fromNamespaceAndPath("orbitalis", frame.sector.path),
                    )
                }

                is FlightFrame.Surface -> {
                    when (frame.body) {
                        SolarSystemProfile.EARTH -> {
                            Level.OVERWORLD
                        }

                        SolarSystemProfile.MOON -> {
                            MoonWorld.KEY
                        }

                        else -> {
                            ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                net.minecraft.resources.ResourceLocation
                                    .parse(frame.body),
                            )
                        }
                    }
                }
            }

        fun levels(server: MinecraftServer): List<ServerLevel> = server.allLevels.filter { supported(it.dimension()) }.toList()

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
