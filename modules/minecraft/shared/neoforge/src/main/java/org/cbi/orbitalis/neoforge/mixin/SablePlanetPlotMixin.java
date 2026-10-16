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

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.cbi.orbitalis.minecraft.integration.sable.PlanetPlotAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;

@Pseudo
@Mixin(value = LevelPlot.class, remap = false)
public abstract class SablePlanetPlotMixin implements PlanetPlotAccess {
    @Shadow @Final private PlotChunkHolder[] chunks;
    @Shadow @Final private List<PlotChunkHolder> loadedChunks;
    @Shadow @Final protected int logSize;
    @Shadow @Final private SubLevel subLevel;
    @Shadow @Final protected Object2ObjectOpenHashMap<BlockPos, BlockEntitySubLevelActor> blockEntityActors;
    @Shadow @Final private Object2ObjectOpenHashMap<BlockPos, BlockEntitySubLevelReactionWheel> blockEntityReactionWheels;
    @Shadow protected boolean expandPlotIfNecessary;
    @Shadow protected abstract void onRemoveChunkHolder(LevelChunk chunk);

    @Override public int orbitalisLogSize() { return logSize; }
    @Override public void orbitalisDisableExpansion() { expandPlotIfNecessary = false; }

    @Override public void orbitalisDetachChunk(ChunkPos local) {
        LevelPlot plot = (LevelPlot)(Object)this;
        PlotChunkHolder holder = plot.getChunkHolder(local);
        if (holder == null) return;
        ChunkPos global = holder.getPos();
        LevelChunk chunk = holder.getChunk();
        blockEntityActors.keySet().removeIf(pos -> new ChunkPos(pos).equals(global));
        blockEntityReactionWheels.entrySet().removeIf(entry -> {
            if (!new ChunkPos(entry.getKey()).equals(global)) return false;
            if (subLevel instanceof ServerSubLevel server)
                server.getReactionWheelManager().wheelChanged(entry.getKey(), entry.getValue(), false);
            return true;
        });
        if (plot instanceof org.cbi.orbitalis.minecraft.integration.sable.PlanetServerPlotAccess access)
            access.orbitalisRemoveLiftProviders(global);
        onRemoveChunkHolder(chunk);
        chunks[local.x + (local.z << logSize)] = null;
        loadedChunks.remove(holder);
        plot.updateBoundingBox();
    }
}
