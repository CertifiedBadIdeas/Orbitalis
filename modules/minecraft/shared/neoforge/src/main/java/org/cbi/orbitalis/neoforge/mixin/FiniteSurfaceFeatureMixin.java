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

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class FiniteSurfaceFeatureMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void orbitalis$boundSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var bounds = FiniteSurfaceTerrain.bounds(level);
        if (bounds != null && !bounds.contains(entity.getBlockX(), entity.getBlockZ())) cir.setReturnValue(false);
    }

    @Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
    private void orbitalis$boundFeatures(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        var bounds = FiniteSurfaceTerrain.bounds(level);
        if (bounds != null && !bounds.contains(pos.getX(), pos.getZ())) cir.setReturnValue(false);
    }
}
