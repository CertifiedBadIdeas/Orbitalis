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

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A restored BE needs native pressure discovery: loading its block does not invoke onPlace. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidTransportBehaviour", remap = false)
public abstract class CreatePlanetFluidLifecycleMixin {
    @Inject(method = "initialize", at = @At("TAIL"))
    private void orbitalis$loaded(CallbackInfo callback) {
        var body = ((FluidTransportBehaviour) (Object) this).blockEntity;
        var level = body.getLevel();
        if (level != null && PlanetNeighbours.active(level))
            FluidPropagator.propagateChangedPipe(level, body.getBlockPos(), body.getBlockState());
    }
}
