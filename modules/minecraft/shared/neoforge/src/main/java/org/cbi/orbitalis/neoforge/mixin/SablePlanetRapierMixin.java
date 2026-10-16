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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the backend's public scene lookup working when its pipeline has a planet adapter. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D", remap = false)
public abstract class SablePlanetRapierMixin {
    @ModifyExpressionValue(method = "getSceneHandle", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;getPipeline()Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"))
    private static PhysicsPipeline orbitalis$nativeBackend(PhysicsPipeline pipeline) { return SablePlanetCollisionPipeline.unwrap(pipeline); }
}
