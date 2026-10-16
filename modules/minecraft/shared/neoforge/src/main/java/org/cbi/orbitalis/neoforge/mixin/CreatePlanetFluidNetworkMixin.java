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
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.cbi.orbitalis.neoforge.integration.CreatePlanetFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidNetwork", remap = false)
public abstract class CreatePlanetFluidNetworkMixin {
    @Shadow Level world;
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getConnectedPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$target(BlockFace face, Operation<BlockPos> original) {
        return CreatePlanetFluid.target(world, face);
    }
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getOpposite()Lnet/createmod/catnip/math/BlockFace;"))
    private BlockFace orbitalis$peer(BlockFace face, Operation<BlockFace> original) {
        return CreatePlanetFluid.peer(world, face);
    }
}
