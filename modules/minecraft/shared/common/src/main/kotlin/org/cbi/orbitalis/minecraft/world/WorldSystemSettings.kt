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

package org.cbi.orbitalis.minecraft.world

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer

/** Creation-time selection. Once saved, all consumers derive their physical system from this value. */
data class WorldSystemSettings(
    val earthWidth: Int = DEFAULT_WIDTH,
) {
    init {
        require(earthWidth in MIN_WIDTH..MAX_WIDTH) { "Earth width must be between 1000 and 20000 blocks" }
    }

    fun encode(): CompoundTag =
        CompoundTag().apply {
            putInt("Version", VERSION)
            putInt("EarthWidth", earthWidth)
        }

    companion object {
        const val KEY = "OrbitalisWorldSettings"
        const val VERSION = 1
        const val DEFAULT_WIDTH = 10000
        const val MIN_WIDTH = 1000
        const val MAX_WIDTH = 20000
        val PRESETS = listOf(1000, 2000, 5000, 10000, 20000)

        /** Null means a legacy save; unsupported or malformed settings must never silently resize it. */
        @JvmStatic
        fun decode(tag: Tag?): WorldSystemSettings? {
            if (tag == null) return null
            require(tag is CompoundTag) { "Invalid Orbitalis world settings tag" }
            require(tag.contains("Version", Tag.TAG_INT.toInt()) && tag.getInt("Version") == VERSION) {
                "Unsupported Orbitalis world settings version"
            }
            require(tag.contains("EarthWidth", Tag.TAG_INT.toInt())) { "Missing Earth width" }
            return WorldSystemSettings(tag.getInt("EarthWidth"))
        }

        @JvmStatic
        fun get(server: MinecraftServer): WorldSystemSettings? {
            val settings: Any = server.worldData.levelSettings
            return decode((settings as WorldSystemSettingsOwner).orbitalisWorldSettings)
        }
    }
}

/** Implemented by the native LevelSettings mixin; retain opaque data to preserve unknown versions. */
interface WorldSystemSettingsOwner {
    var orbitalisWorldSettings: Tag?
}

/** State belongs to one creation screen, including its tab rebuilds and child dialogs. */
interface PlanetSizeSelection {
    var orbitalisEarthWidth: Int
}
