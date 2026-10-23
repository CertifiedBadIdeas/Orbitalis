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

package org.cbi.orbitalis.minecraft.integration.sable

import net.minecraft.nbt.CompoundTag

/** Comparison-only handling of NeoForge's lazily allocated optional metadata container. */
internal object NativeBlockEntityMetadata {
    fun forComparison(payload: CompoundTag): CompoundTag =
        if (payload.contains("NeoForgeData", 10) && payload.getCompound("NeoForgeData").isEmpty) {
            payload.copy().also { it.remove("NeoForgeData") }
        } else {
            payload
        }

    fun equivalentPlots(
        source: CompoundTag,
        destination: CompoundTag,
    ): Boolean = source == destination || plotForComparison(source) == plotForComparison(destination)

    private fun plotForComparison(plot: CompoundTag): CompoundTag =
        plot.copy().also { normalized ->
            val chunks = normalized.getCompound("chunks")
            for (key in chunks.allKeys) {
                for (entry in chunks.getCompound(key).getList("block_entities", 10)) {
                    val payload = entry as CompoundTag
                    if (payload.contains("NeoForgeData", 10) && payload.getCompound("NeoForgeData").isEmpty) {
                        payload.remove("NeoForgeData")
                    }
                }
            }
        }
}
