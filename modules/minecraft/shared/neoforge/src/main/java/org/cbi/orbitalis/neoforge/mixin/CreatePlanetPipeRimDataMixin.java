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

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.cbi.orbitalis.neoforge.integration.CreatePlanetPipeRims;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuse native initial chunk tags and behaviour updates; never persist presentation modes. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.FluidTransportBehaviour", remap = false)
public abstract class CreatePlanetPipeRimDataMixin implements CreatePlanetPipeRims.Data {
    @Unique private volatile int orbitalis$pipeRims;

    @Override
    public int orbitalis$getPipeRims() { return orbitalis$pipeRims; }

    @Inject(method = "write", at = @At("RETURN"))
    private void orbitalis$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo callback) {
        if (clientPacket) CreatePlanetPipeRims.write(tag, (FluidTransportBehaviour) (Object) this);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void orbitalis$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo callback) {
        int next = clientPacket ? CreatePlanetPipeRims.read(tag) : 0;
        if (next == orbitalis$pipeRims) return;
        orbitalis$pipeRims = next;
        var body = ((FluidTransportBehaviour) (Object) this).blockEntity;
        var level = body.getLevel();
        if (level != null && level.isClientSide) {
            body.requestModelDataUpdate();
            level.sendBlockUpdated(body.getBlockPos(), body.getBlockState(), body.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
