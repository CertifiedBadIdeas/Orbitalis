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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the native redstone implementation, but query its actual source and local direction. */
@Mixin(SignalGetter.class)
public interface PlanetSignalMixin {
    @WrapMethod(method = {"getSignal", "getDirectSignal"})
    default int orbitalis$signal(BlockPos position, Direction direction, Operation<Integer> original) {
        if ((Object) this instanceof Level level) {
            var edge = PlanetNeighbours.signal(level, position, direction);
            if (edge != null) {
                if (!edge.getReady()) return 0;
                return original.call(edge.getPosition(), edge.getDirection());
            }
        }
        return original.call(position, direction);
    }
}
