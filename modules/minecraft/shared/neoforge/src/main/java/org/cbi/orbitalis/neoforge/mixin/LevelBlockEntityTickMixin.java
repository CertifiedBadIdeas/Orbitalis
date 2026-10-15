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

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferTickGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve ticking block-entity payloads while their ship's durable handoff is pending. */
@Mixin(Level.class)
public abstract class LevelBlockEntityTickMixin {
    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void orbitalis$tickUnlessTransferring(TickingBlockEntity ticker) {
        if (!ShipTransferTickGate.blocks((Level) (Object) this, ticker.getPos())) {
            ticker.tick();
        }
    }
}
