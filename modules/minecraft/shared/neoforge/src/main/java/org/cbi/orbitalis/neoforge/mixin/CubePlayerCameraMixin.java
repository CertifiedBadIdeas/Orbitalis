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

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.client.CubePlayerView;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CubePlayerCameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void move(float distance, float vertical, float horizontal);
    @Shadow private float getMaxZoom(float distance) { throw new AssertionError(); }

    @Inject(method = "setup", at = @At("RETURN"))
    private void orbitalis$orient(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo callback) {
        if (!CubePlayerPhysics.active(entity)) return;
        Quaternionf frame = CubePlayerView.rotation(entity);
        rotation.set(new Quaternionf(frame).mul(rotation));
        rotation.transform(new Vector3f(0f, 0f, -1f), forwards);
        rotation.transform(new Vector3f(0f, 1f, 0f), up);
        rotation.transform(new Vector3f(-1f, 0f, 0f), left);
        Vec3 centre = entity.getPosition(partialTick).add(0.0, CubePlayerPhysics.HALF_HEIGHT, 0.0);
        Vector3f offset = frame.transform(new Vector3f(0f, entity.getEyeHeight() - (float) CubePlayerPhysics.HALF_HEIGHT, 0f));
        Vec3 eye = centre.add(offset.x, offset.y, offset.z);
        // Clip the eased eye displacement so a face turn cannot put the camera through terrain.
        eye = entity.level().clip(new ClipContext(centre, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity)).getLocation();
        setPosition(eye);
        if (detached) move(-getMaxZoom(4f), 0f, 0f);
    }
}
