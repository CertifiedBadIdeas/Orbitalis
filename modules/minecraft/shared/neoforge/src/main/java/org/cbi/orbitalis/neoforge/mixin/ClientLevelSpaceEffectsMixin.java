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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.cbi.orbitalis.neoforge.client.SpaceEffects;
import org.cbi.orbitalis.neoforge.client.SurfaceFlightEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Select presentation without rewriting persisted dimension types or changing the Overworld. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSpaceEffectsMixin {
    @Inject(method = "effects", at = @At("HEAD"), cancellable = true)
    private void orbitalis$spaceEffects(CallbackInfoReturnable<DimensionSpecialEffects> callback) {
        if (SpaceEffects.applies((ClientLevel) (Object) this)) {
            callback.setReturnValue(SpaceEffects.INSTANCE);
        }
    }

    @Inject(method = "effects", at = @At("RETURN"), cancellable = true)
    private void orbitalis$surfaceEffects(CallbackInfoReturnable<DimensionSpecialEffects> callback) {
        callback.setReturnValue(SurfaceFlightEffects.select((ClientLevel) (Object) this, callback.getReturnValue()));
    }
}
