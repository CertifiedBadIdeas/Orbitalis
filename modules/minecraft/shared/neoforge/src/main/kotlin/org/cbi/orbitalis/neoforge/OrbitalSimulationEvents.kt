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

package org.cbi.orbitalis.neoforge

import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object OrbitalSimulationEvents {
    /** Publish the completed physics tick before ordinary map and unloaded-motion consumers run. */
    @JvmStatic
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun tick(event: ServerTickEvent.Post) = OrbitalSimulation.tick(event.server)
}
