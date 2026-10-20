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

package org.cbi.orbitalis.minecraft.orbit

import net.minecraft.server.MinecraftServer
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace

/** Server-thread simulation metadata access. No terrain, native payloads or construction wake. */
object OrbitalSimulation {
    var mayAdvance: (MinecraftServer) -> Boolean = { true }
    var canAccelerate: (MinecraftServer) -> Boolean = { true }
    var surfaceGravity: (MinecraftServer) -> Double = { 11.0 }

    fun data(server: MinecraftServer): OrbitalObjectData? =
        server
            .getLevel(FlatPlanetSpace.SPACE)
            ?.dataStorage
            ?.computeIfAbsent(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME)

    fun profile(server: MinecraftServer): SolarSystemProfile? =
        data(server)
            ?.takeIf { it.readable }
            ?.configureCelestials(
                surfaceGravity(server),
                org.cbi.orbitalis.minecraft.world.WorldSystemSettings
                    .get(server)
                    ?.earthWidth,
            )

    fun tick(server: MinecraftServer) {
        val data = data(server) ?: return
        if (!data.readable || !mayAdvance(server)) return
        data.configureCelestials(
            surfaceGravity(server),
            org.cbi.orbitalis.minecraft.world.WorldSystemSettings
                .get(server)
                ?.earthWidth,
        )
        data.advanceClock()
    }
}
