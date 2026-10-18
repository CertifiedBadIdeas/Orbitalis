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

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.cbi.orbitalis.minecraft.world.PlanetSizeSelection;
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Append to the native More tab's grid, allowing Minecraft to own layout and keyboard navigation. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
public abstract class PlanetSizeTabMixin extends GridLayoutTab {
    protected PlanetSizeTabMixin(Component title) { super(title); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void orbitalis$sizeControl(CreateWorldScreen screen, CallbackInfo callback) {
        PlanetSizeSelection selection = (PlanetSizeSelection) (Object) screen;
        var button = CycleButton.<Integer>builder(value -> Component.translatable("world.orbitalis.size.blocks", value))
            .withValues(WorldSystemSettings.Companion.getPRESETS())
            .withInitialValue(selection.getOrbitalisEarthWidth())
            .create(0, 0, 210, 20, Component.translatable("world.orbitalis.size"),
                (control, value) -> selection.setOrbitalisEarthWidth(value));
        button.setTooltip(Tooltip.create(Component.translatable("world.orbitalis.size.tooltip")));
        layout.addChild(button, 3, 0);
    }
}
