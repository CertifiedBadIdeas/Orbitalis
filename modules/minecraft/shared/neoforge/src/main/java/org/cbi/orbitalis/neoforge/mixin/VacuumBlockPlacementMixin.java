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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.cbi.orbitalis.minecraft.space.SpaceWorlds;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** At the resolved chunk: Sable's world-to-plot routing has already selected the owning grid. */
@Mixin(LevelChunk.class)
public abstract class VacuumBlockPlacementMixin {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void orbitalisRequirePhysicalObject(BlockPos pos, BlockState state, boolean moved,
                                                CallbackInfoReturnable<BlockState> cir) {
        if (!state.isAir() && !SpaceWorlds.allowsBlock(level, pos)) {
            cir.setReturnValue(null);
        }
    }
}
