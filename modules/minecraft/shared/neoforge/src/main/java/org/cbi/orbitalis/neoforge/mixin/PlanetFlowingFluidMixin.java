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

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.cbi.orbitalis.minecraft.planet.PlanetFluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Native fluid algorithms, with physical-cell addressing and position-dependent cardinal gravity. */
@Mixin(FlowingFluid.class)
public abstract class PlanetFlowingFluidMixin {
    @Shadow protected abstract int getSlopeFindDistance(LevelReader world);

    @WrapMethod(method = "tick")
    private void orbitalis$tick(Level world, BlockPos pos, FluidState state, Operation<Void> original) {
        if (PlanetFluids.frame(world,pos) == null) {
            original.call(world,pos,state);
            return;
        }
        // A full candidate neighbourhood is required: a cold foreign chart cannot stand for air.
        if (!PlanetFluids.ready(world,pos,getSlopeFindDistance(world)+4)) {
            world.scheduleTick(pos,(Fluid)(Object)this,((Fluid)(Object)this).getTickDelay(world));
            return;
        }
        try {
            if (!FluidInteractionRegistry.canInteract(world,pos)) original.call(world,pos,state);
        } catch (PlanetFluids.Unavailable unavailable) {
            world.scheduleTick(pos,(Fluid)(Object)this,((Fluid)(Object)this).getTickDelay(world));
        }
    }

    @WrapOperation(method = {"getNewLiquid", "getSpread", "spreadToSides"}, at = @At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$relative(BlockPos pos, Direction d, Operation<BlockPos> original, @Local(argsOnly=true) Level world) {
        return PlanetFluids.offset(world,pos,d);
    }
    @WrapOperation(method = {"getSlopeDistance", "sourceNeighborCount"}, at = @At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$probe(BlockPos pos, Direction d, Operation<BlockPos> original, @Local(argsOnly=true) LevelReader world) {
        return PlanetFluids.offset(world,pos,d);
    }
    @WrapOperation(method = {"spread", "getNewLiquid", "getSpread"}, at = @At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$below(BlockPos pos, Operation<BlockPos> original, @Local(argsOnly=true) Level world) {
        return PlanetFluids.offset(world,pos,PlanetFluids.down(world,pos));
    }
    @WrapOperation(method = "method_15743", at = @At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$slopeBelow(BlockPos pos, Operation<BlockPos> original, @Local(argsOnly=true) LevelReader world) {
        return PlanetFluids.offset(world,pos,PlanetFluids.down(world,pos));
    }
    @WrapOperation(method = "getNewLiquid", at = @At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;above()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$above(BlockPos pos, Operation<BlockPos> original, @Local(argsOnly=true) Level world) {
        return PlanetFluids.offset(world,pos,PlanetFluids.down(world,pos).getOpposite());
    }
    @WrapOperation(method = {"getNewLiquid", "getSpread"}, at = @At(value="INVOKE",target="Lnet/minecraft/core/Direction$Plane;iterator()Ljava/util/Iterator;"))
    private Iterator<Direction> orbitalis$sides(Direction.Plane plane, Operation<Iterator<Direction>> original,
        @Local(argsOnly=true) Level world, @Local(argsOnly=true) BlockPos pos) {
        return PlanetFluids.down(world,pos) == Direction.DOWN ? original.call(plane) : PlanetFluids.sides(world,pos).iterator();
    }
    @WrapOperation(method = {"getSlopeDistance", "sourceNeighborCount"}, at = @At(value="INVOKE",target="Lnet/minecraft/core/Direction$Plane;iterator()Ljava/util/Iterator;"))
    private Iterator<Direction> orbitalis$probeSides(Direction.Plane plane, Operation<Iterator<Direction>> original,
        @Local(argsOnly=true) LevelReader world, @Local(argsOnly=true,ordinal=0) BlockPos pos) {
        return PlanetFluids.down(world,pos) == Direction.DOWN ? original.call(plane) : PlanetFluids.sides(world,pos).iterator();
    }
    @WrapOperation(method = "getSpread", at = @At(value="INVOKE",target="Lnet/minecraft/world/level/material/FlowingFluid;getCacheKey(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)S"))
    private short orbitalis$key(BlockPos origin, BlockPos target, Operation<Short> original, @Local(argsOnly=true) Level world) {
        return PlanetFluids.cacheKey(world,origin,target,original.call(origin,target));
    }
    @WrapOperation(method = "getSlopeDistance", at = @At(value="INVOKE",target="Lnet/minecraft/world/level/material/FlowingFluid;getCacheKey(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)S"))
    private short orbitalis$probeKey(BlockPos origin, BlockPos target, Operation<Short> original, @Local(argsOnly=true) LevelReader world) {
        return PlanetFluids.cacheKey(world,origin,target,original.call(origin,target));
    }

    @WrapOperation(method = {"getSpread", "getSlopeDistance"}, at = @At(value="INVOKE",target="Lnet/minecraft/world/level/material/FlowingFluid;getSlopeDistance(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;ILnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lit/unimi/dsi/fastutil/shorts/Short2ObjectMap;Lit/unimi/dsi/fastutil/shorts/Short2BooleanMap;)I"))
    private int orbitalis$recurse(FlowingFluid fluid, LevelReader world, BlockPos target, int depth, Direction incoming,
        BlockState state, BlockPos origin, Short2ObjectMap<Pair<BlockState,FluidState>> blocks, Short2BooleanMap holes,
        Operation<Integer> original, @Local(argsOnly=true,ordinal=0) BlockPos source) {
        return original.call(fluid,world,target,depth,PlanetFluids.recursiveDirection(world,source,target,incoming),state,origin,blocks,holes);
    }

    @WrapMethod(method="canPassThroughWall")
    private boolean orbitalis$wall(Direction d, BlockGetter world, BlockPos from, BlockState a, BlockPos to, BlockState b, Operation<Boolean> original) {
        Boolean result = PlanetFluids.passWall(world,from,a,to,b);
        return result != null ? result : original.call(PlanetFluids.wallDirection(world,from,to,d),world,from,a,to,b);
    }
    @WrapOperation(method="canSpreadTo",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;canBeReplacedWith(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/Direction;)Z"))
    private boolean orbitalis$replacement(FluidState state, BlockGetter world, BlockPos target, Fluid fluid, Direction d,
        Operation<Boolean> original, @Local(argsOnly=true,ordinal=0) BlockPos source) {
        return PlanetFluids.withIncomingDown(world,source,target,
            () -> original.call(state,world,target,fluid,PlanetFluids.arrival(world,source,target,d)));
    }
    @WrapOperation(method={"spread", "spreadToSides"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FlowingFluid;spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V"))
    private void orbitalis$spreadDirection(FlowingFluid fluid, LevelAccessor world, BlockPos target, BlockState state, Direction d,
        FluidState next, Operation<Void> original, @Local(argsOnly=true) BlockPos source) {
        PlanetFluids.withIncomingDown(world,source,target,() -> {
            original.call(fluid,world,target,state,PlanetFluids.arrival(world,source,target,d),next);
            return null;
        });
    }
}
