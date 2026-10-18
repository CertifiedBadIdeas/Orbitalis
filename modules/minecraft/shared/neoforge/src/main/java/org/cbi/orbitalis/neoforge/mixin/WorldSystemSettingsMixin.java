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

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettingsOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep creation intent with native immutable settings, including every native copy path. */
@Mixin(LevelSettings.class)
public abstract class WorldSystemSettingsMixin implements WorldSystemSettingsOwner {
    @Unique private Tag orbitalis$settings = new WorldSystemSettings().encode();

    @Override public Tag getOrbitalisWorldSettings() {
        return orbitalis$settings == null ? null : orbitalis$settings.copy();
    }
    @Override public void setOrbitalisWorldSettings(Tag value) {
        orbitalis$settings = value == null ? null : value.copy();
    }

    @Inject(method = "parse", at = @At("RETURN"))
    private static void orbitalis$read(Dynamic<?> tag, WorldDataConfiguration config,
                                      CallbackInfoReturnable<LevelSettings> callback) {
        var raw = tag.get(WorldSystemSettings.KEY).result();
        Tag settings = null;
        if (raw.isPresent()) {
            var value = raw.get().convert(NbtOps.INSTANCE).getValue();
            // Preserve invalid records too; the domain boundary rejects their interpretation.
            settings = value;
        }
        ((WorldSystemSettingsOwner) (Object) callback.getReturnValue()).setOrbitalisWorldSettings(settings);
    }

    @Inject(method = {"copy", "withGameType", "withDifficulty", "withDataConfiguration"}, at = @At("RETURN"))
    private void orbitalis$copy(CallbackInfoReturnable<LevelSettings> callback) {
        ((WorldSystemSettingsOwner) (Object) callback.getReturnValue()).setOrbitalisWorldSettings(orbitalis$settings);
    }
}
