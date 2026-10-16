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

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.mixinterface.EntityExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adapt the native OBB solver's vanilla anchor conventions, only for cube players. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision", remap = false)
public abstract class SableCubeCollisionMixin {
    @Redirect(method = "collide", at = @At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/api/entity/EntitySubLevelUtil;getCustomEntityOrientation(Lnet/minecraft/world/entity/Entity;F)Lorg/joml/Quaterniondc;"))
    private static Quaterniondc orbitalis$orientation(Entity entity, float partialTick) {
        return CubePlayerPhysics.active(entity) ? new Quaterniond(CubePlayerPhysics.rotation(entity))
            : EntitySubLevelUtil.getCustomEntityOrientation(entity, partialTick);
    }

    @Redirect(method = {"collide", "getSubLevelEntityCollisionShape"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB orbitalis$unrotatedBounds(Entity entity) {
        if (!CubePlayerPhysics.active(entity)) return entity.getBoundingBox();
        Vec3 c = CubePlayerPhysics.centre(entity);
        double h = CubePlayerPhysics.halfHeight(entity);
        return new AABB(c.x - .3, c.y - h, c.z - .3, c.x + .3, c.y + h, c.z + .3);
    }

    @Inject(method = "transformEntityBoundsCenter", at = @At("HEAD"), cancellable = true)
    private static void orbitalis$centredBody(LevelReusedVectors sink, Quaterniondc orientation,
                                              Entity entity, Vector3d centre, CallbackInfo callback) {
        if (CubePlayerPhysics.active(entity)) callback.cancel();
    }

    @Redirect(method = "collide", at = @At(value = "INVOKE",
        target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getFeetPos(Lnet/minecraft/world/entity/Entity;FLorg/joml/Quaterniondc;)Lorg/joml/Vector3d;"))
    private static Vector3d orbitalis$feet(ActiveSableCompanion helper, Entity entity, float offset, Quaterniondc orientation) {
        if (!CubePlayerPhysics.active(entity)) return helper.getFeetPos(entity, offset, orientation);
        Vec3 feet = CubePlayerPhysics.centre(entity).subtract(CubePlayerPhysics.normal(entity).scale(CubePlayerPhysics.halfHeight(entity) + offset));
        return new Vector3d(feet.x, feet.y, feet.z);
    }

    // The first raw update is the solver's temporary feet position; the final update restores the
    // original network anchor and must pass through untouched.
    @Redirect(method = "collide", at = @At(value = "INVOKE", ordinal = 0,
        target = "Ldev/ryanhcode/sable/mixinterface/EntityExtension;sable$setPosSuperRaw(Lnet/minecraft/world/phys/Vec3;)V"))
    private static void orbitalis$temporaryAnchor(EntityExtension extension, Vec3 feet) {
        Entity entity = (Entity) extension;
        if (CubePlayerPhysics.active(entity)) {
            feet = feet.add(CubePlayerPhysics.normal(entity).scale(CubePlayerPhysics.halfHeight(entity)))
                .add(0, -CubePlayerPhysics.HALF_HEIGHT, 0);
        }
        extension.sable$setPosSuperRaw(feet);
    }
}
