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
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.Level
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/** Luminous feathered upper-edge halo, illustrative rather than an atmosphere scattering model. */
object PlanetLimbRenderer {
    internal data class Vertex(
        val position: Vector3,
        val alpha: Int,
        val color: Int,
    )

    private const val DEFAULT_TERRAIN_CEILING = 320.0
    private const val TERRAIN_CLEARANCE = 64.0

    private fun terrainCeiling(): Double {
        val level = Minecraft.getInstance().level ?: return DEFAULT_TERRAIN_CEILING
        if (level.dimension() == Level.OVERWORLD) return level.maxBuildHeight.toDouble()
        val earth =
            level
                .registryAccess()
                .registryOrThrow(
                    Registries.DIMENSION_TYPE,
                ).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD)
                .value()
        return (earth.minY() + earth.height()).toDouble()
    }

    internal fun vertices(
        halfSize: Double,
        terrainCeiling: Double = DEFAULT_TERRAIN_CEILING,
    ): List<Vertex> =
        buildList {
            val h = halfSize
            // The entire feather floats above the native build envelope, including atlas relief.
            val baseHeight = terrainCeiling + TERRAIN_CLEARANCE
            val corners = listOf(-h to -h, h to -h, h to h, -h to h)
            // A narrow pale core and a broad blue falloff, without a bloom framebuffer or post-process.
            val rows =
                listOf(
                    Triple(-220.0, 22.0, 0),
                    Triple(-100.0, 25.0, 28),
                    Triple(-25.0, 30.0, 150),
                    Triple(0.0, 32.0, 235),
                    Triple(20.0, 30.0, 165),
                    Triple(55.0, 26.0, 70),
                    Triple(110.0, 18.0, 30),
                    Triple(190.0, 10.0, 8),
                    Triple(280.0, 0.0, 0),
                )
            for (side in corners.indices) {
                val a = corners[side]
                val b = corners[(side + 1) % corners.size]

                fun point(
                    fraction: Double,
                    row: Int,
                ): Vertex {
                    val (outset, elevation, alpha) = rows[row]
                    val x = a.first + (b.first - a.first) * fraction
                    val z = a.second + (b.second - a.second) * fraction
                    val expand = 1.0 + outset / h
                    val core = (1.0 - kotlin.math.abs(outset) / 110.0).coerceIn(0.0, 1.0)
                    val red = (45 + 120 * core).toInt()
                    val green = (105 + 110 * core).toInt()
                    return Vertex(Vector3(x * expand, baseHeight + elevation, z * expand), alpha, (red shl 16) or (green shl 8) or 255)
                }
                for (segment in 0 until 32) {
                    val start = segment / 32.0
                    val end = (segment + 1) / 32.0
                    for (row in 0 until rows.lastIndex) {
                        add(point(start, row))
                        add(point(end, row))
                        add(point(end, row + 1))
                        add(point(start, row + 1))
                    }
                }
            }
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
        val terrainCeiling: Double,
    )

    internal fun atmosphereBounds(
        halfSize: Double,
        terrainCeiling: Double = DEFAULT_TERRAIN_CEILING,
    ): Pair<Vector3, Vector3> =
        Vector3(-halfSize - 280.0, terrainCeiling, -halfSize - 280.0) to
            Vector3(halfSize + 280.0, terrainCeiling + TERRAIN_CLEARANCE + 280.0, halfSize + 280.0)

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
                terrainCeiling(),
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
            val localObserver =
                Matrix4f(frame.rotation).invert().transformPosition(
                    Vector3f(frame.observer.x.toFloat(), frame.observer.y.toFloat(), frame.observer.z.toFloat()),
                )
            val (minimum, maximum) = atmosphereBounds(SpaceScene.HALF_SIZE, frame.terrainCeiling)
            shader.getUniform("Observer")?.set(localObserver.x, localObserver.y, localObserver.z)
            shader.getUniform("BoundsMin")?.set(minimum.x.toFloat(), minimum.y.toFloat(), minimum.z.toFloat())
            shader.getUniform("BoundsMax")?.set(maximum.x.toFloat(), maximum.y.toFloat(), maximum.z.toFloat())
            shader.getUniform("SceneScale")?.set(frame.scale.toFloat())
            shader.getUniform("Viewport")?.set(frame.width.toFloat(), frame.height.toFloat())
            shader.setSampler("PlanetDepth", frame.depth)
            render(frame.observer, frame.view, frame.projection, frame.scale, shader, frame.rotation, frame.terrainCeiling)
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
    private var meshTerrainCeiling = Double.NaN

    fun clear() {
        RenderSystem.assertOnRenderThread()
        resetWorld()
        mesh?.close()
        mesh = null
        meshHalfSize = Double.NaN
        meshTerrainCeiling = Double.NaN
    }

    private fun buffer(terrainCeiling: Double): VertexBuffer {
        val halfSize = SpaceScene.HALF_SIZE
        mesh?.let { if (meshHalfSize == halfSize && meshTerrainCeiling == terrainCeiling) return it }
        clear()
        val gpu = VertexBuffer(VertexBuffer.Usage.STATIC)
        try {
            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for ((v, alpha, color) in vertices(halfSize, terrainCeiling)) {
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
            meshTerrainCeiling = terrainCeiling
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
        val shader = GameRenderer.getPositionColorShader() ?: return
        render(observer, view, projection, scale, shader, CelestialView.earthRotation(), terrainCeiling())
    }

    private fun render(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
        shader: ShaderInstance,
        rotation: Matrix4f,
        terrainCeiling: Double,
    ) {
        RenderSystem.assertOnRenderThread()
        val write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            RenderSystem.depthMask(false)
            RenderSystem.enableBlend()
            // Keep sky-target alpha intact while adding the luminous RGB feather.
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)
            val gpu = buffer(terrainCeiling)
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
