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
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.CloudStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.SimpleTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer

/** Finite cloud overview using the runtime Minecraft texture and the native renderer's phase. */
object PlanetCloudRenderer {
    private val texture = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png")
    private val filtered = ResourceLocation.fromNamespaceAndPath("orbitalis", "planet_clouds")
    private var registered = false
    var worldShader: ShaderInstance? = null
    var compositeShader: ShaderInstance? = null
    private var scene: Scene? = null
    private var fancyBuffer: VertexBuffer? = null
    private var fastBuffer: VertexBuffer? = null
    private var boundaryBuffer: VertexBuffer? = null
    private var boundaryHalfSize = Double.NaN
    private var cloudDepth: TextureTarget? = null

    private data class Scene(
        val level: ClientLevel,
        val observer: Vector3,
        val view: Matrix4f,
        val projection: Matrix4f,
        val nativeProjection: Matrix4f,
        val scale: Double,
        val depth: Int,
        val color: Vec3,
        val width: Int,
        val height: Int,
    )

    private class CloudTexture : SimpleTexture(texture) {
        override fun load(resources: ResourceManager) {
            super.load(resources)
            val filter =
                com.mojang.blaze3d.pipeline.RenderCall {
                    bind()
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
                    setFilter(false, false)
                }
            if (RenderSystem.isOnRenderThreadOrInit()) filter.execute() else RenderSystem.recordRenderCall(filter)
        }
    }

    private fun textureId(): Int {
        val manager = Minecraft.getInstance().textureManager
        if (!registered) {
            manager.register(filtered, CloudTexture())
            registered = true
        }
        return manager.getTexture(filtered).id
    }

    /** Keep nearest-cloud depth in compressed scene coordinates, before physical depth loses detail. */
    private fun cloudDepth(
        width: Int,
        height: Int,
    ): TextureTarget {
        cloudDepth?.let { if (it.width == width && it.height == height) return it }
        cloudDepth?.destroyBuffers()
        return TextureTarget(width, height, true, Minecraft.ON_OSX).also {
            // An exact float sample matches gl_FragCoord.z in the colour pass; normalized depth24
            // would round it and reject some of the same surface's fragments.
            RenderSystem.bindTexture(it.depthTextureId)
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_DEPTH_COMPONENT32F,
                width,
                height,
                0,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_FLOAT,
                null as ByteBuffer?,
            )
            cloudDepth = it
        }
    }

    private var time = 0f

    internal fun atmosphereCloudHeight(): Double = height.toDouble()

    private var height = 192f
    private var nativeColor = Vec3(1.0, 1.0, 1.0)

    /** Called before either world's sky hook; LevelRenderer's clock survives native travel. */
    fun synchronize(
        nativeTime: Float,
        surfaceHeight: Float?,
        surfaceColor: Vec3,
    ) {
        scene = null
        time = nativeTime
        if (surfaceHeight != null) height = surfaceHeight
        nativeColor = surfaceColor
    }

    fun clear() {
        time = 0f
        height = 192f
        nativeColor = Vec3(1.0, 1.0, 1.0)
        scene = null
        fancyBuffer?.close()
        fastBuffer?.close()
        boundaryBuffer?.close()
        fancyBuffer = null
        fastBuffer = null
        boundaryBuffer = null
        boundaryHalfSize = Double.NaN
        cloudDepth?.destroyBuffers()
        cloudDepth = null
        if (registered) Minecraft.getInstance().textureManager.release(filtered)
        registered = false
    }

    fun prepareWorld(
        level: ClientLevel,
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        depth: Int,
        opacity: Float,
        width: Int,
        height: Int,
        nativeProjection: Matrix4f = projection,
    ) {
        val color =
            if (level.dimension() ==
                Level.OVERWORLD
            ) {
                nativeColor.lerp(Vec3(1.0, 1.0, 1.0), opacity.toDouble())
            } else {
                Vec3(1.0, 1.0, 1.0)
            }
        scene = Scene(level, observer, Matrix4f(view), Matrix4f(projection), Matrix4f(nativeProjection), scale, depth, color, width, height)
    }

    fun renderWorld() {
        val frame = scene ?: return
        scene = null
        if (Minecraft.getInstance().level !== frame.level || worldShader == null || compositeShader == null) return
        render(frame.observer, frame.view, frame.projection, frame.scale, frame)
    }

    fun render(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
    ) = render(observer, view, projection, scale, null)

    private fun render(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        frame: Scene?,
    ) {
        val mc = Minecraft.getInstance()
        val clouds = mc.options.cloudsType
        if (clouds == CloudStatus.OFF || !height.isFinite() || worldShader == null) return
        val fancy = clouds == CloudStatus.FANCY
        val y = height + 0.33f
        val modelView =
            Matrix4f(
                view,
            ).scale(
                scale.toFloat(),
            ).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat())
                .mul(CelestialView.earthRotation())
        val shader = checkNotNull(worldShader)
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val colorMask = BufferUtils.createByteBuffer(4).also { GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, it) }
        val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }
        val clearColor = FloatArray(4).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, it) }
        val src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            RenderSystem.enableDepthTest()
            RenderSystem.depthMask(true)
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            if (fancy) RenderSystem.enableCull() else RenderSystem.disableCull()
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            shader.MODEL_VIEW_MATRIX?.set(modelView)
            shader.PROJECTION_MATRIX?.set(projection)
            shader.getUniform("NativeProjection")?.set(frame?.nativeProjection ?: projection)
            val sunlight = PlanetLighting.earthDirection()
            val brightness = (0.065 + 0.935 * maxOf(0.0, sunlight.y)).toFloat()
            val color = Vec3(brightness.toDouble(), brightness.toDouble(), brightness.toDouble())
            shader.COLOR_MODULATOR?.set(color.x.toFloat(), color.y.toFloat(), color.z.toFloat(), 1f)
            shader.setSampler("Sampler0", textureId())
            shader.setSampler("Sampler1", frame?.depth ?: textureId())
            shader.getUniform("SceneScale")?.set(scale.toFloat())
            shader.getUniform("Viewport")?.set(frame?.width?.toFloat() ?: 1f, frame?.height?.toFloat() ?: 1f)
            shader.getUniform("WorldDepth")?.set(if (frame == null) 0 else 3)
            shader.getUniform("CloudHalfSize")?.set(SpaceScene.HALF_SIZE.toFloat())
            val phase = PlanetCloudMesh.phase(time)
            shader.getUniform("CloudPhase")?.set(phase.x, phase.z)
            val tiles = PlanetCloudMesh.tiles(SpaceScene.HALF_SIZE, time)
            val vertices = buffer(fancy)
            val boundary = if (fancy) boundaryBuffer(SpaceScene.HALF_SIZE) else null

            fun drawTiles() {
                vertices.bind()
                shader.getUniform("CloudBoundary")?.set(0)
                for (tile in tiles) {
                    shader.getUniform("CloudOffset")?.set(tile.x, y, tile.z)
                    shader.apply()
                    vertices.draw()
                }
                if (boundary != null) {
                    boundary.bind()
                    shader.getUniform("CloudBoundary")?.set(1)
                    shader.getUniform("CloudOffset")?.set(0f, y, 0f)
                    shader.apply()
                    boundary.draw()
                }
            }
            if (fancy && frame != null) {
                val nearest = cloudDepth(frame.width, frame.height)
                nearest.bindWrite(true)
                RenderSystem.colorMask(true, true, true, true)
                RenderSystem.clearColor(0f, 0f, 0f, 0f)
                RenderSystem.clearDepth(1.0)
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
                RenderSystem.disableBlend()
                RenderSystem.depthFunc(GL11.GL_LESS)
                shader.getUniform("WorldDepth")?.set(2)
                // Opaque replacement into a transparent target selects one nearest straight-alpha
                // cloud surface, without accumulating unresolved interior volume sheets.
                drawTiles()
                shader.clear()
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
                RenderSystem.colorMask(
                    colorMask[0].toInt() != 0,
                    colorMask[1].toInt() != 0,
                    colorMask[2].toInt() != 0,
                    colorMask[3].toInt() != 0,
                )
                RenderSystem.depthFunc(GL11.GL_LEQUAL)
                RenderSystem.enableBlend()
                composite(nearest, projection, frame.nativeProjection, scale)
            } else {
                if (fancy) {
                    RenderSystem.colorMask(false, false, false, false)
                    drawTiles()
                    RenderSystem.colorMask(
                        colorMask[0].toInt() != 0,
                        colorMask[1].toInt() != 0,
                        colorMask[2].toInt() != 0,
                        colorMask[3].toInt() != 0,
                    )
                    RenderSystem.depthMask(false)
                }
                drawTiles()
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
            VertexBuffer.unbind()
            shader.clear()
            RenderSystem.colorMask(
                colorMask[0].toInt() != 0,
                colorMask[1].toInt() != 0,
                colorMask[2].toInt() != 0,
                colorMask[3].toInt() != 0,
            )
            RenderSystem.depthFunc(depthFunction)
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha)
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            RenderSystem.depthMask(write)
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        }
    }

    /** Compose one resolved cloud layer against native depth without rerasterizing its volume. */
    private fun composite(
        surface: TextureTarget,
        projection: Matrix4f,
        nativeProjection: Matrix4f,
        scale: Double,
    ) {
        val shader = checkNotNull(compositeShader)
        VertexBuffer.unbind()
        shader.setSampler("CloudColor", surface.colorTextureId)
        shader.setSampler("CloudDepth", surface.depthTextureId)
        shader.getUniform("InverseProjection")?.set(Matrix4f(projection).invert())
        shader.PROJECTION_MATRIX?.set(projection)
        shader.getUniform("NativeProjection")?.set(nativeProjection)
        shader.getUniform("SceneScale")?.set(scale.toFloat())
        RenderSystem.disableCull()
        shader.apply()
        try {
            val quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            quad.addVertex(-1f, -1f, 0f).setUv(0f, 0f)
            quad.addVertex(1f, -1f, 0f).setUv(1f, 0f)
            quad.addVertex(1f, 1f, 0f).setUv(1f, 1f)
            quad.addVertex(-1f, 1f, 0f).setUv(0f, 1f)
            BufferUploader.draw(quad.buildOrThrow())
        } finally {
            shader.clear()
        }
    }

    private fun boundaryBuffer(halfSize: Double): VertexBuffer {
        boundaryBuffer?.let { if (boundaryHalfSize == halfSize) return it }
        boundaryBuffer?.close()
        return upload(PlanetCloudMesh.boundary(halfSize)).also {
            boundaryBuffer = it
            boundaryHalfSize = halfSize
        }
    }

    private fun buffer(fancy: Boolean): VertexBuffer {
        (if (fancy) fancyBuffer else fastBuffer)?.let { return it }
        return upload(PlanetCloudMesh.vertices(fancy)).also {
            if (fancy) fancyBuffer = it else fastBuffer = it
        }
    }

    private fun upload(vertices: List<PlanetCloudMesh.Vertex>): VertexBuffer {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        for (v in vertices) {
            val shade = (v.shade * 255f).toInt()
            builder.addVertex(v.x, v.y, v.z).setUv(v.u, v.v).setColor(shade, shade, shade, 204)
        }
        return VertexBuffer(VertexBuffer.Usage.STATIC).also {
            it.bind()
            it.upload(builder.buildOrThrow())
            VertexBuffer.unbind()
        }
    }
}
