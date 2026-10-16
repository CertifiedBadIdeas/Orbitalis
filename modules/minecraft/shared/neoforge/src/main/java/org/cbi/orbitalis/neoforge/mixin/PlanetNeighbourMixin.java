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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Transform at native dispatch, preserving the normal queued update limit and order. */
@Mixin(NeighborUpdater.class)
public interface PlanetNeighbourMixin {
    @WrapOperation(method = "executeUpdate", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/block/state/BlockState;handleNeighborChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"))
    private static void orbitalis$notify(BlockState state, Level level, BlockPos position, Block block,
                                         BlockPos source, boolean moved, Operation<Void> original) {
        var edge = PlanetNeighbours.notification(level, position, source);
        if (edge == null) {
            original.call(state, level, position, block, source, moved);
        } else if (edge.getReady()) {
            BlockPos actual = edge.getPosition();
            // The changed neighbour's position is expressed in the destination's coordinate frame.
            original.call(level.getBlockState(actual), level, actual, block,
                actual.relative(edge.getDirection().getOpposite()), moved);
        }
    }
}
