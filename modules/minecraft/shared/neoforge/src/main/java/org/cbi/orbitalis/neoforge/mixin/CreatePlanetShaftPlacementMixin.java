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
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Retain Create's native preferred-axis algorithm while querying actual foreign shaft ports. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock", remap = false)
public abstract class CreatePlanetShaftPlacementMixin {
    @WrapOperation(method = "getPreferredAxis", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private static BlockPos orbitalis$neighbour(BlockPos pos, Direction side, Operation<BlockPos> original,
            @Local(argsOnly = true) BlockPlaceContext context) {
        var edge = PlanetNeighbours.neighbour(context.getLevel(), pos, side);
        return edge != null && edge.getReady() ? edge.getPosition() : original.call(pos, side);
    }

    @WrapOperation(method = "getPreferredAxis", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/IRotate;hasShaftTowards(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private static boolean orbitalis$port(IRotate block, LevelReader world, BlockPos pos, BlockState state,
            Direction incoming, Operation<Boolean> original, @Local(argsOnly = true) BlockPlaceContext context) {
        var edge = PlanetNeighbours.neighbour(context.getLevel(), context.getClickedPos(), incoming.getOpposite());
        if (edge == null) return original.call(block, world, pos, state, incoming);
        return edge.getReady() && original.call(block, world, edge.getPosition(), state, edge.getDirection().getOpposite());
    }
}
