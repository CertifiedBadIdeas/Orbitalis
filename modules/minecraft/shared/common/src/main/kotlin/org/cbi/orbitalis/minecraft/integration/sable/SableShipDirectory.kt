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
import net.minecraft.server.MinecraftServer
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.moon.MoonWorld

/** One native loaded-body enumeration for map snapshots and flight-control lookup. */
object SableShipDirectory {
    fun loaded(server: MinecraftServer): List<ServerSubLevel> {
        val sample = CelestialFlight.trySample(server)
        return CelestialFlight.levels(server).filter { sample != null || it.dimension() != MoonWorld.KEY }.flatMap { world ->
            world
                ?.let { level ->
                    SubLevelContainer
                        .getContainer(level)
                        ?.allSubLevels
                        ?.filterIsInstance<ServerSubLevel>()
                        ?.filter { !it.isRemoved }
                }.orEmpty()
        }
    }
}
