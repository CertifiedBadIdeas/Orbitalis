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

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.cbi.orbitalis.minecraft.client.CubePlayerView;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bracket every return, including a renderer event cancellation, with a balanced pose stack. */
@Mixin(PlayerRenderer.class)
public abstract class CubePlayerRendererMixin {
    /** Vanilla's glider bank compares horizontal vectors; supply them in the gravity frame. */
    @Redirect(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbitalis$localGliderLook(AbstractClientPlayer player, float partialTick) {
        Vec3 vector = player.getViewVector(partialTick);
        return CubePlayerPhysics.active(player) ? CubePlayerPhysics.local(vector, player) : vector;
    }

    @Redirect(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getDeltaMovementLerped(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbitalis$localGliderVelocity(AbstractClientPlayer player, float partialTick) {
        Vec3 vector = player.getDeltaMovementLerped(partialTick);
        return CubePlayerPhysics.active(player) ? CubePlayerPhysics.local(vector, player) : vector;
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void orbitalis$before(AbstractClientPlayer player, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (!CubePlayerPhysics.active(player)) return;
        poses.pushPose();
        poses.translate(0.0, CubePlayerPhysics.HALF_HEIGHT, 0.0);
        poses.mulPose(CubePlayerView.rotation(player, partialTick));
        poses.translate(0.0, -CubePlayerPhysics.halfHeight(player), 0.0);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void orbitalis$after(AbstractClientPlayer player, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (CubePlayerPhysics.active(player)) poses.popPose();
    }
}
