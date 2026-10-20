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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.cbi.orbitalis.core.planet.Vector3;
import org.cbi.orbitalis.minecraft.client.space.FlightSky;
import org.cbi.orbitalis.minecraft.client.space.CelestialView;
import org.cbi.orbitalis.minecraft.client.space.SkyProjection;
import org.cbi.orbitalis.minecraft.client.space.SpaceScene;
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep finite-planet clouds and distant terrain inside one physical native depth range. */
@Mixin(GameRenderer.class)
public abstract class PlanetCloudProjectionMixin {
    @ModifyReturnValue(method = "getDepthFar", at = @At("RETURN"))
    private float orbitalis$planetDepth(float original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !CelestialView.INSTANCE.hasFrame(mc.level)) return original;
        var camera = mc.gameRenderer.getMainCamera();
        if (FlightSky.INSTANCE.weight(mc.level, camera) <= 0) return original;
        var p = camera.getPosition();
        var observer = FlightSpaceCoordinates.INSTANCE.globalPosition(mc.level, new Vector3(p.x, p.y, p.z));
        double extent = new Vector3(SpaceScene.INSTANCE.getHALF_SIZE(), 4096.0, SpaceScene.INSTANCE.getHALF_SIZE()).length();
        return SkyProjection.INSTANCE.nativeFar(original, (observer.length() + extent) * 2.0);
    }
}
