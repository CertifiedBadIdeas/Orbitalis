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

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bypass Player's global-Y flight wrapper as well as LivingEntity's ordinary travel. */
@Mixin(Player.class)
public abstract class CubePlayerLocomotionMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void orbitalis$travel(Vec3 input, CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (CubePlayerPhysics.active(player)) {
            if (player.isControlledByLocalInstance()) CubePlayerPhysics.travel(player, input);
            callback.cancel();
        }
    }
}
