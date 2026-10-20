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

package org.cbi.orbitalis.minecraft.client.space

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.lighting.SkyLighting
import org.joml.Matrix4f

object SurfaceCelestialSky {
    @JvmStatic fun enabled(level: ClientLevel): Boolean = SkyLighting.physicalOverworld(level)

    @JvmStatic fun render(
        level: ClientLevel,
        camera: Camera,
        view: Matrix4f,
        projection: Matrix4f,
        foggy: Boolean,
    ) {
        if (!enabled(level) || foggy || camera.fluidInCamera != net.minecraft.world.level.material.FogType.NONE) return
        val entity = camera.entity
        if (entity is net.minecraft.world.entity.LivingEntity &&
            (
                entity.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) ||
                    entity.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)
            )
        ) {
            return
        }
        val p = camera.position
        val observer = CelestialView.globalPosition(level, Vector3(p.x, p.y, p.z))
        val sample = CelestialView.sample()
        val rootView = CelestialView.rootView(level, view)
        val scale = 60.0 / (observer.length() + sample.frames.profile.earthOrbitRadius)
        val depth =
            org.lwjgl.opengl.GL11
                .glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
        val mask =
            org.lwjgl.opengl.GL11
                .glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK)
        val cull =
            org.lwjgl.opengl.GL11
                .glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE)
        val shader = RenderSystem.getShader()
        try {
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            RenderSystem.enableCull()
            SolarRenderer.render(
                sample.frames.profile,
                sample.frames.tick,
                sample.frames.seconds,
                observer,
                rootView,
                projection,
                scale,
                writeDepth = false,
            )
            LunarPlanetRenderer.render(
                sample.frames.profile,
                sample.frames.tick,
                CelestialFlight.lunarSeed,
                observer,
                rootView,
                projection,
                scale,
                sample.frames.seconds,
            )
        } finally {
            RenderSystem.setShader { shader }
            RenderSystem.depthMask(mask)
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
        }
    }
}
