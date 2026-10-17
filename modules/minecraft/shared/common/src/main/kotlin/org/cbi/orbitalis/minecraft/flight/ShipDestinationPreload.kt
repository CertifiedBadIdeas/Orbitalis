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

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.core.planet.Vector3
import java.util.UUID

/** A bounded vanilla terrain ticket. Native ship/plot staging remains owned by the transfer journal. */
class ShipDestinationPreload : AutoCloseable {
    private val token = UUID.randomUUID()
    private var target: Pair<ServerLevel, ChunkPos>? = null

    fun update(
        level: ServerLevel,
        position: Vector3,
    ) {
        val chunk = ChunkPos(BlockPos.containing(position.x, position.y, position.z))
        if (target?.first !== level || target?.second != chunk) {
            close()
            target = level to chunk
        }
        // Refresh the timeout, without waiting for chunk generation on the calling server tick.
        level.chunkSource.addRegionTicket(TYPE, chunk, 2, token)
    }

    override fun close() {
        target?.let { (level, chunk) -> level.chunkSource.removeRegionTicket(TYPE, chunk, 2, token) }
        target = null
    }

    companion object {
        private val TYPE: TicketType<UUID> = TicketType.create("orbitalis_ship_preload", Comparator.naturalOrder<UUID>(), 200)
    }
}
