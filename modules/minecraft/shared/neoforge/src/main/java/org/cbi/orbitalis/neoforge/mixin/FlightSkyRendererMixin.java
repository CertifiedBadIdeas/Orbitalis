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

package org.cbi.orbitalis.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.cbi.orbitalis.minecraft.client.space.FlightSky;
import org.cbi.orbitalis.minecraft.client.space.PlanetCloudRenderer;
import org.cbi.orbitalis.minecraft.client.space.SpaceSkyRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Composite the same orbital scene before native travel, leaving ground/other dimensions native. */
@Mixin(LevelRenderer.class)
public abstract class FlightSkyRendererMixin {
    @Shadow private ClientLevel level;
    @Shadow private int ticks;

    @WrapMethod(method = "renderSky")
    private void orbitalis$sky(Matrix4f view, Matrix4f projection, float partialTick, Camera camera,
                               boolean foggy, Runnable setupFog, Operation<Void> original) {
        PlanetCloudRenderer.INSTANCE.synchronize(ticks + partialTick,
            level.dimension() == Level.OVERWORLD ? level.effects().getCloudHeight() : null,
            level.getCloudColor(partialTick));
        float weight = FlightSky.INSTANCE.weight(level, camera);
        if (level.dimension() != Level.OVERWORLD || weight <= 0) {
            original.call(view, projection, partialTick, camera, foggy, setupFog);
            return;
        }
        if (weight < 1) original.call(view, projection, partialTick, camera, foggy, setupFog);
        setupFog.run();
        SpaceSkyRenderer.INSTANCE.render(level, camera, view, foggy, weight);
    }

    @WrapMethod(method = "renderClouds")
    private void orbitalis$cloudLayer(com.mojang.blaze3d.vertex.PoseStack poses, Matrix4f view,
                                      Matrix4f projection, float partialTick, double x, double y, double z,
                                      Operation<Void> original) {
        // Wrap the entire method, including renderer replacements injected by Sodium.
        // One height/phase-matched layer owns the elevated view: coincident sheets would z-fight.
        if (FlightSky.INSTANCE.weight(level, Minecraft.getInstance().gameRenderer.getMainCamera()) > 0) {
            net.minecraft.client.renderer.FogRenderer.levelFogColor();
            PlanetCloudRenderer.INSTANCE.captureFog();
            return;
        }
        original.call(poses, view, projection, partialTick, x, y, z);
    }

    @WrapOperation(method = {"renderSnowAndRain", "tickRain"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float orbitalis$weather(ClientLevel level, float partialTick, Operation<Float> original) {
        return original.call(level, partialTick) * (1 - FlightSky.INSTANCE.weight(level, Minecraft.getInstance().gameRenderer.getMainCamera()));
    }
}
