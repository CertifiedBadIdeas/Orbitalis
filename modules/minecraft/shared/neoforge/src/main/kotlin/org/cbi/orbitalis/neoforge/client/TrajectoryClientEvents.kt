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

package org.cbi.orbitalis.neoforge.client

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.navigation.TrajectoryRenderer
import org.cbi.orbitalis.minecraft.navigation.TrajectorySnapshot

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object TrajectoryClientEvents {
    var snapshot: TrajectorySnapshot? = null
    var marker: org.cbi.orbitalis.core.planet.Vector3? = null
    var sourceLevel: ClientLevel? = null
    var receivedNanos = 0L

    @JvmStatic
    @SubscribeEvent
    fun logout(event: ClientPlayerNetworkEvent.LoggingOut) {
        snapshot = null
        marker = null
        sourceLevel = null
    }

    @JvmStatic
    @SubscribeEvent
    fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val data = snapshot ?: return
        if (sourceLevel !== level || data.dimension != level.dimension().location() ||
            System.nanoTime() - receivedNanos > 3_000_000_000L
        ) {
            snapshot = null
            return
        }
        if (minecraft.options.hideGui) return
        TrajectoryRenderer.render(level, event.camera, event.modelViewMatrix, data, marker)
    }
}
