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

package org.cbi.orbitalis.minecraft.client.map

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import java.util.UUID

/** Screen-owned world-space lines share the planet's projection and real relief depth attachment. */
class MapTrajectoryRenderer : AutoCloseable {
    private var mesh: VertexBuffer? = null
    private var previous: List<MapTrajectories.Path>? = null
    private var selection: UUID? = null
    private var previousBodies: List<CelestialMapScene.Body>? = null

    private fun upload(
        paths: List<MapTrajectories.Path>,
        selected: UUID?,
        bodies: List<CelestialMapScene.Body>,
    ) {
        if (previous === paths && selection == selected && previousBodies === bodies) return
        previous = paths
        previousBodies = bodies
        selection = selected
        if (paths.none { it.prediction.points.size > 1 } && bodies.none { it.orbit.size > 1 }) {
            mesh?.close()
            mesh = null
            return
        }
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)
        for (body in bodies) {
            val points = body.orbit
            if (points.size < 2) continue

            fun vertex(p: Vector3) {
                buffer.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setColor(130, 155, 180, 160)
            }
            for (i in points.indices) {
                vertex(points[i])
                vertex(points[(i + 1) % points.size])
            }
        }
        for (path in paths.sortedWith(compareBy<MapTrajectories.Path> { it.planned }.thenBy { it.source.id == selected })) {
            val chosen = path.source.id == selected

            fun vertex(p: Vector3) {
                buffer.addVertex(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()).setColor(
                    if (path.planned) {
                        208
                    } else if (chosen) {
                        255
                    } else {
                        75
                    },
                    if (path.planned) {
                        140
                    } else if (chosen) {
                        225
                    } else {
                        170
                    },
                    if (path.planned) {
                        255
                    } else if (chosen) {
                        155
                    } else {
                        215
                    },
                    if (chosen) 255 else 155,
                )
            }
            val points = path.prediction.points
            for (i in 1 until points.size) {
                vertex(points[i - 1].state.position)
                vertex(points[i].state.position)
            }
            if (path.prediction.end == CoastPrediction.End.ORBIT && points.size > 1) {
                vertex(points.last().state.position)
                vertex(points.first().state.position)
            }
        }
        val gpu = mesh ?: VertexBuffer(VertexBuffer.Usage.STATIC).also { mesh = it }
        gpu.bind()
        try {
            buffer.buildOrThrow().use { gpu.upload(it) }
        } finally {
            VertexBuffer.unbind()
        }
    }

    /** Caller has drawn the planet and keeps its viewport/depth until this pass finishes. */
    fun render(
        camera: MapCamera,
        view: Matrix4f,
        projection: Matrix4f,
        paths: List<MapTrajectories.Path>,
        selected: UUID?,
        bodies: List<CelestialMapScene.Body> = emptyList(),
    ) {
        upload(paths, selected, bodies)
        val gpu = mesh ?: return
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val color = RenderSystem.getShaderColor().copyOf()
        val shader = RenderSystem.getShader()
        try {
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.depthMask(false)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            val modelView =
                Matrix4f(
                    view,
                ).scale(
                    (1.0 / camera.range).toFloat(),
                ).translate(-camera.focus.x.toFloat(), -camera.focus.y.toFloat(), -camera.focus.z.toFloat())
            gpu.bind()
            gpu.drawWithShader(modelView, projection, checkNotNull(GameRenderer.getPositionColorShader()))
        } finally {
            VertexBuffer.unbind()
            RenderSystem.setShader { shader }
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3])
            RenderSystem.depthMask(mask)
            RenderSystem.depthFunc(func)
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        }
    }

    override fun close() {
        mesh?.close()
        mesh = null
        previous = null
        previousBodies = null
    }
}
