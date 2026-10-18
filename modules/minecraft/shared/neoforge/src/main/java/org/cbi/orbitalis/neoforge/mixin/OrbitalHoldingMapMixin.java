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

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.cbi.orbitalis.minecraft.integration.sable.SableOrbitalObjects;
import org.cbi.orbitalis.minecraft.integration.sable.SableHoldingMapAccess;
import org.cbi.orbitalis.minecraft.integration.sable.SableHoldingChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.UUID;

/** Optional native persistence boundary; direct transfer snapshot reconstruction remains separate. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap", remap = false)
public abstract class OrbitalHoldingMapMixin implements SableHoldingMapAccess {
    @Shadow @Final private ServerLevel level;
    @Shadow private SubLevelHoldingChunk getOrLoadHoldingChunk(ChunkPos pos, boolean create) { throw new AssertionError(); }
    @Shadow private void setDirty(ChunkPos pos) { throw new AssertionError(); }
    @Shadow public abstract void loadHoldingSubLevel(HoldingSubLevel holding);

    @Override
    public void orbitalisTakeAndLoad(ChunkPos owner, UUID id) {
        var chunk = getOrLoadHoldingChunk(owner, false);
        if (chunk == null) return;
        var detached = ((SableHoldingChunkAccess) chunk).orbitalisSnatch(id);
        if (detached == null) return;
        setDirty(owner);
        for (var holding : detached) loadHoldingSubLevel(holding);
    }

    @Inject(method = "moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V", at = @At("HEAD"))
    private void orbitalisCaptureSave(ServerSubLevel body, ChunkPos chunk, List<UUID> relations, CallbackInfo ci) {
        SableOrbitalObjects.INSTANCE.prepareNativeSave(body);
    }

    @Inject(method = "moveToUnloaded", at = @At("HEAD"))
    private void orbitalisCaptureUnload(ServerSubLevel body, ChunkPos chunk, CallbackInfo ci) {
        SableOrbitalObjects.INSTANCE.prepareNativeUnload(body);
    }

    @Inject(method = "saveAll", at = @At("RETURN"))
    private void orbitalisCheckpoint(CallbackInfo ci) {
        SableOrbitalObjects.INSTANCE.checkpoint(level);
    }

    @Inject(method = "loadHoldingSubLevel", at = @At("HEAD"), cancellable = true)
    private void orbitalisRestoreMotion(HoldingSubLevel holding, CallbackInfo ci) {
        if (!SableOrbitalObjects.INSTANCE.beforeNativeLoad(level, holding)) {
            // snatchAndLoad has already detached this payload from its native chunk. Return
            // ownership before postponing, so a save/restart can never lose the construction.
            ChunkPos owner = holding.data().getOriginLoadedChunk();
            if (owner == null) {
                var p = holding.data().pose().position();
                owner = new ChunkPos(BlockPos.containing(p.x, p.y, p.z));
            }
            getOrLoadHoldingChunk(owner, true).acceptHoldingSubLevel(holding);
            setDirty(owner);
            ci.cancel();
        }
    }

    @Inject(method = "loadHoldingSubLevel", at = @At("RETURN"))
    private void orbitalisExactMotion(HoldingSubLevel holding, CallbackInfo ci) {
        SableOrbitalObjects.INSTANCE.afterNativeLoad(level, holding);
    }
}
