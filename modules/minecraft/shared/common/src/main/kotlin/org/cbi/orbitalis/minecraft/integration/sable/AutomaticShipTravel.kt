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

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
import org.cbi.orbitalis.minecraft.flight.ShipDestinationPreload
import org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
import org.slf4j.LoggerFactory
import java.util.UUID

/** One server coordinator owns all native ship transfers; guidance may reserve its own handoff. */
class AutomaticShipTravel(
    private val server: MinecraftServer,
    private val transfers: SableShipTransfer,
    private val owner: Any? = null,
) {
    private data class Loading(
        val source: ShipDestinationPreload,
        val destination: ShipDestinationPreload,
    ) {
        fun close() {
            source.close()
            destination.close()
        }
    }

    private val loading = mutableMapOf<UUID, Loading>()

    fun close() {
        loading.values.forEach { it.close() }
        loading.clear()
    }

    private val retry = mutableMapOf<UUID, Int>()
    private var ticks = 0

    fun tick() {
        ticks++
        retry.entries.removeIf { it.value <= ticks }
        if (transfers.busy) return
        if (CelestialFlight.trySample(server) == null) {
            loading.values.forEach { it.close() }
            loading.clear()
            return
        }
        val seen = mutableSetOf<UUID>()
        for (world in CelestialFlight.levels(server)) {
            val key = world.dimension()
            val container = SubLevelContainer.getContainer(world) ?: continue
            for (body in container.allSubLevels.filterIsInstance<ServerSubLevel>().toList()) {
                if (body.isRemoved || SpaceTravelReservations.reserved(body.uniqueId, owner) || body.uniqueId in retry) continue
                if (body.massTracker.centerOfMass == null || body.massTracker.mass <= 0) continue
                val reading = SableFlightGravity.reading(body)
                val physical =
                    CelestialFlight
                        .sample(
                            server,
                        ).rootState(
                            CartesianState(reading.position, Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z)),
                            key,
                        )
                val global = physical.position
                seen.add(body.uniqueId)
                if (body.uniqueId !in loading && loading.size >= 256) {
                    // Resting ships must not permanently occupy all motion-history slots.
                    val oldest = loading.entries.firstOrNull { !it.value.destination.active } ?: continue
                    oldest.value.close()
                    loading.remove(oldest.key)
                }
                val tickets = loading.getOrPut(body.uniqueId) { Loading(ShipDestinationPreload(), ShipDestinationPreload()) }
                val mayLoad = tickets.destination.active || loading.values.count { it.destination.active } < 16
                if (!tickets.destination.approach(
                        world,
                        global,
                        physical.velocity,
                        mayLoad,
                    )
                ) {
                    tickets.source.close()
                    continue
                }
                val needsSourceTerrain =
                    !org.cbi.orbitalis.minecraft.space.SpaceWorlds
                        .objects(world)
                if (needsSourceTerrain) {
                    tickets.source.update(
                        world,
                        reading.position,
                        reading.velocity.length(),
                    )
                } else {
                    tickets.source.close()
                }
                val destination =
                    PlayerSpaceTravel.destination(
                        world,
                        reading.position,
                        Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z),
                    )
                        ?: continue
                if (!tickets.destination.ready) continue
                val b = body.boundingBox()
                if (b.maxX() - b.minX() > 32 || b.maxZ() - b.minZ() > 32) continue
                try {
                    val participants = ShipTransferPassengers.nearby(body)
                    val player = participants.filterIsInstance<ServerPlayer>().singleOrNull()
                    transfers.begin(player, body, destination)
                    return
                } catch (failure: IllegalArgumentException) {
                    retry[body.uniqueId] = ticks + 200
                    LoggerFactory
                        .getLogger(
                            "Orbitalis/Travel",
                        ).warn("Ship {} remains at source: {}", body.uniqueId, failure.message)
                }
            }
        }
        loading.entries.removeIf { (id, tickets) ->
            if (id !in seen) {
                tickets.close()
                true
            } else {
                false
            }
        }
    }

    fun attached(player: ServerPlayer): Boolean {
        if (Sable.HELPER.getTrackingSubLevel(player) != null) return true
        var root = player.rootVehicle
        if (root !== player && Sable.HELPER.getTrackingSubLevel(root) != null) return true
        val container = SubLevelContainer.getContainer(player.level()) ?: return false
        val position = root.position()
        return container.allSubLevels.any { body ->
            body.plot.contains(position) ||
                body.boundingBox().let { b ->
                    position.x >= b.minX() - 1 && position.x <= b.maxX() + 1 && position.y >= b.minY() - 1 &&
                        position.y <= b.maxY() + 3 && position.z >= b.minZ() - 1 && position.z <= b.maxZ() + 1
                }
        }
    }
}
