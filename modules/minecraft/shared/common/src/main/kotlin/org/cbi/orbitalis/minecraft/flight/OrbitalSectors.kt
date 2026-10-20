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

package org.cbi.orbitalis.minecraft.flight

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import org.cbi.orbitalis.core.orbit.OrbitalSector

/** Server-thread-only catalogue of native charts; save directories and native body storage stay independent. */
object OrbitalSectors {
    private val materializing = mutableSetOf<Pair<MinecraftServer, ResourceKey<Level>>>()
    var create: (MinecraftServer, ResourceKey<Level>) -> ServerLevel = { _, _ -> error("Orbital sector adapter unavailable") }

    fun sector(key: ResourceKey<Level>): OrbitalSector? =
        key.location().let {
            if (it.namespace == "orbitalis") OrbitalSector.parse(it.path) else null
        }

    fun ensure(
        server: MinecraftServer,
        key: ResourceKey<Level>,
    ): ServerLevel? {
        check(server.isSameThread) { "Sector creation belongs to the server thread" }
        server.getLevel(key)?.let { return it }
        if (sector(key) == null) return null
        val data = Catalogue.open(server)
        check(data.compatible) { "Unknown or malformed orbital sector catalogue; preserved unchanged" }
        check(key.location().toString() in data.keys || data.keys.size < MAX_SECTORS) { "Orbital sector catalogue limit reached" }
        // This is an asynchronous index; a durable canonical player/journal key can rebuild it.
        if (data.keys.add(key.location().toString())) {
            data.setDirty()
            server.overworld().dataStorage.save()
        }
        check(
            server.allLevels.count {
                sector(it.dimension()) != null
            } < MAX_LOADED_SECTORS,
        ) { "Loaded orbital sector limit reached; source authority retained" }
        return create(server, key)
    }

    fun restore(server: MinecraftServer) {
        check(server.isSameThread)
        val data = Catalogue.open(server)
        check(data.compatible) { "Unknown or malformed orbital sector catalogue; preserved unchanged" }
        // Native worlds remain cold until a player, ship recovery or simulation explicitly needs one.
    }

    /** Native getLevel fallback for persisted player dimensions and transfer recovery, never an IO-worker hook. */
    @JvmStatic
    fun lookup(
        server: MinecraftServer,
        key: ResourceKey<Level>,
    ): ServerLevel? {
        if (!server.isSameThread || sector(key) == null) return null
        val token = server to key
        if (token in materializing) return null
        val data = Catalogue.open(server)
        check(data.compatible) { "Unknown or malformed orbital sector catalogue; preserved unchanged" }
        materializing.add(token)
        return try {
            ensure(server, key)
        } finally {
            materializing.remove(token)
        }
    }

    class Catalogue private constructor(
        private val original: CompoundTag,
        val keys: MutableSet<String>,
        val compatible: Boolean,
    ) : SavedData() {
        override fun save(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): CompoundTag {
            if (!compatible) return tag.merge(original.copy())
            tag.putInt("Version", 1)
            tag.put("Sectors", ListTag().also { list -> keys.sorted().forEach { list.add(StringTag.valueOf(it)) } })
            return tag
        }

        companion object {
            fun load(tag: CompoundTag): Catalogue {
                val list = tag.getList("Sectors", 8)
                val keys = list.map { it.asString }.toMutableSet()
                val valid =
                    tag.getInt("Version") == 1 && tag.contains("Sectors", 9) &&
                        (tag.get("Sectors") as? ListTag)?.size == list.size && list.size <= MAX_SECTORS &&
                        keys.size == list.size &&
                        keys.all {
                            val id = ResourceLocation.tryParse(it)
                            id != null && id.namespace == "orbitalis" && OrbitalSector.parse(id.path) != null
                        }
                return Catalogue(tag.copy(), keys, valid)
            }

            fun open(server: MinecraftServer): Catalogue =
                server.overworld().dataStorage.computeIfAbsent(
                    Factory({ Catalogue(CompoundTag(), mutableSetOf(), true) }, { tag, _ -> load(tag) }, null),
                    "orbitalis_orbital_sectors",
                )
        }
    }

    const val MAX_LOADED_SECTORS = 256
    const val MAX_SECTORS = 4096
}
