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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.pipes.FluidPipeBlock", remap = false)
public abstract class CreatePlanetPipeBlockMixin {
    @WrapMethod(method = "canConnectTo")
    private static boolean orbitalis$connect(BlockAndTintGetter world, BlockPos position, BlockState state,
        Direction direction, Operation<Boolean> original) {
        var edge = world instanceof Level level ? PlanetNeighbours.signal(level, position, direction) : null;
        if (edge == null) return original.call(world, position, state, direction);
        return edge.getReady() && original.call(world, edge.getPosition(), world.getBlockState(edge.getPosition()), edge.getDirection());
    }
}
