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
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/** Small feathered upper-edge halo, illustrative rather than an atmosphere scattering model. */
object PlanetLimbRenderer {
    private data class Vertex(
        val position: Vector3,
        val alpha: Int,
    )

    private val vertices: List<Vertex> =
        buildList {
            val h = SpaceScene.HALF_SIZE
            val corners = listOf(-h to -h, h to -h, h to h, -h to h)
            val rows = listOf(Triple(-90.0, 18.0, 0), Triple(0.0, 180.0, 22), Triple(110.0, 90.0, 12), Triple(200.0, 0.0, 0))
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
                    return Vertex(Vector3(x * expand, SpaceScene.TOP + elevation, z * expand), alpha)
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

    fun render(
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
    ) {
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
            // The sky target is composited later: a feather must not punch holes in its alpha.
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE)
            shader.MODEL_VIEW_MATRIX?.set(
                Matrix4f(view).scale(scale.toFloat()).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat()),
            )
            shader.PROJECTION_MATRIX?.set(projection)
            shader.COLOR_MODULATOR?.set(1f, 1f, 1f, 1f)
            shader.apply()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for ((v, alpha) in vertices) {
                buffer.addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat()).setColor(108, 181, 236, alpha)
            }
            BufferUploader.draw(buffer.buildOrThrow())
        } finally {
            shader.clear()
            RenderSystem.depthMask(write)
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha)
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        }
    }
}
