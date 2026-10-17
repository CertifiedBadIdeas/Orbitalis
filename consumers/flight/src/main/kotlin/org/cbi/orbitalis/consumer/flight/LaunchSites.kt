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

package org.cbi.orbitalis.consumer.flight

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.joml.Quaterniond
import java.util.UUID
import kotlin.math.abs

/** Only landing bookmarks survive a restart. Never persists a running controller or throttle. */
internal class LaunchSites : SavedData() {
    class Site(
        val ship: UUID,
        val position: Vector3,
        attitude: Quaterniond,
        val surface: ResourceKey<Level> = FlightTestSpace.SURFACE,
    ) {
        private val savedAttitude = Quaterniond(attitude)
        val attitude: Quaterniond get() = Quaterniond(savedAttitude)

        init {
            require(surface == FlightTestSpace.SURFACE || surface == Level.OVERWORLD)
            val heights = if (surface == Level.OVERWORLD) -64.0..384.0 else 68.0..320.0
            require(
                savedAttitude.isFinite && position.x.isFinite() && position.y.isFinite() && position.z.isFinite() &&
                    abs(savedAttitude.lengthSquared() - 1) <= 1e-6 && abs(position.x) <= 1800 && abs(position.z) <= 1800 &&
                    position.y in heights,
            ) { "Launch site outside the supported surface bounds" }
        }
    }

    private val sites = mutableMapOf<UUID, Site>()

    fun require(
        pilot: UUID,
        ship: UUID,
    ): Site {
        val site = sites[pilot]
        require(site != null && site.ship == ship) { "No launch bookmark for this pilot and ship; launch with autopilot orbit first" }
        return site
    }

    fun put(
        pilot: UUID,
        site: Site,
    ) {
        require(pilot in sites || sites.size < 64) { "Test launch bookmark limit reached" }
        sites[pilot] = site
        setDirty()
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag {
        tag.putInt("version", 2)
        val list = ListTag()
        sites.forEach { (pilot, site) ->
            val entry = CompoundTag()
            entry.putUUID("pilot", pilot)
            entry.putString("surface", site.surface.location().toString())
            entry.putUUID("ship", site.ship)
            entry.putDouble("x", site.position.x)
            entry.putDouble("y", site.position.y)
            entry.putDouble("z", site.position.z)
            val attitude = site.attitude
            entry.putDouble("qx", attitude.x)
            entry.putDouble("qy", attitude.y)
            entry.putDouble("qz", attitude.z)
            entry.putDouble("qw", attitude.w)
            list.add(entry)
        }
        tag.put("sites", list)
        return tag
    }

    companion object {
        fun get(server: MinecraftServer): LaunchSites =
            server.overworld().dataStorage.computeIfAbsent(
                Factory(::LaunchSites, ::load, null),
                "orbitalis_flight_launch_sites",
            )

        fun load(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): LaunchSites {
            require(tag.getInt("version") in 1..2 && tag.contains("sites", 9)) { "Unsupported launch bookmark data" }
            val list = tag.get("sites") as ListTag
            require(list.isEmpty() || list.elementType.toInt() == 10) { "Invalid launch bookmark list" }
            require(list.size <= 64) { "Too many launch bookmarks" }
            val data = LaunchSites()
            for (value in list) {
                val entry = value as CompoundTag
                for (key in listOf("x", "y", "z", "qx", "qy", "qz", "qw")) {
                    require(entry.contains(key, 99) && entry.getDouble(key).isFinite()) { "Invalid launch bookmark vector" }
                }
                val site =
                    Site(
                        entry.getUUID("ship"),
                        Vector3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")),
                        Quaterniond(entry.getDouble("qx"), entry.getDouble("qy"), entry.getDouble("qz"), entry.getDouble("qw")),
                        if (tag.getInt("version") ==
                            1
                        ) {
                            FlightTestSpace.SURFACE
                        } else {
                            listOf(FlightTestSpace.SURFACE, Level.OVERWORLD).singleOrNull {
                                it.location().toString() == entry.getString("surface")
                            } ?: throw IllegalArgumentException("Invalid launch surface")
                        },
                    )
                require(data.sites.put(entry.getUUID("pilot"), site) == null) { "Duplicate launch bookmark" }
            }
            return data
        }
    }
}
