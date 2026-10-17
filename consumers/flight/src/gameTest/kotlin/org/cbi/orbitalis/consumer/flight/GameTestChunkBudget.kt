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

import com.mojang.logging.LogUtils
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.io.File

/** A server-side fixture budget; never present in the normal consumer client/server. */
@EventBusSubscriber(modid = FlightConsumerMod.ID)
object GameTestChunkBudget {
    private const val RADIUS = 2
    private val logger = LogUtils.getLogger()
    private val peaks = mutableMapOf<String, Int>()

    @JvmStatic
    @SubscribeEvent
    fun started(event: ServerStartedEvent) {
        peaks.clear()
        val players = event.server.playerList
        logger.info("GameTest player distances before budget: view={}, simulation={}", players.viewDistance, players.simulationDistance)
        players.setViewDistance(RADIUS)
        players.setSimulationDistance(RADIUS)
        logger.info("GameTest server view/simulation radius: {} chunks", RADIUS)
    }

    @JvmStatic
    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        for (level in event.server.allLevels) {
            val name = level.dimension().location().toString()
            peaks[name] = maxOf(peaks[name] ?: 0, level.chunkSource.loadedChunksCount)
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun stopping(event: ServerStoppingEvent) {
        val levels =
            event.server.allLevels.joinToString(",") { level ->
                val name = level.dimension().location().toString()
                val container = SubLevelContainer.getContainer(level)
                "\"$name\":{\"loaded_at_stop\":${level.chunkSource.loadedChunksCount},\"peak_loaded\":${peaks[name] ?: 0},\"players\":${level.players().size},\"sublevels\":${container?.allSubLevels?.size ?: 0},\"force_loaded_sublevels\":${container?.collectForceLoadedSubLevels()?.size ?: 0}}"
            }
        File("server-stopping.json").writeText(
            "{\"stopping_epoch_millis\":${System.currentTimeMillis()},\"radius\":$RADIUS,\"levels\":{$levels}}\n",
        )
        logger.info("GameTest chunk metrics at shutdown: {}", levels)
    }
}
