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

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.client.CubePlayerView;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply after the common mixin so these client HEAD cancellations precede its physical fallback. */
@Mixin(value = Entity.class, priority = 900)
public abstract class CubePlayerPickingMixin {
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void orbitalis$visualEye(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity == Minecraft.getInstance().getCameraEntity() && CubePlayerPhysics.active(entity)) {
            callback.setReturnValue(CubePlayerView.eye(entity, partialTick));
        }
    }

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void orbitalis$visualView(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity == Minecraft.getInstance().getCameraEntity() && CubePlayerPhysics.active(entity)) {
            callback.setReturnValue(CubePlayerView.view(entity, partialTick));
        }
    }
}
