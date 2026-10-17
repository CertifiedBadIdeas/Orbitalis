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

/** Native border settings remain authoritative; the marker only records that defaults were installed. */
object FlatOverworld {
    const val SIZE = 2000.0
    const val DATA_NAME = "orbitalis_flat_surface"

    private class Initialized : SavedData() {
        override fun save(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): CompoundTag = tag.apply { putInt("Version", 1) }
    }

    // Any existing marker, including a future version, prevents rewriting native border settings.
    private val factory = SavedData.Factory(::Initialized, { _, _ -> Initialized() }, null)

    fun initialize(
        level: ServerLevel,
        storage: DimensionDataStorage = level.dataStorage,
    ) {
        require(level.dimension() == Level.OVERWORLD)
        if (storage.get(factory, DATA_NAME) != null) return
        level.worldBorder.setCenter(0.0, 0.0)
        level.worldBorder.size = SIZE
        storage.set(DATA_NAME, Initialized().apply { setDirty() })
    }
}
