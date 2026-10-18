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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.Level;
import org.cbi.orbitalis.minecraft.client.space.FlightSky;
import org.cbi.orbitalis.minecraft.client.space.SpaceSkyRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Composite the same orbital scene before native travel, leaving ground/other dimensions native. */
@Mixin(LevelRenderer.class)
public abstract class FlightSkyRendererMixin {
    @Shadow private ClientLevel level;

    @WrapMethod(method = "renderSky")
    private void orbitalis$sky(Matrix4f view, Matrix4f projection, float partialTick, Camera camera,
                               boolean foggy, Runnable setupFog, Operation<Void> original) {
        float weight = FlightSky.INSTANCE.weight(level, camera);
        if (level.dimension() != Level.OVERWORLD || weight <= 0) {
            original.call(view, projection, partialTick, camera, foggy, setupFog);
            return;
        }
        if (weight < 1) original.call(view, projection, partialTick, camera, foggy, setupFog);
        setupFog.run();
        SpaceSkyRenderer.INSTANCE.render(level, camera, view, foggy, weight);
    }

    @WrapOperation(method = "renderClouds", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void orbitalis$clouds(VertexBuffer buffer, Matrix4f view, Matrix4f projection, ShaderInstance shader,
                                  Operation<Void> original) {
        float weight = FlightSky.INSTANCE.weight(level, Minecraft.getInstance().gameRenderer.getMainCamera());
        if (weight >= 1) return;
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3] * (1 - weight));
            original.call(buffer, view, projection, shader);
        } finally {
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    @WrapOperation(method = {"renderSnowAndRain", "tickRain"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float orbitalis$weather(ClientLevel level, float partialTick, Operation<Float> original) {
        return original.call(level, partialTick) * (1 - FlightSky.INSTANCE.weight(level, Minecraft.getInstance().gameRenderer.getMainCamera()));
    }
}
