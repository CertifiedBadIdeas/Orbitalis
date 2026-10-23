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

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.util.concurrent.CompletableFuture

/** Optional adapters own their persistence; Orbitalis owns ship authority and native snapshots. */
interface ShipTransferParticipant {
    interface Preparation : AutoCloseable {
        val ready: CompletableFuture<Void>
    }

    interface Scope : AutoCloseable {
        /** Authority is resolved; update address metadata before the native checkpoint. */
        fun resolve()
    }

    /** Source plot ticks are held. Do not block; readiness must include final persistence. */
    fun prepare(
        source: ServerLevel,
        positions: Set<BlockPos>,
    ): Preparation

    /** Open before any destination copy loads; close after obsolete copies are removed, before save. */
    fun stage(
        source: ServerLevel,
        destination: ServerLevel,
        positions: Map<BlockPos, BlockPos>,
    ): Scope

    /** Guard both copies during journal recovery; reconcile the selected survivor on close. */
    fun recover(
        source: ServerLevel,
        destination: ServerLevel,
        positions: Map<BlockPos, BlockPos>,
        committed: Boolean,
    ): Scope
}

object ShipTransferParticipants {
    private val adapters = linkedMapOf<String, ShipTransferParticipant>()

    /** Register during mod initialization. Returned registration is useful for isolated integration tests. */
    @Synchronized
    fun register(
        id: String,
        participant: ShipTransferParticipant,
    ): AutoCloseable {
        require(id.isNotBlank() && id !in adapters) { "Duplicate transfer participant: $id" }
        adapters[id] = participant
        return AutoCloseable { synchronized(this) { if (adapters[id] === participant) adapters.remove(id) } }
    }

    @Synchronized
    internal fun snapshot(): List<ShipTransferParticipant> = adapters.values.toList()
}
