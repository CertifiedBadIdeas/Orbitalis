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

import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

/** Fail closed on unknown/modded state; this classifier never authorizes replacing an existing entry. */
object VacuumChunkData {
    private val rootKeys =
        setOf(
            "DataVersion",
            "xPos",
            "yPos",
            "zPos",
            "LastUpdate",
            "InhabitedTime",
            "Status",
            "sections",
            "isLightOn",
            "Heightmaps",
            "structures",
            "block_entities",
            "entities",
            "block_ticks",
            "fluid_ticks",
            "PostProcessing",
            "CarvingMasks",
            "UpgradeData",
        )
    private val statuses =
        setOf(
            "empty",
            "structure_starts",
            "structure_references",
            "biomes",
            "noise",
            "surface",
            "carvers",
            "features",
            "initialize_light",
            "light",
            "spawn",
            "full",
        )
    private val sectionKeys = setOf("Y", "block_states", "biomes", "BlockLight", "SkyLight")
    private val heightmaps =
        setOf("WORLD_SURFACE_WG", "WORLD_SURFACE", "OCEAN_FLOOR_WG", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES")

    @JvmStatic
    fun regenerable(tag: CompoundTag): Boolean {
        if (tag.allKeys.any { it !in rootKeys }) return false
        if (tag.getInt("DataVersion") != SharedConstants.getCurrentVersion().dataVersion.version) return false
        val status = tag.getString("Status").removePrefix("minecraft:")
        if (status !in statuses) return false
        val pendingBiomes = status in setOf("empty", "structure_starts", "structure_references")
        if (tag.contains("InhabitedTime") &&
            (!tag.contains("InhabitedTime", Tag.TAG_LONG.toInt()) || tag.getLong("InhabitedTime") != 0L)
        ) {
            return false
        }
        for (key in listOf("Heightmaps", "structures")) {
            if (tag.contains(key) && !tag.contains(key, Tag.TAG_COMPOUND.toInt())) return false
        }
        for (key in listOf("block_entities", "entities", "block_ticks", "fluid_ticks", "PostProcessing", "CarvingMasks", "UpgradeData")) {
            if (tag.contains(key) && !empty(tag.get(key))) return false
        }
        if (tag.getCompound("Heightmaps").allKeys.any { it !in heightmaps }) return false
        val structures = tag.getCompound("structures")
        if (structures.allKeys.any { it != "starts" && it != "References" }) return false
        if (structures.allKeys.any { !empty(structures.get(it)) }) return false
        val sections = tag.get("sections")
        if (sections !is ListTag) return false
        for (element in sections) {
            if (element !is CompoundTag || element.allKeys.any { it !in sectionKeys }) return false
            if (element.contains("BlockLight") &&
                (!element.contains("BlockLight", Tag.TAG_BYTE_ARRAY.toInt()) || element.getByteArray("BlockLight").any { it != 0.toByte() })
            ) {
                return false
            }
            if (element.contains("block_states") && !air(element.getCompound("block_states"))) return false
            if (element.contains("biomes") && !voidBiome(element.getCompound("biomes"), pendingBiomes)) return false
        }
        return true
    }

    private fun air(states: CompoundTag): Boolean {
        if (states.allKeys.any { it != "palette" && it != "data" } || !emptyPaletteData(states)) return false
        val palette = states.getList("palette", Tag.TAG_COMPOUND.toInt())
        if (palette.size != 1) return false
        val state = palette.getCompound(0)
        return state.allKeys == setOf("Name") && state.getString("Name") == "minecraft:air"
    }

    private fun voidBiome(
        biomes: CompoundTag,
        pending: Boolean,
    ): Boolean {
        if (biomes.allKeys.any { it != "palette" && it != "data" } || !emptyPaletteData(biomes)) return false
        val palette = biomes.getList("palette", Tag.TAG_STRING.toInt())
        return palette.size == 1 &&
            (palette.getString(0) == "minecraft:the_void" || (pending && palette.getString(0) == "minecraft:plains"))
    }

    private fun emptyPaletteData(tag: CompoundTag): Boolean =
        !tag.contains("data") || (tag.contains("data", Tag.TAG_LONG_ARRAY.toInt()) && tag.getLongArray("data").isEmpty())

    private fun empty(tag: Tag?): Boolean =
        when (tag) {
            is CompoundTag -> tag.isEmpty
            is ListTag -> tag.all(::empty)
            else -> false
        }
}
