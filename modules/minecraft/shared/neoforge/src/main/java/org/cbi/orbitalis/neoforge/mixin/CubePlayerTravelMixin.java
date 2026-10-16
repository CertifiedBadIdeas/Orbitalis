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
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.cbi.orbitalis.minecraft.lab.CubePlayerLocomotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class CubePlayerTravelMixin implements CubePlayerLocomotion {
    @Shadow protected abstract float getFrictionInfluencedSpeed(float friction);

    @Override
    public float orbitalisInputAcceleration(float friction) { return getFrictionInfluencedSpeed(friction); }

    @Override
    public float orbitalisSupportFriction() {
        LivingEntity entity = (LivingEntity) (Object) this;
        BlockPos pos = entity.getBlockPosBelowThatAffectsMyMovement();
        return entity.level().getBlockState(pos).getFriction(entity.level(), pos, entity);
    }

    /** Weak planetary acceleration must accumulate below vanilla's per-axis resting threshold. */
    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003), require = 3)
    private double orbitalis$keepWeakGravityMotion(double threshold) {
        return CubePlayerPhysics.active((LivingEntity) (Object) this) ? 0.0 : threshold;
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void orbitalis$flyingJump(CallbackInfo callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (CubePlayerPhysics.active(entity) && ((Player) entity).getAbilities().flying) callback.cancel();
    }

    /** Keep native attributes, jump boost and NeoForge's living-jump hook; only rotate the impulse. */
    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void orbitalis$jumpImpulse(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        if (CubePlayerPhysics.active(entity)) CubePlayerPhysics.jump((Player) entity, y);
        else original.call(entity, x, y, z);
    }

    @ModifyArg(method = "jumpFromGround", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), index = 0)
    private Vec3 orbitalis$sprintJump(Vec3 impulse) {
        LivingEntity entity = (LivingEntity) (Object) this;
        return CubePlayerPhysics.active(entity) ? CubePlayerPhysics.rotate(impulse, entity) : impulse;
    }
}
