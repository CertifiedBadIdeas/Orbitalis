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

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterShadersEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.space.PlanetLimbRenderer

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object PlanetLimbShaders {
    @JvmStatic @SubscribeEvent
    fun register(event: RegisterShadersEvent) {
        event.registerShader(
            ShaderInstance(
                event.resourceProvider,
                ResourceLocation.fromNamespaceAndPath(Orbitalis.MOD_ID, "planet_limb"),
                DefaultVertexFormat.POSITION_COLOR,
            ),
        ) { PlanetLimbRenderer.worldShader = it }
    }
}

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object PlanetLimbEvents {
    // Compose front scatter after the normal cloud pass; native depth still occludes nearby hulls.
    @JvmStatic
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun render(event: RenderLevelStageEvent) {
        if (event.stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) PlanetLimbRenderer.renderWorld()
    }
}
