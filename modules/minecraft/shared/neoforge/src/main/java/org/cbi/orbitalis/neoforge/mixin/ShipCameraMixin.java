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
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.client.ship.ShipCameraAccess;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

/** World-space camera basis after native Sable pose projection has finished. */
@Mixin(Camera.class)
public abstract class ShipCameraMixin implements ShipCameraAccess {
    @Shadow private Vec3 position;
    @Shadow @Final private BlockPos.MutableBlockPos blockPosition;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private float roll;
    @Shadow private boolean detached;

    @Override
    public void orbitalisSetView(Vec3 position, float yaw, float pitch) {
        this.position = position;
        this.blockPosition.set(position.x, position.y, position.z);
        this.xRot = pitch;
        this.yRot = yaw;
        this.roll = 0;
        this.detached = true;
        this.rotation.rotationYXZ((float) Math.PI - yaw * (float) Math.PI / 180f, -pitch * (float) Math.PI / 180f, 0);
        this.forwards.set(0, 0, -1).rotate(this.rotation);
        this.up.set(0, 1, 0).rotate(this.rotation);
        this.left.set(-1, 0, 0).rotate(this.rotation);
    }
}
