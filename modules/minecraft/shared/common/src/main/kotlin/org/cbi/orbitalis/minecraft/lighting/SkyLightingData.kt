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

package org.cbi.orbitalis.minecraft.lighting

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/** World-owned Overworld sky mode; absent data defaults to native Minecraft daylight. */
class SkyLightingData private constructor(
    private val opaque: CompoundTag? = null,
) : SavedData() {
    var cosmic = false
        private set
    val writable get() = opaque == null

    fun select(value: Boolean) {
        require(writable) { "Unsupported sky lighting data; original record preserved" }
        if (cosmic != value) {
            cosmic = value
            setDirty()
        }
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag {
        opaque?.let { return it.copy() }
        tag.putInt("Version", 1)
        tag.putString("Overworld", if (cosmic) "cosmic" else "vanilla")
        return tag
    }

    companion object {
        var clientCosmic = false
        val FACTORY = Factory({ SkyLightingData() }, ::load, null)

        fun get(server: MinecraftServer): SkyLightingData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, "orbitalis_sky_lighting")

        fun load(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): SkyLightingData {
            if (!tag.contains("Version", 3) || tag.getInt("Version") != 1 ||
                !tag.contains("Overworld", 8) || tag.getString("Overworld") !in setOf("vanilla", "cosmic")
            ) {
                return SkyLightingData(tag.copy())
            }
            return SkyLightingData().apply { cosmic = tag.getString("Overworld") == "cosmic" }
        }
    }
}
