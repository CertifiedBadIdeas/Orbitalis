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

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame;
import org.joml.Quaternionf;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class CubePlayerEntityMixin implements CubePlayerFrame {
    @Unique private Direction orbitalis$up;
    @Unique private double orbitalis$yawRebase;
    @Unique private Quaternionf orbitalis$orientation;
    @Unique private double orbitalis$bodyHalfHeight;

    @Override
    public Direction orbitalisGetUp() { return orbitalis$up; }

    @Override
    public void orbitalisSetUp(Direction up) {
        orbitalis$up = up;
        orbitalis$orientation = up == null ? null : CubePlayerPhysics.rotation(up);
    }

    @Override
    public double orbitalisGetBodyHalfHeight() { return orbitalis$bodyHalfHeight; }

    @Override
    public void orbitalisSetBodyHalfHeight(double height) { orbitalis$bodyHalfHeight = height; }

    @Override
    public Quaternionf orbitalisGetOrientation() { return orbitalis$orientation == null ? null : new Quaternionf(orbitalis$orientation); }

    @Override
    public void orbitalisSetOrientation(Quaternionf value) { orbitalis$orientation = value == null ? null : new Quaternionf(value); }

    @Override
    public double orbitalisGetYawRebase() { return orbitalis$yawRebase; }

    @Override
    public void orbitalisSetYawRebase(double radians) { orbitalis$yawRebase = radians % (2.0 * Math.PI); }

    @Inject(method = "makeBoundingBox", at = @At("HEAD"), cancellable = true)
    private void orbitalis$box(CallbackInfoReturnable<AABB> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() != null && CubePlayerPhysics.active(entity)) {
            callback.setReturnValue(CubePlayerPhysics.box(entity));
        } else {
            orbitalis$up = null;
            orbitalis$orientation = null;
            orbitalis$bodyHalfHeight = 0.0;
        }
    }

    /** Native pose changes must not leave a vanilla global-Y box over the accepted collider. */
    @Inject(method = "refreshDimensions", at = @At("RETURN"))
    private void orbitalis$poseBounds(CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (CubePlayerPhysics.active(entity)) CubePlayerPhysics.refresh(entity);
    }

    /** Global Y is not planetary altitude; only opted-in scene players bypass vanilla void damage. */
    @Inject(method = "checkBelowWorld", at = @At("HEAD"), cancellable = true)
    private void orbitalis$planetVoid(CallbackInfo callback) {
        if (CubePlayerPhysics.active((Entity) (Object) this)) callback.cancel();
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void orbitalis$move(MoverType type, Vec3 movement, CallbackInfo callback) {
        Entity entity = (Entity) (Object) this;
        if (CubePlayerPhysics.active(entity)) {
            CubePlayerPhysics.move(entity, movement);
            callback.cancel();
        }
    }

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void orbitalis$eye(CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (CubePlayerPhysics.active(entity)) callback.setReturnValue(CubePlayerPhysics.eye(entity, 1f));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void orbitalis$eyeLerp(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (CubePlayerPhysics.active(entity)) callback.setReturnValue(CubePlayerPhysics.eye(entity, partialTick));
    }

    @Inject(method = "getLookAngle", at = @At("HEAD"), cancellable = true)
    private void orbitalis$look(CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (CubePlayerPhysics.active(entity)) callback.setReturnValue(CubePlayerPhysics.view(entity, 1f));
    }

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void orbitalis$view(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        if (CubePlayerPhysics.active(entity)) callback.setReturnValue(CubePlayerPhysics.view(entity, partialTick));
    }
}
