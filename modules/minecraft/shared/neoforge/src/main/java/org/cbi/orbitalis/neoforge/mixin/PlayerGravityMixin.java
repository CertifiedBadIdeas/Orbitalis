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

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.flight.PlayerGravity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/** Retains the native movement/collision pipeline; only field and air forces change. */
@Mixin(LivingEntity.class)
public abstract class PlayerGravityMixin {
    @Unique private boolean orbitalisActive;
    @Unique private boolean orbitalisCreative;
    @Unique private boolean orbitalisGliding;
    @Unique private double orbitalisVacuum;
    @Unique private double orbitalisTravelGravity;
    @Unique private int orbitalisAirAdds;
    @Unique private Vec3 orbitalisDrift;

    @WrapMethod(method = "travel")
    private void orbitalisTravel(Vec3 input, Operation<Void> original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        orbitalisActive = PlayerGravity.active(entity);
        if (!orbitalisActive) {
            original.call(input);
            return;
        }
        orbitalisCreative = PlayerGravity.creative(entity);
        orbitalisVacuum = PlayerGravity.vacuum(entity);
        orbitalisGliding = entity.isFallFlying() && !orbitalisCreative && orbitalisVacuum < 1.0;
        orbitalisAirAdds = 0;
        orbitalisDrift = null;
        orbitalisTravelGravity = -PlayerGravity.acceleration(entity).y * (1.0 - orbitalisVacuum);
        if (!orbitalisCreative) entity.setDeltaMovement(entity.getDeltaMovement().add(PlayerGravity.acceleration(entity).scale(0.5 * orbitalisVacuum)));
        try {
            original.call(input);
            if (!orbitalisCreative) {
                Vec3 acceleration = PlayerGravity.acceleration(entity);
                entity.setDeltaMovement(entity.getDeltaMovement().add(
                    acceleration.x * (1.0 - 0.5 * orbitalisVacuum),
                    acceleration.y * 0.5 * orbitalisVacuum,
                    acceleration.z * (1.0 - 0.5 * orbitalisVacuum)));
            }
        } finally {
            orbitalisActive = false;
        }
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getGravity()D"))
    private double orbitalisGravity(LivingEntity entity, Operation<Double> original) {
        return orbitalisActive ? (orbitalisCreative ? 0.0 : orbitalisTravelGravity) : original.call(entity);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
    private boolean orbitalisGliding(LivingEntity entity, Operation<Boolean> original) {
        return orbitalisActive ? orbitalisGliding : original.call(entity);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbitalisAerodynamics(Vec3 velocity, double x, double y, double z, Operation<Vec3> original) {
        if (orbitalisActive && orbitalisGliding) {
            if (orbitalisAirAdds++ == 0) {
                // Native gravity/lift already uses the atmospheric share of the field.
            } else {
                double density = 1.0 - orbitalisVacuum;
                x *= density;
                y *= density;
                z *= density;
            }
        }
        return original.call(velocity, x, y, z);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbitalisAirDrag(Vec3 velocity, double x, double y, double z, Operation<Vec3> original) {
        if (orbitalisActive) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (!entity.onGround()) {
                x += (1.0 - x) * orbitalisVacuum;
                z += (1.0 - z) * orbitalisVacuum;
            }
            y += (1.0 - y) * orbitalisVacuum;
        }
        return original.call(velocity, x, y, z);
    }

    @WrapOperation(method = {"travel", "handleRelativeFrictionAndCalculateMovement"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void orbitalisAirControl(LivingEntity entity, float speed, Vec3 input, Operation<Void> original) {
        if (orbitalisActive && !orbitalisCreative && !entity.onGround()) speed *= (float) (1.0 - orbitalisVacuum);
        original.call(entity, speed, input);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbitalisDrift(LivingEntity entity, Vec3 input, float friction, Operation<Vec3> original) {
        Vec3 velocity = original.call(entity, input, friction);
        if (orbitalisActive) orbitalisDrift = velocity;
        return velocity;
    }

    @WrapOperation(method = "travel", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;shouldDiscardFriction()Z")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void orbitalisNormalDrag(LivingEntity entity, double x, double y, double z, Operation<Void> original) {
        if (orbitalisActive && orbitalisDrift != null) {
            if (!entity.onGround()) {
                x += (orbitalisDrift.x - x) * orbitalisVacuum;
                z += (orbitalisDrift.z - z) * orbitalisVacuum;
            }
            y += (orbitalisDrift.y - y) * orbitalisVacuum;
        }
        original.call(entity, x, y, z);
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003), require = 3)
    private double orbitalisWeakVelocity(double original) {
        LivingEntity entity = (LivingEntity) (Object) this;
        return PlayerGravity.active(entity) && PlayerGravity.vacuum(entity) > 0.0 ? 0.0 : original;
    }
}
