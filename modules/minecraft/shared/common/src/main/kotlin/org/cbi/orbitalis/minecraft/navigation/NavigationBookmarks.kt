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

package org.cbi.orbitalis.minecraft.navigation

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import java.util.UUID
import kotlin.math.abs

/** Personal navigation markers, independent of the disposable consumer's autopilot launch contract. */
class NavigationBookmarks : SavedData() {
    private val markers = linkedMapOf<UUID, Vector3>()

    fun get(player: UUID): Vector3? = markers[player]

    fun put(
        player: UUID,
        position: Vector3,
        halfSize: Double = FlatPlanetSpace.HALF_SIZE,
    ) {
        validate(position, halfSize)
        require(player in markers || markers.size < 1024) { "Navigation bookmark limit reached" }
        markers[player] = position
        setDirty()
    }

    fun clear(player: UUID) {
        if (markers.remove(player) != null) setDirty()
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag {
        tag.putInt("version", 1)
        val entries = ListTag()
        markers.forEach { (player, position) ->
            val entry = CompoundTag()
            entry.putUUID("player", player)
            entry.putDouble("x", position.x)
            entry.putDouble("y", position.y)
            entry.putDouble("z", position.z)
            entries.add(entry)
        }
        tag.put("markers", entries)
        return tag
    }

    companion object {
        private const val KEY = "orbitalis_navigation_markers"

        fun get(server: MinecraftServer): NavigationBookmarks =
            server.overworld().dataStorage.computeIfAbsent(
                Factory(::NavigationBookmarks, ::load, null),
                KEY,
            )

        fun validate(
            position: Vector3,
            halfSize: Double = FlatPlanetSpace.HALF_SIZE,
        ) {
            require(abs(position.x) < halfSize && abs(position.z) < halfSize && position.y in -64.0..320.0) {
                "Mark a surface location inside the finite Overworld footprint and build height"
            }
        }

        fun load(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): NavigationBookmarks {
            require(tag.getInt("version") == 1 && tag.contains("markers", 9)) { "Unsupported navigation marker data" }
            val entries = tag.get("markers") as ListTag
            require(entries.size <= 1024 && (entries.isEmpty() || entries.elementType.toInt() == 10)) { "Invalid navigation marker list" }
            val result = NavigationBookmarks()
            for (raw in entries) {
                val entry = raw as CompoundTag
                require(entry.hasUUID("player") && listOf("x", "y", "z").all { entry.contains(it, 99) }) { "Invalid navigation marker" }
                val position = Vector3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"))
                validate(position, 10000.0)
                require(result.markers.put(entry.getUUID("player"), position) == null) { "Duplicate navigation marker" }
            }
            return result
        }
    }
}
