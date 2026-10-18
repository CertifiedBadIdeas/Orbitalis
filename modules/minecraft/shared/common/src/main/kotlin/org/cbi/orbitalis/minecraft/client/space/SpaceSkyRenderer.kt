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
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import kotlin.random.Random

/** Distant presentation mesh; no remote chunks, block entities or optional mod APIs are loaded. */
object SpaceSkyRenderer {
    private var target: TextureTarget? = null

    fun clear() {
        target?.destroyBuffers()
        target = null
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
    private val sun = billboard(SpaceScene.SUN_DIRECTION, 85.0, 1.5)

    fun render(
        level: ClientLevel,
        camera: Camera,
        matrix: Matrix4f,
        foggy: Boolean,
        opacity: Float = 1f,
    ) {
        if (opacity <= 0f || (level.dimension() == MoonWorld.KEY && !CelestialFlight.available(level))) return
        val sceneMatrix = CelestialView.rootView(level, matrix)
        if (foggy || camera.fluidInCamera != FogType.NONE) return
        val entity = camera.entity
        if (entity is LivingEntity && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))) return
        val position = camera.position
        val observer = FlightSpaceCoordinates.globalPosition(level, Vector3(position.x, position.y, position.z))
        val scale = SpaceScene.scale(observer, 4096.0)
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
        val surface =
            target ?: TextureTarget(width, height, true, Minecraft.ON_OSX).also {
                it.setClearColor(0f, 0f, 0f, 1f)
                target = it
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
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for (star in stars) {
                for (vertex in star.vertices) add(buffer, sceneMatrix, vertex, star.brightness, star.brightness, star.brightness)
            }
            for (vertex in sun) add(buffer, sceneMatrix, vertex, 255, 247, 220)
            BufferUploader.drawWithShader(buffer.buildOrThrow())
            RenderSystem.enableDepthTest()
            RenderSystem.depthMask(true)
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            SurfacePlanetRenderer.renderScene(observer, sceneMatrix, RenderSystem.getProjectionMatrix(), scale, clouds = false)
            val sample = if (CelestialFlight.available(level)) CelestialFlight.sample(level) else null
            val lunarVisible =
                level.dimension() != MoonWorld.KEY || LunarPlanetMesh.visibleFromSurface(Vector3(position.x, position.y, position.z))
            if (sample != null && lunarVisible) {
                LunarPlanetRenderer.render(
                    sample.frames.profile,
                    sample.frames.tick,
                    CelestialFlight.lunarSeed,
                    observer,
                    sceneMatrix,
                    RenderSystem.getProjectionMatrix(),
                    scale,
                )
            }
            PlanetCloudRenderer.prepareWorld(
                level,
                observer,
                sceneMatrix,
                RenderSystem.getProjectionMatrix(),
                scale,
                surface.depthTextureId,
                opacity,
                width,
                height,
            )
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
