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
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.ServerSubLevel", remap = false)
public abstract class SablePlanetBodyMixin {
    @Unique private MassTracker orbitalis$staticMass;

    @Unique private MassTracker orbitalis$mass() {
        if (orbitalis$staticMass == null) orbitalis$staticMass = SablePlanetCollisionPipeline.mass((ServerSubLevel) (Object) this);
        return orbitalis$staticMass;
    }

    @Inject(method = "getSelfMassTracker", at = @At("HEAD"), cancellable = true)
    private void orbitalis$fixedSelfMass(CallbackInfoReturnable<MassTracker> callback) {
        if (SablePlanetCollisionPipeline.isStatic((ServerSubLevel) (Object) this)) callback.setReturnValue(orbitalis$mass());
    }

    @Inject(method = "getMassTracker", at = @At("HEAD"), cancellable = true)
    private void orbitalis$fixedMass(CallbackInfoReturnable<MassData> callback) {
        if (SablePlanetCollisionPipeline.isStatic((ServerSubLevel) (Object) this)) callback.setReturnValue(orbitalis$mass());
    }

    @Inject(method = "buildMassTracker", at = @At("HEAD"), cancellable = true)
    private void orbitalis$skipMassBuild(CallbackInfo callback) {
        if (SablePlanetCollisionPipeline.isStatic((ServerSubLevel) (Object) this)) callback.cancel();
    }

    @Inject(method = "updateMergedMassData", at = @At("HEAD"), cancellable = true)
    private void orbitalis$skipMassUpdate(float partial, CallbackInfo callback) {
        if (SablePlanetCollisionPipeline.isStatic((ServerSubLevel) (Object) this)) callback.cancel();
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/plot/heat/SubLevelHeatMapManager;tick()V"))
    private void orbitalis$keepChartWhole(SubLevelHeatMapManager heat, Operation<Void> original) {
        if (!SablePlanetCollisionPipeline.isStatic((ServerSubLevel) (Object) this)) original.call(heat);
    }
}
