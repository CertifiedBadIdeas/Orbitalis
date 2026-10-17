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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.navigation.TrajectoryReceiver

@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object TrajectoryClientSetup {
    @JvmStatic
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork(
            Runnable {
                TrajectoryReceiver.accept = { payload ->
                    val level = Minecraft.getInstance().level
                    val value = payload.snapshot
                    TrajectoryClientEvents.snapshot =
                        value?.takeIf {
                            level != null && it.dimension == level.dimension().location() &&
                                level.gameTime - it.sampledTick in -20L..60L
                        }
                    TrajectoryClientEvents.marker = payload.marker
                    TrajectoryClientEvents.sourceLevel = level
                    TrajectoryClientEvents.receivedNanos = System.nanoTime()
                }
            },
        )
    }
}
