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

package org.cbi.orbitalis.minecraft.surface

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import net.minecraft.world.level.storage.ServerLevelData
import kotlin.math.ceil
import kotlin.math.floor

/** Persisted terrain extent is independent of the native administrative movement border. */
object FlatOverworld {
    const val SIZE = org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SIZE
    const val DATA_NAME = "orbitalis_flat_surface"
    private val defaultBounds = (SIZE / 2.0).toInt().let { FiniteSurfaceTerrain.Bounds(-it, -it, it, it) }

    private class Initialized(
        val version: Int = 3,
        val bounds: FiniteSurfaceTerrain.Bounds? = defaultBounds,
    ) : SavedData() {
        override fun save(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): CompoundTag =
            tag.apply {
                putInt("Version", version)
                bounds?.let { putIntArray("Bounds", intArrayOf(it.minX, it.minZ, it.maxX, it.maxZ)) }
            }
    }

    private val factory =
        SavedData.Factory(
            { Initialized() },
            { tag, _ ->
                val raw = if (tag.contains("Bounds", Tag.TAG_INT_ARRAY.toInt())) tag.getIntArray("Bounds") else intArrayOf()
                val bounds =
                    if (raw.size == 4 && raw.all { it in -30000000..30000000 } && raw[0] <= raw[2] && raw[1] <= raw[3]) {
                        FiniteSurfaceTerrain.Bounds(raw[0], raw[1], raw[2], raw[3])
                    } else {
                        null
                    }
                Initialized(tag.getInt("Version"), bounds)
            },
            null,
        )

    fun footprint(
        level: ServerLevel,
        storage: DimensionDataStorage = level.dataStorage,
    ): FiniteSurfaceTerrain.Bounds {
        val data = storage.get(factory, DATA_NAME)
        return if (data?.version == 3) data.bounds ?: defaultBounds else defaultBounds
    }

    /** Native startup reapplies saved settings later; keep that application consistent. */
    fun prepare(level: ServerLevel) {
        val saved = level.levelData as ServerLevelData
        level.worldBorder.applySettings(saved.worldBorder)
        initialize(level)
        saved.worldBorder = level.worldBorder.createSettings()
        FiniteSurfaceTerrain.attach(level)
    }

    fun initialize(
        level: ServerLevel,
        storage: DimensionDataStorage = level.dataStorage,
    ) {
        require(level.dimension() == Level.OVERWORLD)
        val marker = storage.get(factory, DATA_NAME)
        // Future or malformed records are read-only: use bounded fallback without changing native settings.
        if (marker != null && marker.version !in 1..2) return
        val border = level.worldBorder
        val stationaryDefault =
            border.centerX == 0.0 && border.centerZ == 0.0 &&
                border.size == border.lerpTarget && border.lerpRemainingTime <= 0L &&
                (border.size == SIZE || (marker?.version == 1 && border.size == 2000.0))
        val bounds =
            if (marker == null || stationaryDefault) {
                val half =
                    (
                        org.cbi.orbitalis.minecraft.world.WorldSystemSettings
                            .get(level.server)
                            ?.earthWidth ?: SIZE.toInt()
                    ) / 2
                FiniteSurfaceTerrain.Bounds(-half, -half, half, half)
            } else {
                FiniteSurfaceTerrain.Bounds(
                    ceil(border.minX).toInt(),
                    ceil(border.minZ).toInt(),
                    floor(border.maxX).toInt(),
                    floor(border.maxZ).toInt(),
                )
            }
        if (marker == null || stationaryDefault) {
            border.setCenter(0.0, 0.0)
            border.size = WorldBorder.MAX_SIZE
        }
        storage.set(DATA_NAME, Initialized(bounds = bounds).apply { setDirty() })
    }
}
