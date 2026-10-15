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

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.GlStateBackup
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.OrbitPlanetReference
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace

/** Physical-client-only subscription; server startup never resolves the renderer or Minecraft client. */
@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object FlightReferenceEvents {
    @JvmStatic
    @SubscribeEvent
    fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_SKY) return
        if (Minecraft.getInstance().level?.dimension() != FlightTestSpace.ORBIT) return
        val state = GlStateBackup()
        RenderSystem.backupGlState(state)
        try {
            OrbitPlanetReference.render(event.camera, event.modelViewMatrix)
        } finally {
            RenderSystem.restoreGlState(state)
        }
    }
}
