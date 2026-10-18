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

import net.minecraft.client.gui.screens.ReceivingLevelScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.space.FlightTransitionView

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object FlightTransitionEvents {
    @JvmStatic @SubscribeEvent
    fun frame(event: RenderFrameEvent.Post) = FlightTransitionView.frame()

    @JvmStatic @SubscribeEvent
    fun loading(event: ScreenEvent.Render.Pre) {
        if (event.screen is ReceivingLevelScreen && FlightTransitionView.hideLoadingScreen()) event.isCanceled = true
    }

    @JvmStatic @SubscribeEvent
    fun logout(event: ClientPlayerNetworkEvent.LoggingOut) = FlightTransitionView.clear()
}
