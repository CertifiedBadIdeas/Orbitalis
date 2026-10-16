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

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's floating counter assumes global -Y; collision/movement validation still runs. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CubePlayerPacketMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void orbitalis$localUp(CallbackInfo callback) {
        if (CubePlayerPhysics.active(player)) {
            clientIsFloating = false;
            aboveGroundTickCount = 0;
        }
    }
}
