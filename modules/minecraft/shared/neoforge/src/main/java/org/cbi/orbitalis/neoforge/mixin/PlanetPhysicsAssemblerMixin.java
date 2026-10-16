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
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Fixed chart storage is terrain for the assembler, while native assembly retains its parent pose. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity", remap = false)
public abstract class PlanetPhysicsAssemblerMixin {
    @WrapOperation(method = {"assembleOrDisassemble", "getSubLevel"}, at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getContaining(Lnet/minecraft/world/level/block/entity/BlockEntity;)Ldev/ryanhcode/sable/sublevel/SubLevel;"))
    private SubLevel orbitalis$terrain(ActiveSableCompanion companion, BlockEntity assembler, Operation<SubLevel> original) {
        SubLevel body = original.call(companion, assembler);
        return body instanceof ServerSubLevel server && SablePlanetCollisionPipeline.isStatic(server) ? null : body;
    }
}
