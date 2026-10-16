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

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.cbi.orbitalis.minecraft.planet.PlanetFluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidInteractionRegistry.class)
public abstract class PlanetFluidInteractionMixin {
    @WrapMethod(method="canInteract",remap=false)
    private static boolean orbitalis$interaction(Level world, BlockPos pos, Operation<Boolean> original) {
        if(PlanetFluids.frame(world,pos) != null && !PlanetFluids.ready(world,pos,2)) return false;
        try { return original.call(world,pos); }
        catch(PlanetFluids.Unavailable unavailable) { return false; }
    }
    @ModifyExpressionValue(method="canInteract",remap=false,at=@At(value="FIELD",target="Lnet/minecraft/world/level/block/LiquidBlock;POSSIBLE_FLOW_DIRECTIONS:Lcom/google/common/collect/ImmutableList;",remap=true))
    private static ImmutableList<Direction> orbitalis$sides(ImmutableList<Direction> original, @Local(argsOnly=true) Level world, @Local(argsOnly=true) BlockPos pos) {
        if(PlanetFluids.down(world,pos) == Direction.DOWN) return original;
        return ImmutableList.<Direction>builder().add(PlanetFluids.down(world,pos)).addAll(PlanetFluids.sides(world,pos)).build();
    }
    @WrapOperation(method="canInteract",remap=false,at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;",remap=true))
    private static BlockPos orbitalis$relative(BlockPos pos, Direction d, Operation<BlockPos> original, @Local(argsOnly=true) Level world) {
        return PlanetFluids.offset(world,pos,d);
    }
    @WrapOperation(method="lambda$static$2",remap=false,at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;",remap=true))
    private static BlockPos orbitalis$soulSoil(BlockPos pos, Operation<BlockPos> original, @Local(argsOnly=true) Level world) {
        return PlanetFluids.offset(world,pos,PlanetFluids.down(world,pos));
    }
}
