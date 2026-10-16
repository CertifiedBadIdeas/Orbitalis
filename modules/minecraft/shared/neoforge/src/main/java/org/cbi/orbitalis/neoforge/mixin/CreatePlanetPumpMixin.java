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
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.cbi.orbitalis.neoforge.integration.CreatePlanetFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.pump.PumpBlockEntity", remap = false)
public abstract class CreatePlanetPumpMixin {
    @WrapOperation(method = "distributePressureTo", at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/BlockFace;getConnectedPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$target(BlockFace face, Operation<BlockPos> original,
        @Share("planetEdge") LocalRef<PlanetNeighbours.Edge> saved) {
        var edge = CreatePlanetFluid.edge(((PumpBlockEntity) (Object) this).getLevel(), face);
        saved.set(edge);
        return edge != null && edge.getReady() ? edge.getPosition() : original.call(face);
    }
    @WrapOperation(method = {"distributePressureTo", "searchForEndpointRecursively"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getOpposite()Lnet/minecraft/core/Direction;"))
    private Direction orbitalis$incoming(Direction direction, Operation<Direction> original,
        @Share("planetEdge") LocalRef<PlanetNeighbours.Edge> saved) {
        var edge = saved.get();
        return edge != null && edge.getReady() ? edge.getDirection().getOpposite() : original.call(direction);
    }
    @WrapOperation(method = "searchForEndpointRecursively", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$search(BlockPos source, Direction direction, Operation<BlockPos> original,
        @Share("planetEdge") LocalRef<PlanetNeighbours.Edge> saved) {
        var edge = PlanetNeighbours.neighbour(((PumpBlockEntity) (Object) this).getLevel(), source, direction);
        saved.set(edge);
        return edge != null && edge.getReady() ? edge.getPosition() : original.call(source, direction);
    }
    @WrapOperation(method = "updatePressureChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbitalis$update(BlockPos source, Direction direction, Operation<BlockPos> original) {
        return CreatePlanetFluid.target(((PumpBlockEntity) (Object) this).getLevel(), source, direction);
    }
    @WrapMethod(method = "hasReachedValidEndpoint")
    private boolean orbitalis$endpoint(LevelAccessor world, BlockFace face, boolean pull, Operation<Boolean> original) {
        var edge = world instanceof net.minecraft.world.level.Level level ? CreatePlanetFluid.edge(level, face) : null;
        if (edge == null) return original.call(world, face, pull);
        return edge.getReady() && original.call(world, CreatePlanetFluid.query(edge), pull);
    }
}
