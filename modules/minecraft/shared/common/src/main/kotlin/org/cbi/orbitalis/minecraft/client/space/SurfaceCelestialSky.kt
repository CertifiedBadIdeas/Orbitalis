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

package org.cbi.orbitalis.minecraft.client.space

import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import org.cbi.orbitalis.minecraft.lighting.SkyLighting
import org.joml.Matrix4f

object SurfaceCelestialSky {
    @JvmStatic fun enabled(level: ClientLevel): Boolean = SkyLighting.physicalOverworld(level)

    @JvmStatic fun render(
        level: ClientLevel,
        camera: Camera,
        view: Matrix4f,
        projection: Matrix4f,
        foggy: Boolean,
    ) {
        if (!enabled(level)) return
        SpaceSkyRenderer.render(level, camera, view, foggy, celestialsOnly = true, nativeProjection = projection)
    }
}
