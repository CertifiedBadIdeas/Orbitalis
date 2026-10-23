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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.server.MinecraftServer
import org.cbi.orbitalis.minecraft.flight.CelestialFlight

/** Keep vanilla block/entity ticks running wherever native Sable already owns loaded ships. */
object SableWorldActivity {
    fun beforeTick(server: MinecraftServer) {
        for (level in CelestialFlight.levels(server)) {
            val container = SubLevelContainer.getContainer(level) ?: continue
            // Sable plot tickets are not vanilla forced terrain chunks. Without this, vanilla
            // idles the dimension after 300 playerless ticks while native ship physics continues.
            // Do not load any ship/chunk or retain an empty dimension on this adapter's behalf.
            if (container.allSubLevels.any { !it.isRemoved }) level.resetEmptyTime()
        }
    }
}
