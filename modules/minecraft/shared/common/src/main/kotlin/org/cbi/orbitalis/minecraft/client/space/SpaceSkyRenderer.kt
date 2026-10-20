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

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.material.FogType
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import kotlin.random.Random

/** Distant presentation mesh; no remote chunks, block entities or optional mod APIs are loaded. */
object SpaceSkyRenderer {
    private var target: TextureTarget? = null
    private var celestialTarget: TextureTarget? = null

    fun clear() {
        PlanetLimbRenderer.resetWorld()
        target?.destroyBuffers()
        target = null
        celestialTarget?.destroyBuffers()
        celestialTarget = null
    }

    private data class Star(
        val vertices: List<Vector3>,
        val brightness: Int,
    )

    private fun billboard(
        direction: Vector3,
        radius: Double,
        size: Double,
    ): List<Vector3> {
        val reference = if (kotlin.math.abs(direction.y) < 0.9) Vector3(0.0, 1.0, 0.0) else Vector3(1.0, 0.0, 0.0)
        val u = direction.cross(reference).normalized() * size
        val v = direction.cross(u).normalized() * size
        val centre = direction * radius
        return listOf(centre - u - v, centre + u - v, centre + u + v, centre - u + v)
    }

    private val stars =
        buildList {
            val random = Random(512903)
            repeat(1800) {
                val direction = Vector3(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0))
                if (direction.length() in 0.1..1.0) {
                    add(Star(billboard(direction.normalized(), 90.0, random.nextDouble(0.025, 0.075)), random.nextInt(130, 256)))
                }
            }
        }

    @JvmOverloads
    fun render(
        level: ClientLevel,
        camera: Camera,
        matrix: Matrix4f,
        foggy: Boolean,
        opacity: Float = 1f,
        celestialsOnly: Boolean = false,
        projection: Matrix4f = RenderSystem.getProjectionMatrix(),
    ) {
        if (!celestialsOnly) PlanetLimbRenderer.resetWorld()
        if (opacity <= 0f || (level.dimension() == MoonWorld.KEY && !CelestialFlight.available(level))) return
        val sceneMatrix = CelestialView.rootView(level, matrix)
        if (foggy || camera.fluidInCamera != FogType.NONE) return
        val entity = camera.entity
        if (entity is LivingEntity && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))) return
        val position = camera.position
        val observer = CelestialView.globalPosition(level, Vector3(position.x, position.y, position.z))
        val scale =
            if (celestialsOnly) {
                60.0 / (
                    observer.length() +
                        CelestialView
                            .sample()
                            .frames.profile.earthOrbitRadius
                )
            } else {
                SpaceScene.scale(observer, 4096.0)
            }
        val minecraft = Minecraft.getInstance()
        val width = minecraft.mainRenderTarget.width
        val height = minecraft.mainRenderTarget.height
        val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }
        val clearColor = FloatArray(4).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, it) }
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val previousColor = RenderSystem.getShaderColor().copyOf()
        val previousShader = RenderSystem.getShader()
        val depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        // Ascent atmosphere/cloud passes retain target depth until their later world stages.
        val previousTarget = if (celestialsOnly) celestialTarget else target
        val surface =
            previousTarget ?: TextureTarget(width, height, true, Minecraft.ON_OSX).also {
                it.setClearColor(0f, 0f, 0f, if (celestialsOnly) 0f else 1f)
                if (celestialsOnly) celestialTarget = it else target = it
            }
        if (surface.width != width || surface.height != height) surface.resize(width, height, Minecraft.ON_OSX)
        RenderSystem.depthMask(true)
        surface.clear(Minecraft.ON_OSX)
        surface.bindWrite(true)
        RenderSystem.depthMask(false)
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        try {
            if (!celestialsOnly) {
                val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
                for (star in stars) {
                    for (vertex in star.vertices) add(buffer, sceneMatrix, vertex, star.brightness, star.brightness, star.brightness)
                }
                BufferUploader.drawWithShader(buffer.buildOrThrow())
            }
            RenderSystem.enableDepthTest()
            RenderSystem.depthMask(true)
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            val sample = if (CelestialFlight.available(level)) CelestialView.sample() else null
            // The vanilla Overworld mode alone retains native celestial bodies during ascent.
            if (sample != null &&
                (
                    level.dimension() != net.minecraft.world.level.Level.OVERWORLD ||
                        org.cbi.orbitalis.minecraft.lighting.SkyLighting
                            .physicalOverworld(level)
                )
            ) {
                SolarRenderer.render(
                    sample.frames.profile,
                    sample.frames.tick,
                    sample.frames.seconds,
                    observer,
                    sceneMatrix,
                    projection,
                    scale,
                )
            }
            if (!celestialsOnly) {
                SurfacePlanetRenderer.renderScene(
                    observer,
                    sceneMatrix,
                    projection,
                    scale,
                    clouds = false,
                    limb = false,
                )
            }
            val lunarVisible =
                level.dimension() != MoonWorld.KEY || LunarPlanetMesh.visibleFromSurface(Vector3(position.x, position.y, position.z))
            if (sample != null && lunarVisible) {
                LunarPlanetRenderer.render(
                    sample.frames.profile,
                    sample.frames.tick,
                    CelestialFlight.lunarSeed,
                    observer,
                    sceneMatrix,
                    projection,
                    scale,
                    sample.frames.seconds,
                )
            }
            if (!celestialsOnly) {
                PlanetLimbRenderer.prepareWorld(
                    level,
                    observer,
                    sceneMatrix,
                    projection,
                    scale,
                    surface.depthTextureId,
                    opacity,
                    width,
                    height,
                )
                PlanetCloudRenderer.prepareWorld(
                    level,
                    observer,
                    sceneMatrix,
                    projection,
                    scale,
                    surface.depthTextureId,
                    opacity,
                    width,
                    height,
                )
            }
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
            ScreenTexture.draw(surface.colorTextureId, opacity)
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
            RenderSystem.depthFunc(depthFunction)
            RenderSystem.setShader { previousShader }
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3])
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if (depthTest) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            RenderSystem.depthMask(depthWrite)
        }
    }

    private fun add(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        vertex: Vector3,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        buffer.addVertex(matrix, vertex.x.toFloat(), vertex.y.toFloat(), vertex.z.toFloat()).setColor(red, green, blue, 255)
    }
}
