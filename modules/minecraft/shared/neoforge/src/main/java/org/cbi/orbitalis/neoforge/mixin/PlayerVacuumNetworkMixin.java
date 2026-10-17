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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace;
import org.cbi.orbitalis.minecraft.flight.PlayerGravity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Keep native movement validation, using already accepted client momentum in vacuum. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerVacuumNetworkMixin {
    @Shadow public ServerPlayer player;

    @Unique
    private boolean orbitalisVacuum() {
        return FlatPlanetSpace.INSTANCE.contains(player.level()) && PlayerGravity.vacuum(player) >= 0.99;
    }

    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbitalisExpectedVelocity(ServerPlayer entity, Operation<Vec3> original) {
        Vec3 nativeVelocity = original.call(entity);
        if (orbitalisVacuum() && !entity.isPassenger()) {
            Vec3 accepted = entity.getKnownMovement();
            if (accepted.lengthSqr() <= 250000.0 && accepted.lengthSqr() > nativeVelocity.lengthSqr()) return accepted;
        }
        return nativeVelocity;
    }

    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isFlightAllowed()Z"))
    private boolean orbitalisVacuumIsNotFloating(MinecraftServer server, Operation<Boolean> original) {
        return orbitalisVacuum() || original.call(server);
    }
}
