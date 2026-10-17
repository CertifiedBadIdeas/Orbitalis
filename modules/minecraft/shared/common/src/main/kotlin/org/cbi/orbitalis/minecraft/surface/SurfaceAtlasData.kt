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
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.saveddata.SavedData
import org.cbi.orbitalis.core.planet.SurfaceAtlas

/** Versioned, disposable presentation cache. Unknown versions stay read-only, never rewritten. */
class SurfaceAtlasData(
    val atlas: SurfaceAtlas = SurfaceAtlas(),
    val supported: Boolean = true,
) : SavedData() {
    fun capture(
        level: ServerLevel,
        chunk: LevelChunk,
    ): Boolean {
        if (!supported || level.dimension() != Level.OVERWORLD || chunk.level !== level) return false
        val cx = chunk.pos.x
        val cz = chunk.pos.z
        if (cx !in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK || cz !in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK) return false
        val value =
            SurfaceAtlasSampler.sample(
                level,
                cx,
                cz,
                { x, z -> chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) },
                chunk::getBlockState,
                { x, y, z -> chunk.getNoiseBiome(x shr 2, y shr 2, z shr 2).value() },
            )
        return record(value)
    }

    fun record(value: SurfaceAtlas.Sample): Boolean {
        if (!supported) return false
        if (!atlas.put(value)) return false
        setDirty()
        return true
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag =
        tag.apply {
            check(supported) { "Unsupported surface atlas is read-only" }
            putInt("Version", 1)
            val records = IntArray(atlas.size * 4)
            atlas.all().forEachIndexed { index, sample ->
                val i = index * 4
                records[i] = sample.x
                records[i + 1] = sample.z
                records[i + 2] = sample.height
                records[i + 3] = sample.color
            }
            putIntArray("Samples", records)
        }

    companion object {
        const val DATA_NAME = "orbitalis_surface_atlas"

        fun load(tag: CompoundTag): SurfaceAtlasData {
            if (tag.getInt("Version") != 1) return SurfaceAtlasData(supported = false)
            val result = SurfaceAtlasData()
            val records = tag.getIntArray("Samples")
            if (records.size > SurfaceAtlas.MAX_SAMPLES * 4 || records.size % 4 != 0) return result
            for (i in records.indices step 4) {
                try {
                    result.atlas.put(SurfaceAtlas.Sample(records[i], records[i + 1], records[i + 2], records[i + 3]))
                } catch (
                    _: IllegalArgumentException,
                ) {
                    // A malformed cache record remains unknown.
                }
            }
            return result
        }

        private val FACTORY = SavedData.Factory({ SurfaceAtlasData() }, { tag, _ -> load(tag) }, null)

        fun get(level: ServerLevel): SurfaceAtlasData {
            require(level.dimension() == Level.OVERWORLD)
            return level.dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
        }
    }
}
