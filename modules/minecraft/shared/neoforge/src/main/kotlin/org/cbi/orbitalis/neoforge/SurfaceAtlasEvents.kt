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

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.surface.SurfaceAtlasService

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object SurfaceAtlasEvents {
    private val services = mutableMapOf<MinecraftServer, SurfaceAtlasService>()

    private fun service(server: MinecraftServer) =
        services.getOrPut(server) {
            SurfaceAtlasService(server) { player, payload -> PacketDistributor.sendToPlayer(player, payload) }
        }

    @JvmStatic @SubscribeEvent
    fun load(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        if (level.dimension() != Level.OVERWORLD) return
        val pos = event.chunk.pos
        level.server.execute { service(level.server).loaded(pos.x, pos.z) }
    }

    @JvmStatic @SubscribeEvent
    fun unload(event: ChunkEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        if (level.dimension() != Level.OVERWORLD) return
        val chunk = event.chunk as? LevelChunk ?: return
        // Native chunk unload is on the server thread, while full block data is still available.
        if (!level.server.isSameThread) return
        services[level.server]?.unloaded(chunk)
    }

    @JvmStatic @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        services[event.server]?.tick()
    }

    @JvmStatic @SubscribeEvent
    fun logout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val level = event.entity.level() as? ServerLevel ?: return
        services[level.server]?.logout(event.entity.uuid)
    }

    @JvmStatic @SubscribeEvent
    fun stop(event: ServerStoppedEvent) {
        services.remove(event.server)
    }
}
