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

package org.cbi.orbitalis.minecraft.map

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import java.util.UUID

/** Optional native adapters are installed without making client/server map code load Sable classes. */
object OrbitalMapHooks {
    var objects: (MinecraftServer) -> List<MapObject> = { emptyList() }
    var playerPosition: (ServerPlayer) -> Vector3 = { player ->
        FlightSpaceCoordinates.globalPosition(player.level(), Vector3(player.x, player.y, player.z))
    }
}

/** Server-thread leases; viewing never requests terrain, holding payloads or native object wake. */
class OrbitalMapService(
    private val server: MinecraftServer,
    private val publish: (ServerPlayer, MapSnapshot) -> Unit,
    private val findPlayer: (UUID) -> ServerPlayer? = server.playerList::getPlayer,
) {
    private data class Viewer(
        val token: UUID,
        var expires: Long,
    )

    private val viewers = mutableMapOf<UUID, Viewer>()
    private val sent = mutableMapOf<UUID, Long>()
    private var tick = 0L

    fun request(
        player: ServerPlayer,
        request: MapRequest,
    ) {
        if (!request.open) {
            if (viewers[player.uuid]?.token == request.token) viewers.remove(player.uuid)
        } else if (FlatPlanetSpace.contains(player.level())) {
            viewers[player.uuid] = Viewer(request.token, tick + 100)
        }
    }

    fun viewing(player: ServerPlayer): Boolean =
        viewers[player.uuid]?.let { it.expires > tick && FlatPlanetSpace.contains(player.level()) } == true

    fun logout(id: UUID) {
        viewers.remove(id)
        sent.remove(id)
    }

    fun tick() {
        tick++
        viewers.entries.removeIf { (id, viewer) ->
            viewer.expires <= tick ||
                findPlayer(id)?.let { !FlatPlanetSpace.contains(it.level()) } != false
        }
        val recipients = viewers.keys.mapNotNull(findPlayer).filter { tick - (sent[it.uuid] ?: -10L) >= 10L }
        if (recipients.isEmpty()) return
        val objects = OrbitalMapHooks.objects(server).distinctBy { it.id }
        for (player in recipients) {
            val position = OrbitalMapHooks.playerPosition(player)
            if (position.length() > 1e8) continue
            val selected =
                objects
                    .sortedWith(
                        compareBy<MapObject> { (it.position - position).length() }.thenBy { it.id },
                    ).take(MapSnapshot.MAX_OBJECTS)
            publish(
                player,
                MapSnapshot(checkNotNull(viewers[player.uuid]).token, tick, position, selected, objects.size.coerceAtMost(1000000)),
            )
            sent[player.uuid] = tick
        }
    }
}
