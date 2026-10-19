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

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.ShaderInstance
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/** Finite atmospheric volume shared by orbital and map views. */
object PlanetLimbRenderer {
    internal data class Vertex(
        val position: Vector3,
        val alpha: Int = 255,
        val color: Int = 0xffffff,
    )

    internal fun atmosphereBounds(
        halfSize: Double,
        cloudHeight: Double = 192.0,
    ): Pair<Vector3, Vector3> {
        val feather = atmosphereFeather(halfSize)
        return Vector3(-halfSize - feather, SpaceScene.TOP, -halfSize - feather) to
            Vector3(halfSize + feather, maxOf(SpaceScene.TOP + 32.0, cloudHeight + 24.0), halfSize + feather)
    }

    internal fun atmosphereFeather(halfSize: Double): Double = (halfSize * 0.12).coerceIn(24.0, 160.0)

    internal fun vertices(
        halfSize: Double,
        cloudHeight: Double = 192.0,
    ): List<Vertex> {
        val (a, b) = atmosphereBounds(halfSize, cloudHeight)
        val corners =
            listOf(
                Vector3(a.x, a.y, a.z),
                Vector3(b.x, a.y, a.z),
                Vector3(b.x, a.y, b.z),
                Vector3(a.x, a.y, b.z),
                Vector3(a.x, b.y, a.z),
                Vector3(b.x, b.y, a.z),
                Vector3(b.x, b.y, b.z),
                Vector3(a.x, b.y, b.z),
            )
        return listOf(0, 1, 2, 3, 4, 7, 6, 5, 0, 4, 5, 1, 1, 5, 6, 2, 2, 6, 7, 3, 3, 7, 4, 0)
            .map { Vertex(corners[it]) }
    }

    var worldShader: ShaderInstance? = null
    private var scene: Scene? = null

    private data class Scene(
        val level: ClientLevel,
        val observer: Vector3,
        val view: Matrix4f,
        val projection: Matrix4f,
        val rotation: Matrix4f,
        val scale: Double,
        val depth: Int,
        val opacity: Float,
        val width: Int,
        val height: Int,
        val cloudHeight: Double,
    )

    fun resetWorld() {
        scene = null
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
    ) {
        scene =
            Scene(
                level,
                observer,
                Matrix4f(view),
                Matrix4f(projection),
                CelestialView.earthRotation(),
                scale,
                depth,
                opacity,
                width,
                height,
                PlanetCloudRenderer.atmosphereCloudHeight(),
            )
    }

    /** The atmosphere in front of native terrain is composed after that terrain has written depth. */
    fun renderWorld() {
        val frame = scene ?: return
        scene = null
        val shader = worldShader ?: return
        if (Minecraft.getInstance().level !== frame.level) return
        val previousColor = RenderSystem.getShaderColor().copyOf()
        val previousShader = RenderSystem.getShader()
        val depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        try {
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.disableCull()
            RenderSystem.setShaderColor(1f, 1f, 1f, frame.opacity)
            shader.getUniform("Viewport")?.set(frame.width.toFloat(), frame.height.toFloat())
            shader.setSampler("PlanetDepth", frame.depth)
            shader.getUniform("UsePlanetDepth")?.set(1)
            render(frame.observer, frame.view, frame.projection, frame.scale, shader, frame.rotation, frame.cloudHeight)
        } finally {
            RenderSystem.setShader { previousShader }
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3])
            RenderSystem.depthFunc(depthFunction)
            if (depthTest) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
        }
    }

    private var mesh: VertexBuffer? = null
    private var meshHalfSize = Double.NaN
    private var meshCloudHeight = Double.NaN

    fun clear() {
        RenderSystem.assertOnRenderThread()
        resetWorld()
        mesh?.close()
        mesh = null
        meshHalfSize = Double.NaN
        meshCloudHeight = Double.NaN
    }

    private fun buffer(cloudHeight: Double): VertexBuffer {
        val halfSize = SpaceScene.HALF_SIZE
        mesh?.let { if (meshHalfSize == halfSize && meshCloudHeight == cloudHeight) return it }
        clear()
        val gpu = VertexBuffer(VertexBuffer.Usage.STATIC)
        try {
            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for ((v, alpha, color) in vertices(halfSize, cloudHeight)) {
                builder.addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat()).setColor(
                    color shr 16 and 255,
                    color shr 8 and 255,
                    color and 255,
                    alpha,
                )
            }
            builder.buildOrThrow().use { data ->
                gpu.bind()
                try {
                    gpu.upload(data)
                } finally {
                    VertexBuffer.unbind()
                }
            }
            mesh = gpu
            meshHalfSize = halfSize
            meshCloudHeight = cloudHeight
            return gpu
        } catch (error: Throwable) {
            gpu.close()
            throw error
        }
    }

    fun render(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
    ) {
        val shader = worldShader ?: return
        shader.getUniform("UsePlanetDepth")?.set(0)
        render(observer, view, projection, scale, shader, CelestialView.earthRotation(), PlanetCloudRenderer.atmosphereCloudHeight())
    }

    private fun render(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        shader: ShaderInstance,
        rotation: Matrix4f,
        cloudHeight: Double,
    ) {
        RenderSystem.assertOnRenderThread()
        val localObserver =
            Matrix4f(rotation).invert().transformPosition(
                Vector3f(observer.x.toFloat(), observer.y.toFloat(), observer.z.toFloat()),
            )
        val (minimum, maximum) = atmosphereBounds(SpaceScene.HALF_SIZE, cloudHeight)
        shader.getUniform("Observer")?.set(localObserver.x, localObserver.y, localObserver.z)
        shader.getUniform("BoundsMin")?.set(minimum.x.toFloat(), minimum.y.toFloat(), minimum.z.toFloat())
        shader.getUniform("BoundsMax")?.set(maximum.x.toFloat(), maximum.y.toFloat(), maximum.z.toFloat())
        shader.getUniform("Feather")?.set(atmosphereFeather(SpaceScene.HALF_SIZE).toFloat())
        shader.getUniform("SceneScale")?.set(scale.toFloat())
        shader.getUniform("InverseProjMat")?.set(Matrix4f(projection).invert())
        val write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            RenderSystem.depthMask(false)
            RenderSystem.enableBlend()
            // Preserve the sky target alpha while adding scattered light.
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)
            val gpu = buffer(cloudHeight)
            gpu.bind()
            try {
                gpu.drawWithShader(
                    Matrix4f(
                        view,
                    ).scale(
                        scale.toFloat(),
                    ).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat())
                        .mul(rotation),
                    projection,
                    shader,
                )
            } finally {
                VertexBuffer.unbind()
            }
        } finally {
            RenderSystem.depthMask(write)
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha)
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        }
    }
}
