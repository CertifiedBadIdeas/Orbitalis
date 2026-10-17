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

package org.cbi.orbitalis.neoforge.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.client.renderer.LightTexture
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.minecraft.client.space.SpaceSkyRenderer
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.joml.Matrix4f
import org.joml.Vector3f

/** Native NeoForge sky hooks selected by dimension ID, including existing diagnostic saves. */
object SpaceEffects : DimensionSpecialEffects(Float.NaN, false, SkyType.NONE, false, false) {
    @JvmStatic
    fun applies(level: ClientLevel): Boolean = level.dimension() == FlightTestSpace.ORBIT || level.dimension() == FlightTestSpace.SPACE

    override fun getBrightnessDependentFogColor(
        color: Vec3,
        brightness: Float,
    ): Vec3 = Vec3.ZERO

    override fun isFoggyAt(
        x: Int,
        z: Int,
    ): Boolean = false

    override fun getSunriseColor(
        time: Float,
        partialTick: Float,
    ): FloatArray? = null

    override fun renderSky(
        level: ClientLevel,
        ticks: Int,
        partialTick: Float,
        modelViewMatrix: Matrix4f,
        camera: Camera,
        projectionMatrix: Matrix4f,
        isFoggy: Boolean,
        setupFog: Runnable,
    ): Boolean {
        setupFog.run()
        SpaceSkyRenderer.render(level, camera, modelViewMatrix, isFoggy)
        return true
    }

    override fun renderClouds(
        level: ClientLevel,
        ticks: Int,
        partialTick: Float,
        poseStack: PoseStack,
        camX: Double,
        camY: Double,
        camZ: Double,
        modelViewMatrix: Matrix4f,
        projectionMatrix: Matrix4f,
    ): Boolean = true

    override fun renderSnowAndRain(
        level: ClientLevel,
        ticks: Int,
        partialTick: Float,
        lightTexture: LightTexture,
        camX: Double,
        camY: Double,
        camZ: Double,
    ): Boolean = true

    override fun tickRain(
        level: ClientLevel,
        ticks: Int,
        camera: Camera,
    ): Boolean = true

    override fun adjustLightmapColors(
        level: ClientLevel,
        partialTicks: Float,
        skyDarken: Float,
        blockLightRedFlicker: Float,
        skyLight: Float,
        pixelX: Int,
        pixelY: Int,
        colors: Vector3f,
    ) {
        // Constant exposed daylight, with native skylight occlusion and warm local block lights.
        // Night vision, darkness and gamma are still processed by vanilla after this hook.
        val sky = LightTexture.getBrightness(level.dimensionType(), pixelY)
        val block = LightTexture.getBrightness(level.dimensionType(), pixelX) * blockLightRedFlicker
        val green = block * ((block * 0.6f + 0.4f) * 0.6f + 0.4f)
        val blue = block * (block * block * 0.6f + 0.4f)
        colors.set(minOf(1f, sky + block), minOf(1f, sky + green), minOf(1f, sky + blue))
    }
}
