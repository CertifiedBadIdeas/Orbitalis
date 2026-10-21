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
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.client.renderer.LightTexture
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.minecraft.client.space.FlightSky
import org.cbi.orbitalis.minecraft.lighting.SkyLighting
import org.joml.Matrix4f
import org.joml.Vector3f

/** Preserve native surface effects; blend only the exposed-light calculation towards vacuum. */
class SurfaceFlightEffects private constructor(
    private val native: DimensionSpecialEffects,
) : DimensionSpecialEffects(
        native.cloudHeight,
        native.hasGround(),
        native.skyType(),
        native.forceBrightLightmap(),
        native.constantAmbientLight(),
    ) {
    override fun getBrightnessDependentFogColor(
        color: Vec3,
        brightness: Float,
    ): Vec3 = native.getBrightnessDependentFogColor(color, brightness)

    override fun isFoggyAt(
        x: Int,
        z: Int,
    ): Boolean = native.isFoggyAt(x, z)

    override fun getSunriseColor(
        time: Float,
        partialTick: Float,
    ): FloatArray? = native.getSunriseColor(time, partialTick)

    override fun renderSky(
        level: ClientLevel,
        ticks: Int,
        partialTick: Float,
        modelViewMatrix: Matrix4f,
        camera: Camera,
        projectionMatrix: Matrix4f,
        isFoggy: Boolean,
        setupFog: Runnable,
    ): Boolean = native.renderSky(level, ticks, partialTick, modelViewMatrix, camera, projectionMatrix, isFoggy, setupFog)

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
    ): Boolean = native.renderClouds(level, ticks, partialTick, poseStack, camX, camY, camZ, modelViewMatrix, projectionMatrix)

    override fun renderSnowAndRain(
        level: ClientLevel,
        ticks: Int,
        partialTick: Float,
        lightTexture: LightTexture,
        camX: Double,
        camY: Double,
        camZ: Double,
    ): Boolean = native.renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ)

    override fun tickRain(
        level: ClientLevel,
        ticks: Int,
        camera: Camera,
    ): Boolean = native.tickRain(level, ticks, camera)

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
        native.adjustLightmapColors(level, partialTicks, skyDarken, blockLightRedFlicker, skyLight, pixelX, pixelY, colors)
        // Cosmic Overworld time already derives native skylight from the physical Sun. Replacing
        // it with constant vacuum daylight would illuminate native/Voxy terrain through the night
        // while the overview clouds still follow the Sun below the surface.
        if (SkyLighting.physicalOverworld(level)) return
        val weight = FlightSky.weight(level, Minecraft.getInstance().gameRenderer.mainCamera)
        if (weight <= 0f) return
        val vacuum = Vector3f(colors)
        SpaceEffects.adjustLightmapColors(level, partialTicks, skyDarken, blockLightRedFlicker, skyLight, pixelX, pixelY, vacuum)
        colors.lerp(vacuum, weight)
    }

    companion object {
        private var original: DimensionSpecialEffects? = null
        private var adapter: SurfaceFlightEffects? = null

        @JvmStatic fun select(
            level: ClientLevel,
            effects: DimensionSpecialEffects,
        ): DimensionSpecialEffects {
            if (level.dimension() != Level.OVERWORLD || effects is SurfaceFlightEffects) return effects
            // Do not replace an unrelated mod's effects extension and lose its custom render hooks.
            if (effects.javaClass != DimensionSpecialEffects.OverworldEffects::class.java) return effects
            if (original !== effects) {
                original = effects
                adapter = SurfaceFlightEffects(effects)
            }
            return checkNotNull(adapter)
        }
    }
}
