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

package org.cbi.orbitalis.minecraft.space

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.storage.LevelResource
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Server-thread model selection; IO workers read only immutable, path-scoped eligibility. */
object SpaceWorlds {
    private val objectFolders = ConcurrentHashMap.newKeySet<Path>()
    private val levels = mutableMapOf<ServerLevel, Boolean>()
    var isShipPlot: (ServerLevel, BlockPos) -> Boolean = { _, _ -> false }

    fun regionFolder(level: ServerLevel): Path =
        DimensionType
            .getStorageFolder(level.dimension(), level.server.getWorldPath(LevelResource.ROOT))
            .resolve("region")
            .toAbsolutePath()
            .normalize()

    fun initialize(level: ServerLevel): Boolean {
        if (!org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
                .isOrbital(level.dimension())
        ) {
            return false
        }
        return levels.getOrPut(level) {
            val folder = regionFolder(level)
            val generator = level.chunkSource.generator is VacuumChunkGenerator
            val data = SpaceWorldData.open(level.dataStorage, generator, folder)
            (generator && data.objects).also { if (it) objectFolders.add(folder) }
        }
    }

    fun objects(level: Level): Boolean = level is ServerLevel && levels[level] == true

    /** Called only by the native region IO worker; never query live level/chunk state here. */
    @JvmStatic
    fun sparseRegion(folder: Path): Boolean = folder.toAbsolutePath().normalize() in objectFolders

    @JvmStatic
    fun allowsBlock(
        level: Level,
        pos: BlockPos,
    ): Boolean = !objects(level) || isShipPlot(level as ServerLevel, pos)

    fun close(server: MinecraftServer) {
        levels.keys.filter { it.server === server }.forEach {
            objectFolders.remove(regionFolder(it))
            levels.remove(it)
        }
    }
}
