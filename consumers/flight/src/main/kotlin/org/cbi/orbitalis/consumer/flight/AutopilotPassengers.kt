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

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerPlayer
import org.cbi.orbitalis.minecraft.integration.sable.SableShipContact
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPassengers

/** A remote command owner is not implicitly included in a native ship handoff. */
internal object AutopilotPassengers {
    fun select(
        body: ServerSubLevel,
        candidate: ServerPlayer?,
    ): ServerPlayer? =
        candidate?.takeIf {
            it.isAlive && it.level() === body.level && SableShipContact.resolve(it) === body
        } ?: ShipTransferPassengers.nearby(body).filterIsInstance<ServerPlayer>().singleOrNull()
}
