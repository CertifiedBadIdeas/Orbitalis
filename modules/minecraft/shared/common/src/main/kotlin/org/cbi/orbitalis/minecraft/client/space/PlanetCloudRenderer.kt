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
import net.minecraft.client.Camera
import net.minecraft.client.CloudStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.FogType
import org.cbi.orbitalis.core.cloud.ProceduralCloudField
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.clouds.EarthCloudState
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.lighting.SkyLighting
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Shared procedural cloud volumes; worker-built sector meshes carry no baked lighting. */
object PlanetCloudRenderer {
    var worldShader: ShaderInstance? = null
    var compositeShader: ShaderInstance? = null
    private var scene: Scene? = null
    private var cloudDepth: TextureTarget? = null

    private data class Scene(
        val level: ClientLevel,
        val observer: Vector3,
        val view: Matrix4f,
        val projection: Matrix4f,
        val nativeProjection: Matrix4f,
        val scale: Double,
        val depth: Int?,
        val rotation: Matrix4f,
        val width: Int,
        val height: Int,
    )

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

    internal fun atmosphereCloudHeight(): Double =
        (EarthCloudState.client?.baseHeight ?: EarthCloudState.BASE_HEIGHT) + ProceduralCloudField.MAX_HEIGHT

    @JvmStatic
    fun surfaceFar(
        original: Float,
        eyeHeight: Double,
    ): Float = CloudSectorSelection.surfaceFar(original, eyeHeight, EarthCloudState.client?.baseHeight ?: EarthCloudState.BASE_HEIGHT)

    /** Called before either sky hook so no prepared scene survives a world/camera change. */
    fun beginFrame() {
        scene = null
    }

    fun clear() {
        scene = null
        CloudSectorCache.clear()
        cloudDepth?.destroyBuffers()
        cloudDepth = null
    }

    fun prepareWorld(
        level: ClientLevel,
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        depth: Int,
        width: Int,
        height: Int,
        nativeProjection: Matrix4f = projection,
    ) {
        scene =
            Scene(
                level,
                observer,
                Matrix4f(view),
                Matrix4f(projection),
                Matrix4f(nativeProjection),
                scale,
                depth,
                CelestialView.earthRotation(),
                width,
                height,
            )
    }

    /** The same cloud material below the ascent blend, expressed directly in native surface metres. */
    @JvmStatic
    fun prepareSurface(
        level: ClientLevel,
        camera: Camera,
        view: Matrix4f,
        projection: Matrix4f,
        foggy: Boolean,
    ) {
        if (foggy || camera.fluidInCamera != FogType.NONE ||
            (level.dimension() != Level.OVERWORLD || !CelestialFlight.available(level))
        ) {
            return
        }
        val entity = camera.entity
        if (entity is LivingEntity &&
            (
                entity.hasEffect(MobEffects.BLINDNESS) ||
                    entity.hasEffect(MobEffects.DARKNESS)
            )
        ) {
            return
        }
        val p = camera.position
        val target = Minecraft.getInstance().mainRenderTarget
        scene =
            Scene(
                level,
                Vector3(p.x, p.y, p.z),
                Matrix4f(view),
                Matrix4f(projection),
                Matrix4f(projection),
                1.0,
                null,
                Matrix4f(),
                target.width,
                target.height,
            )
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
        if (clouds == CloudStatus.OFF || worldShader == null) return
        val state = EarthCloudState.sampleClient() ?: return
        val fancy = true // Both settings retain volume; FAST selects coarser geometry.
        val y = state.baseHeight.toFloat()
        val modelView =
            Matrix4f(
                view,
            ).scale(
                scale.toFloat(),
            ).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat())
                .mul(frame?.rotation ?: CelestialView.earthRotation())
        val inverse = Matrix4f(modelView).invert()
        val eye = inverse.transformPosition(Vector3f())
        val localEye = Vector3(eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble())
        val parallel = abs(projection.m33()) > 0.5f
        val distance = abs(localEye.y - state.baseHeight - ProceduralCloudField.MAX_HEIGHT / 2)
        val local = !parallel && distance < 2000.0
        val pixelsPerMetre =
            if (parallel) {
                abs(projection.m00() * modelView.getScale(Vector3f()).x) * mc.window.width / 2.0
            } else {
                null
            }
        val step = CloudSectorSelection.step(distance, pixelsPerMetre, clouds == CloudStatus.FAST)
        val clock = CelestialView.sample().frames
        val wind = EarthCloudState.wind(clock.tick, clock.seconds, ProceduralCloudField.PERIOD)
        val frustum = FrustumIntersection(Matrix4f(projection).mul(modelView))
        val sectors =
            CloudSectorSelection.sectors(SpaceScene.HALF_SIZE, localEye, wind.x, wind.z, local).filter {
                val x = (it.x * ProceduralCloudField.SECTOR_SIZE + wind.x).toFloat()
                val z = (it.z * ProceduralCloudField.SECTOR_SIZE + wind.z).toFloat()
                frustum.testAab(
                    x,
                    y,
                    z,
                    x + ProceduralCloudField.SECTOR_SIZE.toFloat(),
                    y + ProceduralCloudField.MAX_HEIGHT.toFloat(),
                    z + ProceduralCloudField.SECTOR_SIZE.toFloat(),
                )
            }
        CloudSectorCache.pumpUploads()
        val meshes =
            sectors.mapNotNull { sector ->
                CloudSectorCache.request(state.seed, sector.x, sector.z, step)?.let { sector to it }
            }
        if (meshes.isEmpty()) return
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
            val nativeSun =
                frame != null && frame.level.dimension() == Level.OVERWORLD &&
                    !SkyLighting.physicalOverworld(frame.level)
            val sunlight =
                if (nativeSun) {
                    val angle = frame!!.level.getTimeOfDay(mc.timer.getGameTimeDeltaPartialTick(false)) * Math.PI * 2
                    Vector3(-sin(angle), cos(angle), 0.0)
                } else {
                    PlanetLighting.earthDirection()
                }
            PlanetLighting.vector(shader, "SunDirection", sunlight)
            PlanetLighting.vector(shader, "SunRadiance", if (nativeSun) Vector3(1.0, 1.0, 1.0) else PlanetLighting.earthSunRadiance())
            PlanetLighting.prepareView(shader, modelView, projection)
            PlanetLighting.prepareAtmosphere(shader)
            // Weather dims the common material; day/night and chroma belong to its solar shader.
            val weather = (1f - 0.4f * state.rain) * (1f - 0.4f * state.thunder)
            shader.COLOR_MODULATOR?.set(weather, weather, weather, 1f)
            shader.getUniform("UsePlanetDepth")?.set(if (frame?.depth == null) 0 else 1)
            shader.setSampler("Sampler1", frame?.depth ?: 0)
            shader.getUniform("SceneScale")?.set(scale.toFloat())
            shader.getUniform("Viewport")?.set(frame?.width?.toFloat() ?: 1f, frame?.height?.toFloat() ?: 1f)
            shader.getUniform("WorldDepth")?.set(if (frame == null) 0 else 3)
            shader.getUniform("CloudHalfSize")?.set(SpaceScene.HALF_SIZE.toFloat())
            shader.getUniform("CloudFadeDistance")?.set(if (local) CloudSectorSelection.FADE_DISTANCE.toFloat() else 0f)

            fun drawTiles() {
                for ((sector, mesh) in meshes) {
                    mesh.bind()
                    shader.getUniform("CloudOffset")?.set(
                        (sector.x * ProceduralCloudField.SECTOR_SIZE + wind.x).toFloat(),
                        y,
                        (sector.z * ProceduralCloudField.SECTOR_SIZE + wind.z).toFloat(),
                    )
                    shader.apply()
                    mesh.draw()
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
}
