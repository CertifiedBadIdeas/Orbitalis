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

import net.minecraft.client.KeyMapping
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.ship.SableShipView
import org.cbi.orbitalis.minecraft.client.ship.ShipNavball
import org.cbi.orbitalis.minecraft.client.ship.ShipViewHooks
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object ShipViewRegistration {
    val camera = KeyMapping("key.orbitalis.ship_camera", GLFW.GLFW_KEY_F6, "key.categories.orbitalis")
    val axes = KeyMapping("key.orbitalis.navball_axes", GLFW.GLFW_KEY_F7, "key.categories.orbitalis")

    @JvmStatic @SubscribeEvent
    fun keys(event: RegisterKeyMappingsEvent) {
        if (!ModList.get().isLoaded("sable")) return
        ShipViewHooks.controller = SableShipView
        org.cbi.orbitalis.minecraft.client.space.FlightTransitionContact.ship =
            org.cbi.orbitalis.minecraft.client.ship.SableFlightPresentation::ship
        org.cbi.orbitalis.minecraft.client.space.FlightTransitionContact.ready =
            org.cbi.orbitalis.minecraft.client.ship.SableFlightPresentation::ready
        event.register(camera)
        event.register(axes)
    }

    @JvmStatic @SubscribeEvent
    fun layers(event: RegisterGuiLayersEvent) {
        if (!ModList.get().isLoaded("sable")) return
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath("orbitalis", "navball")) { graphics, delta ->
            ShipNavball.render(graphics, delta.getGameTimeDeltaPartialTick(false))
        }
    }
}

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object ShipViewEvents {
    @JvmStatic @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (!ModList.get().isLoaded("sable")) return
        while (ShipViewRegistration.camera.consumeClick()) SableShipView.toggle()
        while (ShipViewRegistration.axes.consumeClick()) ShipNavball.cycleAxes()
        SableShipView.tick()
    }

    @JvmStatic @SubscribeEvent
    fun scroll(event: InputEvent.MouseScrollingEvent) {
        if (ModList.get().isLoaded("sable") && SableShipView.scroll(event.scrollDeltaY)) event.isCanceled = true
    }
}
