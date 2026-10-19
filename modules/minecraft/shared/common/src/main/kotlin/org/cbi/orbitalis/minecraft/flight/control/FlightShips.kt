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

package org.cbi.orbitalis.minecraft.flight.control

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.MinecraftServer
import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableShipDirectory
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData
import java.util.UUID

/** Read-only directory: native loaded bodies, motion estimates and remembered launch IDs. */
object FlightShips {
    data class Entry(
        val id: UUID,
        val dimension: String?,
        val position: Vector3?,
        val body: ServerSubLevel?,
        val estimate: Boolean = false,
    ) {
        fun describe(): String {
            val location = position?.let { "%.1f %.1f %.1f".format(java.util.Locale.ROOT, it.x, it.y, it.z) } ?: "position unknown"
            val state =
                if (body != null) {
                    "loaded"
                } else if (estimate) {
                    "unloaded; orbital estimate"
                } else {
                    "not loaded; launch bookmark only"
                }
            return "$id | $state | ${dimension ?: "dimension unknown"} | $location"
        }
    }

    fun entries(server: MinecraftServer): List<Entry> {
        val result = linkedMapOf<UUID, Entry>()
        LaunchSites.get(server).ships().forEach { result[it] = Entry(it, null, null, null) }
        server.getLevel(FlatPlanetSpace.SPACE)?.let { level ->
            val data = level.dataStorage.get(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME)
            for (record in data
                ?.takeIf { it.readable }
                ?.records
                ?.values
                .orEmpty()) {
                result[record.id] =
                    Entry(record.id, level.dimension().location().toString(), FlatPlanetFrame.toSpace(record.state.position), null, true)
            }
        }
        for (body in SableShipDirectory.loaded(server)) {
            val p =
                if (body.massTracker.centerOfMass !=
                    null
                ) {
                    FlightBody.position(body)
                } else {
                    FlightBody.vector(body.logicalPose().position())
                }
            result[body.uniqueId] =
                Entry(
                    body.uniqueId,
                    body.level
                        .dimension()
                        .location()
                        .toString(),
                    p,
                    body,
                )
        }
        return result.values.sortedBy { it.id.toString() }
    }

    fun loaded(
        server: MinecraftServer,
        id: UUID,
    ): ServerSubLevel =
        checkNotNull(entries(server).firstOrNull { it.id == id }?.body) {
            "Ship $id is not loaded; autopilot never loads ships or chunks. Approach it before issuing guidance."
        }
}
