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
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shared native inventory lookup for hoppers, droppers and crafters, including sided capabilities. */
@Mixin(value = VanillaInventoryCodeHooks.class, remap = false)
public abstract class PlanetInventoryMixin {
    @WrapOperation(method = {"getAttachedItemHandler", "getSourceItemHandler"}, at = @At(value = "INVOKE", target =
        "Lnet/neoforged/neoforge/items/VanillaInventoryCodeHooks;getItemHandlerAt(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/core/Direction;)Ljava/util/Optional;"))
    private static java.util.Optional<?> orbitalis$inventory(Level level, double x, double y, double z,
                                                            Direction side, Operation<java.util.Optional<?>> original) {
        BlockPos target = BlockPos.containing(x, y, z);
        var edge = PlanetNeighbours.neighbour(level, target.relative(side), side.getOpposite());
        if (edge == null) return original.call(level, x, y, z, side);
        if (!edge.getReady()) return java.util.Optional.empty();
        BlockPos actual = edge.getPosition();
        return original.call(level, actual.getX() + .5, actual.getY() + .5, actual.getZ() + .5,
            edge.getDirection().getOpposite());
    }
}
