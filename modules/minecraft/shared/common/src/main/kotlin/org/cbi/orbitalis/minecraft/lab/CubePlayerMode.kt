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

package org.cbi.orbitalis.minecraft.lab

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/** Server-owned scene membership. Native entity data replicates the boolean to clients. */
interface CubePlayerMode {
    fun orbitalisCubeEnrolled(): Boolean

    fun orbitalisSetCubeEnrolled(enabled: Boolean)
}

/** Optional v1 player record; absent, malformed and unknown versions decode disabled. */
object CubePlayerModeData {
    const val KEY = "orbitalis:cube_player"

    @JvmStatic
    fun read(root: CompoundTag): Boolean {
        if (!root.contains(KEY, Tag.TAG_COMPOUND.toInt())) return false
        val record = root.getCompound(KEY)
        return record.contains("version", Tag.TAG_INT.toInt()) && record.getInt("version") == 1 &&
            record.contains("enabled", Tag.TAG_BYTE.toInt()) && record.getByte("enabled").toInt() == 1
    }

    @JvmStatic
    fun write(
        root: CompoundTag,
        enabled: Boolean,
    ) {
        val record = CompoundTag()
        record.putInt("version", 1)
        record.putBoolean("enabled", enabled)
        root.put(KEY, record)
    }
}
