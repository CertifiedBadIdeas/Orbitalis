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

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettingsOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Save settings beside native generation settings before a world can be reopened. */
@Mixin(PrimaryLevelData.class)
public abstract class WorldSystemSettingsSaveMixin {
    @Shadow private LevelSettings settings;

    @Inject(method = "setTagData", at = @At("TAIL"))
    private void orbitalis$save(RegistryAccess registries, CompoundTag root, CompoundTag player, CallbackInfo callback) {
        Tag value = ((WorldSystemSettingsOwner) (Object) settings).getOrbitalisWorldSettings();
        if (value != null) root.put(WorldSystemSettings.KEY, value);
    }
}
