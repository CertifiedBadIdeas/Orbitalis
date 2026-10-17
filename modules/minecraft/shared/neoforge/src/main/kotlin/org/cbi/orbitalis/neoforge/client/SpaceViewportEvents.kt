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
import net.minecraft.world.level.material.FogType
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ViewportEvent
import org.cbi.orbitalis.core.Orbitalis

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object SpaceViewportEvents {
    @JvmStatic
    @SubscribeEvent
    fun fogColor(event: ViewportEvent.ComputeFogColor) {
        val level = Minecraft.getInstance().level ?: return
        if (!SpaceEffects.applies(level) || event.camera.fluidInCamera != FogType.NONE) return
        event.red = 0f
        event.green = 0f
        event.blue = 0f
    }
}
