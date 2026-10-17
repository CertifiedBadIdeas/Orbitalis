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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.cbi.orbitalis.minecraft.space.SpaceWorlds;
import org.cbi.orbitalis.minecraft.space.VacuumChunkData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Path;

/** Remains on the native region worker, after serialization and other mods' save callbacks. */
@Mixin(RegionFileStorage.class)
public abstract class VacuumRegionStorageMixin {
    @Shadow @Final private Path folder;
    @Shadow protected abstract RegionFile getRegionFile(ChunkPos pos) throws IOException;

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void orbitalisSkipNewVacuum(ChunkPos pos, CompoundTag tag, CallbackInfo ci) throws IOException {
        // Only exact terrain folders are registered. Entity/POI/Sable stores never match them.
        if (tag != null && SpaceWorlds.sparseRegion(folder) && VacuumChunkData.regenerable(tag)
                && !getRegionFile(pos).hasChunk(pos)) {
            ci.cancel();
        }
    }
}
