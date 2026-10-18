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
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.map.OrbitalMapScreen
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.map.MapSnapshotReceiver
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object OrbitalMapClient {
    val key = KeyMapping("key.orbitalis.map", GLFW.GLFW_KEY_M, "key.categories.orbitalis")

    @JvmStatic @SubscribeEvent
    fun keys(event: RegisterKeyMappingsEvent) {
        event.register(key)
    }

    @JvmStatic @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork(
            Runnable {
                OrbitalMapScreen.send = { if (Minecraft.getInstance().connection != null) PacketDistributor.sendToServer(it) }
                MapSnapshotReceiver.accept = { (Minecraft.getInstance().screen as? OrbitalMapScreen)?.accept(it) }
            },
        )
    }
}

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object OrbitalMapInput {
    @JvmStatic @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
        while (OrbitalMapClient.key.consumeClick()) {
            if (client.screen == null && client.level?.let(FlatPlanetSpace::contains) == true &&
                client.player != null
            ) {
                client.setScreen(OrbitalMapScreen(OrbitalMapClient.key))
            }
        }
    }
}
