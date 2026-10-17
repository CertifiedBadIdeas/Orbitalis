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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla broadcasts surface-border edits globally, even to dimensions with independent borders. */
@Mixin(PlayerList.class)
public abstract class DiagnosticBorderPacketsMixin {
    @WrapOperation(method = "sendLevelInfo", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getWorldBorder()Lnet/minecraft/world/level/border/WorldBorder;"))
    private WorldBorder orbitalis$initialDiagnosticBorder(ServerLevel vanillaLevel, Operation<WorldBorder> original,
            @Local(argsOnly = true) ServerLevel destination) {
        return FlightSpaceCoordinates.INSTANCE.hasIndependentBorder(destination) ? destination.getWorldBorder() : original.call(vanillaLevel);
    }

    @Inject(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void orbitalis$surfaceBorderRecipients(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundSetBorderSizePacket || packet instanceof ClientboundSetBorderLerpSizePacket
                || packet instanceof ClientboundSetBorderCenterPacket || packet instanceof ClientboundSetBorderWarningDelayPacket
                || packet instanceof ClientboundSetBorderWarningDistancePacket) {
            for (var player : ((PlayerList) (Object) this).getPlayers()) {
                if (!FlightSpaceCoordinates.INSTANCE.hasIndependentBorder(player.serverLevel())) {
                    player.connection.send(packet);
                }
            }
            ci.cancel();
        }
    }
}
