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
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.cbi.orbitalis.neoforge.integration.CreatePlanetFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidPropagator", remap = false)
public abstract class CreatePlanetFluidPropagatorMixin {
    @WrapOperation(method = "propagateChangedPipe", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private static BlockPos orbitalis$propagate(BlockPos pos, Direction direction, Operation<BlockPos> original,
        @Local(argsOnly = true) LevelAccessor world, @Share("planetEdge") LocalRef<PlanetNeighbours.Edge> saved) {
        var edge = world instanceof Level level ? PlanetNeighbours.neighbour(level, pos, direction) : null;
        saved.set(edge);
        return edge != null && edge.getReady() ? edge.getPosition() : original.call(pos, direction);
    }
    @WrapOperation(method = "propagateChangedPipe", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getOpposite()Lnet/minecraft/core/Direction;"))
    private static Direction orbitalis$incoming(Direction direction, Operation<Direction> original,
        @Share("planetEdge") LocalRef<PlanetNeighbours.Edge> saved) {
        var edge = saved.get();
        return edge != null && edge.getReady() ? edge.getDirection().getOpposite() : original.call(direction);
    }
    // The second getAxis call compares the source-side direction with the target pump's native axis.
    @WrapOperation(method = "propagateChangedPipe", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getAxis()Lnet/minecraft/core/Direction$Axis;", ordinal = 1))
    private static Direction.Axis orbitalis$pumpAxis(Direction direction, Operation<Direction.Axis> original,
        @Share("planetEdge") LocalRef<PlanetNeighbours.Edge> saved) {
        var edge = saved.get();
        return edge != null && edge.getReady() ? edge.getDirection().getAxis() : original.call(direction);
    }
    @WrapOperation(method = "resetAffectedFluidNetworks", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private static BlockPos orbitalis$reset(BlockPos pos, Direction direction, Operation<BlockPos> original,
        @Local(argsOnly = true) Level world) {
        return CreatePlanetFluid.target(world, pos, direction);
    }
    @WrapMethod(method = "isOpenEnd")
    private static boolean orbitalis$open(BlockGetter world, BlockPos source, Direction direction, Operation<Boolean> original) {
        var edge = world instanceof Level level ? PlanetNeighbours.neighbour(level, source, direction) : null;
        if (edge == null) return original.call(world, source, direction);
        if (!edge.getReady()) return false;
        return original.call(world, edge.getPosition().relative(edge.getDirection().getOpposite()), edge.getDirection());
    }
    @WrapOperation(method = "validateNeighbourChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState orbitalis$changed(Level world, BlockPos queried, Operation<BlockState> original,
        @Local(argsOnly = true, ordinal = 0) BlockPos current) {
        var edge = PlanetNeighbours.notification(world, queried, current);
        return original.call(world, edge != null && edge.getReady() ? edge.getPosition() : queried);
    }
}
