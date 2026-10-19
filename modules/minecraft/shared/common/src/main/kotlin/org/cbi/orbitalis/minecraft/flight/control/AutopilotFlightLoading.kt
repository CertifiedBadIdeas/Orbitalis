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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.UUIDUtil
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import java.util.UUID

/** Retains an already loaded controlled body; never looks up or wakes a dormant UUID. */
class AutopilotFlightLoading(
    initial: ServerSubLevel,
) : AutoCloseable {
    private val ship = initial.uniqueId
    private val key = UUID.randomUUID()
    private var retained: ServerSubLevel? = null
    private var closed = false

    init {
        require(!initial.isRemoved) { "Cannot retain an unloaded ship" }
        active.add(key)
        try {
            follow(initial)
        } catch (failure: RuntimeException) {
            close()
            throw failure
        }
    }

    fun follow(body: ServerSubLevel) {
        check(!closed)
        require(body.uniqueId == ship) { "Flight loading cannot switch ship identity" }
        // Bridge the mission's readiness delay after source removal. Inspect only already
        // loaded containers: no holding payloads, chunks or dormant UUIDs are loaded here.
        if (body.isRemoved) {
            val replacement =
                body.level.server.allLevels.firstNotNullOfOrNull { level ->
                    (SubLevelContainer.getContainer(level)?.getSubLevel(ship) as? ServerSubLevel)?.takeUnless { it.isRemoved }
                } ?: return
            follow(replacement)
            return
        }
        if (retained !== body) {
            release()
            checkNotNull(SubLevelContainer.getContainer(body.level)).addForceLoadTicket(body, TYPE, key)
            retained = body
        }
        body.level.resetEmptyTime()
    }

    private fun release() {
        retained?.let { body -> SubLevelContainer.getContainer(body.level)?.removeForceLoadTicket(body, TYPE, key) }
        retained = null
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            release()
        } finally {
            active.remove(key)
        }
    }

    companion object {
        // Sable serializes its ticket registry. Register before worlds load and discard
        // tickets from interrupted sessions: flight controllers are never resumed on restart.
        val TYPE: SubLevelLoadingTicketType<UUID> =
            SubLevelLoadingTicketType.create(
                ResourceLocation.fromNamespaceAndPath("orbitalis_flight_consumer", "active_autopilot"),
                UUIDUtil.CODEC,
            )
        private val active = mutableSetOf<UUID>()

        fun initialize() = Unit

        fun discardOrphans(server: MinecraftServer) {
            for (level in server.allLevels) {
                val container = SubLevelContainer.getContainer(level) ?: continue
                for ((body, tickets) in container.collectForceLoadTickets().mapValues { it.value.toList() }) {
                    for (ticket in tickets) {
                        if (ticket.type() == TYPE && ticket.key() !in active) {
                            container.removeForceLoadTicket(body, TYPE, ticket.key() as UUID)
                        }
                    }
                }
            }
        }
    }
}
