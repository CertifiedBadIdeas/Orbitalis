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
import net.minecraft.client.renderer.FogRenderer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.material.FogType
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ViewportEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.client.space.FlightSky

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object SpaceViewportEvents {
    @JvmStatic
    @SubscribeEvent
    fun fogDistance(event: ViewportEvent.RenderFog) {
        val level = Minecraft.getInstance().level ?: return
        if (event.type != FogType.NONE || event.mode != FogRenderer.FogMode.FOG_TERRAIN) return
        val entity = event.camera.entity
        if (entity is LivingEntity && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))) return
        val weight = FlightSky.weight(level, event.camera)
        if (weight <= 0f) return
        // Native distance fog must not paint an opaque frontier over the matching distant body.
        // The existing atmospheric transition reduces fog density continuously into vacuum.
        val atmosphere = 1f - weight
        event.nearPlaneDistance = if (atmosphere > 0f) event.nearPlaneDistance / atmosphere else Float.MAX_VALUE / 2f
        event.farPlaneDistance = if (atmosphere > 0f) event.farPlaneDistance / atmosphere else Float.MAX_VALUE
        event.isCanceled = true
    }

    @JvmStatic
    @SubscribeEvent
    fun fogColor(event: ViewportEvent.ComputeFogColor) {
        val level = Minecraft.getInstance().level ?: return
        if (event.camera.fluidInCamera != FogType.NONE) return
        val weight = FlightSky.weight(level, event.camera)
        event.red *= 1f - weight
        event.green *= 1f - weight
        event.blue *= 1f - weight
    }
}
