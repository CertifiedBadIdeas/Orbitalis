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
import net.minecraft.server.level.progress.ChunkProgressListener
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.storage.DerivedLevelData
import net.minecraft.world.level.storage.ServerLevelData
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.OrbitalSectors
import org.cbi.orbitalis.neoforge.mixin.OrbitalSectorServerAccess

/** Pinned 1.21.1 ServerLevel lifecycle, installed before login and transfer recovery. */
@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object OrbitalSectorEvents {
    @JvmStatic
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun starting(event: ServerStartingEvent) {
        OrbitalSectors.create = ::create
        OrbitalSectors.restore(event.server)
    }

    private fun create(
        server: MinecraftServer,
        key: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
    ): ServerLevel {
        val access = server as OrbitalSectorServerAccess
        val template = checkNotNull(server.getLevel(FlatPlanetSpace.SPACE))
        val listener =
            object : ChunkProgressListener {
                override fun updateSpawnPos(pos: net.minecraft.world.level.ChunkPos) = Unit

                override fun onStatusChange(
                    pos: net.minecraft.world.level.ChunkPos,
                    status: ChunkStatus?,
                ) = Unit

                override fun start() = Unit

                override fun stop() = Unit
            }
        val level =
            ServerLevel(
                server,
                access.orbitalisSectorExecutor(),
                access.orbitalisSectorStorage(),
                DerivedLevelData(server.worldData, server.overworld().levelData as ServerLevelData),
                key,
                LevelStem(template.dimensionTypeRegistration(), template.chunkSource.generator),
                listener,
                false,
                server.worldData.worldGenOptions().seed(),
                emptyList(),
                false,
                null,
            )
        access.orbitalisSectorLevels()[key] = level
        server.markWorldsDirty()
        NeoForge.EVENT_BUS.post(LevelEvent.Load(level))
        return level
    }
}
