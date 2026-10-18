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

import net.minecraft.world.entity.Entity;
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exterior flight around the finite body has no terrain void floor. Other hazards remain native. */
@Mixin(Entity.class)
public abstract class SpaceVoidFloorMixin {
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void orbitalisNoSpaceVoidFloor(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (FlatPlanetSpace.INSTANCE.contains(entity.level())) {
            callback.cancel();
        }
    }
}
