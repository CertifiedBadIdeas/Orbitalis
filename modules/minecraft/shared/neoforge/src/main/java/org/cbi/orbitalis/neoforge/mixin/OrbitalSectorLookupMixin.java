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

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.cbi.orbitalis.minecraft.flight.OrbitalSectors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Persisted player dimensions must be available before native login chooses a fallback world. */
@Mixin(MinecraftServer.class)
public abstract class OrbitalSectorLookupMixin {
    @Inject(method = "getLevel", at = @At("RETURN"), cancellable = true)
    private void orbitalisLookupSector(ResourceKey<Level> key, CallbackInfoReturnable<ServerLevel> callback) {
        if (callback.getReturnValue() == null) {
            ServerLevel level = OrbitalSectors.lookup((MinecraftServer) (Object) this, key);
            if (level != null) callback.setReturnValue(level);
        }
    }
}
