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
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/** Luminous feathered upper-edge halo, illustrative rather than an atmosphere scattering model. */
object PlanetLimbRenderer {
    private data class Vertex(
        val position: Vector3,
        val alpha: Int,
        val color: Int,
    )

    private val vertices: List<Vertex> =
        buildList {
            val h = SpaceScene.HALF_SIZE
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
                    return Vertex(Vector3(x * expand, SpaceScene.TOP + elevation, z * expand), alpha, (red shl 16) or (green shl 8) or 255)
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

    private var mesh: VertexBuffer? = null

    fun clear() {
        RenderSystem.assertOnRenderThread()
        mesh?.close()
        mesh = null
    }

    private fun buffer(): VertexBuffer {
        mesh?.let { return it }
        val gpu = VertexBuffer(VertexBuffer.Usage.STATIC)
        try {
            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for ((v, alpha, color) in vertices) {
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
        RenderSystem.assertOnRenderThread()
        val shader = GameRenderer.getPositionColorShader() ?: return
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
            val gpu = buffer()
            gpu.bind()
            try {
                gpu.drawWithShader(
                    Matrix4f(view).scale(scale.toFloat()).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat()),
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
