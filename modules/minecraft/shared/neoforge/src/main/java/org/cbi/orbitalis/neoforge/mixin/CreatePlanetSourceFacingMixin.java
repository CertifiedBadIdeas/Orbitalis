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
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity", remap = false)
public abstract class CreatePlanetSourceFacingMixin {
    @WrapMethod(method = "getSourceFacing")
    private Direction orbitalis$source(Operation<Direction> original) {
        var body = (KineticBlockEntity) (Object) this;
        if (body.source != null) {
            var relation = PlanetNeighbours.relation(body.getLevel(), body.getBlockPos(), body.source);
            if (relation != null) {
                var delta = relation.getOffset();
                return Direction.getNearest(delta.getX(), delta.getY(), delta.getZ());
            }
        }
        return original.call();
    }
}
