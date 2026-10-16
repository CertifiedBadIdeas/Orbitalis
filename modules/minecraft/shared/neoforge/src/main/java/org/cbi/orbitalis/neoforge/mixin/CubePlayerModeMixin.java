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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.cbi.orbitalis.minecraft.lab.CubePlayerMode;
import org.cbi.orbitalis.minecraft.lab.CubePlayerModeData;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class CubePlayerModeMixin implements CubePlayerMode {
    @Unique private static final EntityDataAccessor<Boolean> orbitalis$enrolled =
        SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
    @Unique private boolean orbitalis$modeRecordSeen;

    @Override
    public boolean orbitalisCubeEnrolled() {
        return ((Player) (Object) this).getEntityData().get(orbitalis$enrolled);
    }

    @Override
    public void orbitalisSetCubeEnrolled(boolean enabled) {
        ((Player) (Object) this).getEntityData().set(orbitalis$enrolled, enabled);
        orbitalis$modeRecordSeen = true;
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void orbitalis$define(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(orbitalis$enrolled, false);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void orbitalis$save(CompoundTag tag, CallbackInfo callback) {
        CubePlayerModeData.write(tag, orbitalisCubeEnrolled());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void orbitalis$load(CompoundTag tag, CallbackInfo callback) {
        ((Player) (Object) this).getEntityData().set(orbitalis$enrolled, CubePlayerModeData.read(tag));
        orbitalis$modeRecordSeen = tag.contains(CubePlayerModeData.KEY);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void orbitalis$migrate(CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (player.level().isClientSide()) return;
        if (CubePlayerPhysics.active(player) && player.getAbilities().flying && player.isFallFlying()) player.stopFallFlying();
        if (player.level().dimension() != PlanetLabCoordinates.INSTANCE.getDIMENSION()) {
            if (orbitalisCubeEnrolled()) orbitalisSetCubeEnrolled(false);
        } else if (!orbitalis$modeRecordSeen && CubePlayerPhysics.centre(player).distanceToSqr(CubePlayerPhysics.CENTRE) < 64.0 * 64.0) {
            orbitalisSetCubeEnrolled(true);
        }
    }
}
