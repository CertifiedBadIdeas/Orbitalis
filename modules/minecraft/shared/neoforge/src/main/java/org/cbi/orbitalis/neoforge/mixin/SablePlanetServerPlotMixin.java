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

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.cbi.orbitalis.minecraft.integration.sable.PlanetServerPlotAccess;
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetChunks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Collection;
import java.util.List;

@Pseudo
@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class SablePlanetServerPlotMixin implements PlanetServerPlotAccess {
    @Shadow @Final private Long2ObjectMap<BlockSubLevelLiftProvider.LiftProviderContext> liftProviders;

    @Override public void orbitalisRemoveLiftProviders(ChunkPos global) {
        liftProviders.keySet().removeIf(key -> new ChunkPos(BlockPos.of(key)).equals(global));
    }

    @WrapOperation(method = "save", at = @At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;getLoadedChunks()Ljava/util/Collection;"))
    private Collection<PlotChunkHolder> orbitalis$snapshotOneChunk(ServerLevelPlot plot, Operation<Collection<PlotChunkHolder>> original) {
        PlotChunkHolder selected = SablePlanetChunks.snapshotHolder(plot);
        return selected == null ? original.call(plot) : List.of(selected);
    }
}
