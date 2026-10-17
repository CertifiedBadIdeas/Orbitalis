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

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import java.nio.file.Files
import java.nio.file.Path

/** A conservative persisted choice: old/unknown terrain is never silently made non-colliding. */
class SpaceWorldData private constructor(
    private val payload: CompoundTag,
) : SavedData() {
    val objects: Boolean get() = payload.getInt("Version") == 1 && payload.getString("Model") == "objects"

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag = tag.merge(payload.copy())

    companion object {
        const val NAME = "orbitalis_space_world"

        fun open(
            storage: DimensionDataStorage,
            vacuumGenerator: Boolean,
            regionFolder: Path,
        ): SpaceWorldData {
            // Native reads may create header-only files. Any possible payload stays conservative.
            val existing =
                Files.isDirectory(regionFolder) &&
                    Files.newDirectoryStream(regionFolder, "r.*.*.mca").use { files ->
                        files.any { Files.size(it) > 8192 }
                    }
            val factory =
                Factory(
                    { create(vacuumGenerator && !existing) },
                    { tag, _ -> load(tag) },
                    null,
                )
            return storage.computeIfAbsent(factory, NAME)
        }

        fun create(objects: Boolean): SpaceWorldData =
            SpaceWorldData(
                CompoundTag().apply {
                    putInt("Version", 1)
                    putString("Model", if (objects) "objects" else "legacy_terrain")
                },
            ).apply { setDirty() }

        fun load(tag: CompoundTag): SpaceWorldData = SpaceWorldData(tag.copy())
    }
}
