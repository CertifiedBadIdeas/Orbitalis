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

import net.minecraft.world.level.LevelSettings;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettingsOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test launcher has no creation screen or ordinary server-start hook. Never packaged in the mod. */
@Mixin(LevelSettings.class)
public abstract class WorldCreationFixtureMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void orbitalisFixtureSelection(CallbackInfo ci) {
        String suite = System.getProperty("orbitalis.gameTestSuite");
        if (suite != null) ((WorldSystemSettingsOwner) (Object) this).setOrbitalisWorldSettings(
            suite.equals("scaled") ? new WorldSystemSettings(1000).encode() : null);
    }
}
