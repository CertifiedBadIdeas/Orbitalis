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
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineProvider;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline;
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", remap = false)
public abstract class SablePlanetPhysicsMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipelineProvider;createPipeline(Lnet/minecraft/server/level/ServerLevel;)Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"))
    private PhysicsPipeline orbitalis$terrainPipeline(PhysicsPipelineProvider provider, ServerLevel level, Operation<PhysicsPipeline> original) {
        PhysicsPipeline pipeline = original.call(provider, level);
        return level.dimension().equals(PlanetLabCoordinates.INSTANCE.getDIMENSION()) ? new SablePlanetCollisionPipeline(level, pipeline) : pipeline;
    }

    @Inject(method = "updateMassDataFromBlockChange", at = @At("HEAD"), cancellable = true)
    private void orbitalis$staticMass(SubLevel body, BlockPos pos, BlockState old, BlockState next, boolean force, CallbackInfo callback) {
        if (body instanceof ServerSubLevel server && SablePlanetCollisionPipeline.isStatic(server)) callback.cancel();
    }

    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void orbitalis$fixedPose(ServerSubLevel body, CallbackInfo callback) {
        if (SablePlanetCollisionPipeline.isStatic(body)) callback.cancel();
    }
}
