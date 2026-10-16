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
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.CubePlayerView
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object CubePlayerViewEvents {
    @JvmStatic
    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        val level = Minecraft.getInstance().level ?: return
        for (player in level.players()) {
            if (CubePlayerPhysics.active(player)) CubePlayerView.tick(player) else CubePlayerView.forget(player.uuid)
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun logout(event: ClientPlayerNetworkEvent.LoggingOut) = CubePlayerView.clear()

    @JvmStatic
    @SubscribeEvent
    fun leave(event: EntityLeaveLevelEvent) {
        if (event.level.isClientSide) CubePlayerView.forget(event.entity.uuid)
    }
}
