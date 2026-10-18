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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import java.nio.file.Path;
import net.minecraft.world.level.LevelSettings;
import org.cbi.orbitalis.minecraft.world.PlanetSizeSelection;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettingsOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carry the chosen width with this creation screen, never in a process-global pending value. */
@Mixin(CreateWorldScreen.class)
public abstract class PlanetSizeCreationMixin implements PlanetSizeSelection {
    @Unique private int orbitalis$earthWidth = WorldSystemSettings.DEFAULT_WIDTH;
    @Override public int getOrbitalisEarthWidth() { return orbitalis$earthWidth; }
    @Override public void setOrbitalisEarthWidth(int value) {
        new WorldSystemSettings(value);
        orbitalis$earthWidth = value;
    }

    @Inject(method = "createFromExisting", at = @At("RETURN"))
    private static void orbitalis$recreate(Minecraft minecraft, Screen parent, LevelSettings settings,
                                           WorldCreationContext context, Path packs,
                                           CallbackInfoReturnable<CreateWorldScreen> callback) {
        var selected = WorldSystemSettings.decode(((WorldSystemSettingsOwner) (Object) settings).getOrbitalisWorldSettings());
        if (selected != null) {
            ((PlanetSizeSelection) (Object) callback.getReturnValue()).setOrbitalisEarthWidth(selected.getEarthWidth());
        }
    }

    @Inject(method = "createLevelSettings", at = @At("RETURN"))
    private void orbitalis$select(boolean debug, CallbackInfoReturnable<LevelSettings> callback) {
        ((WorldSystemSettingsOwner) (Object) callback.getReturnValue())
            .setOrbitalisWorldSettings(new WorldSystemSettings(orbitalis$earthWidth).encode());
    }
}
