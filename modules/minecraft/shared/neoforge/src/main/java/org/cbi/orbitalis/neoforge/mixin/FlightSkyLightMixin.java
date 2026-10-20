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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace;
import org.cbi.orbitalis.minecraft.client.space.CelestialSkylight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic external light: celestial occlusion precedes Sable's native plot/cabin combination. */
@Mixin(BlockAndTintGetter.class)
public interface FlightSkyLightMixin {
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void orbitalis$exposedSky(LightLayer layer, BlockPos position, CallbackInfoReturnable<Integer> callback) {
        if (layer == LightLayer.SKY && (Object) this instanceof ClientLevel level &&
            FlatPlanetSpace.INSTANCE.contains(level) && position.getY() >= level.getMaxBuildHeight()) {
            // Plot positions remain below the ceiling: Sable can still apply native cabin occlusion.
            callback.setReturnValue(CelestialSkylight.scale(level, position, 15));
        }
    }

    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private void orbitalis$celestialShadow(LightLayer layer, BlockPos position, CallbackInfoReturnable<Integer> callback) {
        if (layer == LightLayer.SKY && (Object) this instanceof ClientLevel level &&
            position.getY() < level.getMaxBuildHeight()) {
            callback.setReturnValue(CelestialSkylight.scale(level, position, callback.getReturnValueI()));
        }
    }
}
