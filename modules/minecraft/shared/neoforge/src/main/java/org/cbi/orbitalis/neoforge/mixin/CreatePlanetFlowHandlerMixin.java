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
import com.llamalad7.mixinextras.sugar.Local;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.simibubi.create.foundation.ICapabilityProvider;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import java.lang.ref.WeakReference;
import org.cbi.orbitalis.neoforge.integration.CreatePlanetFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FlowSource$FluidHandler", remap = false)
public abstract class CreatePlanetFlowHandlerMixin {
    @Unique private WeakReference<BlockEntity> orbitalis$owner;

    @Inject(method = "manageSource", at = @At("HEAD"))
    private void orbitalis$owner(Level level, BlockEntity networkBE, CallbackInfo callback) {
        if (PlanetNeighbours.active(level)) orbitalis$owner = new WeakReference<>(networkBE);
    }

    @Inject(method = "provideHandler", at = @At("HEAD"), cancellable = true)
    private void orbitalis$removed(CallbackInfoReturnable<ICapabilityProvider<IFluidHandler>> callback) {
        if (orbitalis$owner == null) return;
        var owner = orbitalis$owner.get();
        // A native network may retain this endpoint until its next traversal. NeoForge permanently
        // disables its cache once the owning pipe is removed; never query that obsolete provider.
        if (owner == null || owner.isRemoved()) callback.setReturnValue(null);
    }

    @WrapOperation(method = "manageSource", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getConnectedPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$target(BlockFace face, Operation<BlockPos> original, @Local(argsOnly = true) Level world) {
        return CreatePlanetFluid.target(world, face);
    }
    @WrapOperation(method = "lambda$manageSource$2", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getOppositeFace()Lnet/minecraft/core/Direction;"))
    private Direction orbitalis$serverSide(BlockFace face, Operation<Direction> original, @Local(argsOnly = true) ServerLevel world) {
        return CreatePlanetFluid.incoming(world, face);
    }
    @WrapOperation(method = "lambda$manageSource$3", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getOppositeFace()Lnet/minecraft/core/Direction;"))
    private Direction orbitalis$previewSide(BlockFace face, Operation<Direction> original, @Local(argsOnly = true) Level world) {
        return CreatePlanetFluid.incoming(world, face);
    }
}
