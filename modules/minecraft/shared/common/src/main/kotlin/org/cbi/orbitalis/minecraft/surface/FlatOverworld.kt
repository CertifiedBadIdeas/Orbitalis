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
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import net.minecraft.world.level.storage.ServerLevelData

/** Native border settings remain authoritative; the marker only records that defaults were installed. */
object FlatOverworld {
    const val SIZE = org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SIZE
    const val DATA_NAME = "orbitalis_flat_surface"

    private class Initialized(
        val version: Int = 2,
    ) : SavedData() {
        override fun save(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): CompoundTag = tag.apply { putInt("Version", version) }
    }

    // Future versions and administrator customizations retain native authority.
    private val factory = SavedData.Factory({ Initialized() }, { tag, _ -> Initialized(tag.getInt("Version")) }, null)

    /** Level-load precedes Minecraft applying saved settings again; keep that later application consistent. */
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
        if (marker != null && marker.version != 1) return
        val border = level.worldBorder
        val legacyDefault =
            marker?.version == 1 && border.centerX == 0.0 && border.centerZ == 0.0 &&
                border.size == 2000.0 && border.lerpTarget == 2000.0 && border.lerpRemainingTime <= 0L
        if (marker == null || legacyDefault) {
            border.setCenter(0.0, 0.0)
            border.size = SIZE
        }
        storage.set(DATA_NAME, Initialized().apply { setDirty() })
    }
}
