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
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.cbi.orbitalis.minecraft.client.CubePlayerView;
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Absolute server teleports carry destination-chart yaw, not a heading to transport from the old site. */
@Mixin(ClientPacketListener.class)
public abstract class CubePlayerTeleportMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void orbitalis$destinationFrame(ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
        if (minecraft.player == null || !CubePlayerPhysics.active(minecraft.player)) return;
        CubePlayerFrame state = (CubePlayerFrame) minecraft.player;
        state.orbitalisSetUp(null);
        state.orbitalisSetBodyHalfHeight(0.0);
        CubePlayerPhysics.refresh(minecraft.player);
        CubePlayerView.INSTANCE.forget(minecraft.player.getUUID());
    }
}
