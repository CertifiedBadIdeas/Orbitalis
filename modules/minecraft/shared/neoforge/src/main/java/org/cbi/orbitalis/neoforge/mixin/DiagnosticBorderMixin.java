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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Orbital diagnostics have independent space coordinates and must not inherit the surface wall. */
@Mixin(MinecraftServer.class)
public abstract class DiagnosticBorderMixin {
    @WrapOperation(method = "createLevels", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/border/WorldBorder;addListener(Lnet/minecraft/world/level/border/BorderChangeListener;)V"))
    private void orbitalis$independentDiagnosticBorder(WorldBorder border, BorderChangeListener listener,
            Operation<Void> original, @Local(ordinal = 1) ServerLevel target) {
        if (!FlightSpaceCoordinates.INSTANCE.hasIndependentBorder(target)) {
            original.call(border, listener);
        }
    }
}
