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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.cbi.orbitalis.minecraft.client.space.SurfaceCelestialSky;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class SurfaceCelestialSkyMixin {
    @Shadow private ClientLevel level;
    @Unique private boolean orbitalis$nativeBody;

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void orbitalis$reset(Matrix4f view, Matrix4f projection, float partialTick, Camera camera, boolean foggy, Runnable fog, CallbackInfo ci) {
        orbitalis$nativeBody = false;
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private void orbitalis$texture(int slot, ResourceLocation texture, Operation<Void> original) {
        orbitalis$nativeBody = slot == 0 && texture.getNamespace().equals("minecraft") &&
            (texture.getPath().equals("textures/environment/sun.png") || texture.getPath().equals("textures/environment/moon_phases.png"));
        original.call(slot, texture);
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void orbitalis$body(MeshData mesh, Operation<Void> original) {
        if (orbitalis$nativeBody && SurfaceCelestialSky.enabled(level)) mesh.close();
        else original.call(mesh);
    }

    @Inject(method = "renderSky", at = @At("TAIL"))
    private void orbitalis$physicalBodies(Matrix4f view, Matrix4f projection, float partialTick, Camera camera, boolean foggy, Runnable fog, CallbackInfo ci) {
        SurfaceCelestialSky.render(level, camera, view, projection, foggy);
    }
}
