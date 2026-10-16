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

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class CubePlayerTravelMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void orbitalis$jump(CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (CubePlayerPhysics.active(entity)) {
            if (!((Player) entity).getAbilities().flying) CubePlayerPhysics.jump((Player) entity);
            callback.cancel();
        }
    }
}
