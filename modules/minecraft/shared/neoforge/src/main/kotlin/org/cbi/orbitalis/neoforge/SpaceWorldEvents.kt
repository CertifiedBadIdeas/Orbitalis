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

import com.mojang.logging.LogUtils
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.space.SpaceWorlds

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object SpaceWorldEvents {
    private val logger = LogUtils.getLogger()

    @JvmStatic
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun load(event: LevelEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        if (level.dimension() != FlatPlanetSpace.SPACE) return
        val objects = SpaceWorlds.initialize(level)
        logger.info("Orbital world model: {}", if (objects) "physical objects in sparse vacuum" else "legacy terrain (preserved)")
    }

    @JvmStatic
    @SubscribeEvent
    fun stopped(event: ServerStoppedEvent) = SpaceWorlds.close(event.server)
}
