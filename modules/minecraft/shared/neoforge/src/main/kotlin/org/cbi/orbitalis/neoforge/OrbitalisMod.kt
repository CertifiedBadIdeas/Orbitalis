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

package org.cbi.orbitalis.neoforge

import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.registries.DeferredRegister
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
import org.cbi.orbitalis.minecraft.moon.LunarChunkGenerator
import org.cbi.orbitalis.minecraft.space.VacuumChunkGenerator
import org.cbi.orbitalis.neoforge.integration.SableFlightIntegration

/** Loader entry point. Gameplay belongs in shared/common or the independent core. */
@Mod(Orbitalis.MOD_ID)
class OrbitalisMod(
    modBus: IEventBus,
) {
    init {
        val generators = DeferredRegister.create(Registries.CHUNK_GENERATOR, Orbitalis.MOD_ID)
        generators.register("vacuum", java.util.function.Supplier { VacuumChunkGenerator.CODEC })
        generators.register("lunar_surface", java.util.function.Supplier { LunarChunkGenerator.CODEC })
        generators.register(modBus)
        NeoForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedOutEvent ->
            (event.entity as? ServerPlayer)?.let { PlayerSpaceTravel.close(it) }
        }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppedEvent ->
            PlayerSpaceTravel.close(event.server)
        }

        if (ModList.get().isLoaded("sable")) {
            SableFlightIntegration.install()
        } else {
            NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post ->
                event.server.playerList.players
                    .forEach {
                        PlayerSpaceTravel
                            .transfer(it)
                    }
            }
        }
    }
}
