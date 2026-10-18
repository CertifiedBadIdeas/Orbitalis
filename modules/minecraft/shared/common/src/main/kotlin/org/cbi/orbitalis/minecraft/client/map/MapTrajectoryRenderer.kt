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
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.Vector3
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import java.util.UUID

/** Scissored GUI-space lines share the exact marker camera; distant endpoints stay GPU-clipped. */
object MapTrajectoryRenderer {
    fun render(
        graphics: GuiGraphics,
        camera: MapCamera,
        paths: List<MapTrajectories.Path>,
        selected: UUID?,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (paths.none { it.prediction.points.size > 1 }) return
        graphics.flush()
        val shader = RenderSystem.getShader()
        val color = RenderSystem.getShaderColor().copyOf()
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            RenderSystem.disableDepthTest()
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)
            val matrix = graphics.pose().last().pose()
            for (path in paths.sortedBy { it.source.id == selected }) {
                val chosen = path.source.id == selected

                fun vertex(position: Vector3) {
                    val p = camera.projectRaw(position, width, height)
                    buffer
                        .addVertex(matrix, (x + p.x).toFloat(), (y + p.y).toFloat(), 0f)
                        .setColor(if (chosen) 255 else 75, if (chosen) 225 else 170, if (chosen) 155 else 215, if (chosen) 255 else 155)
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
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            RenderSystem.setShader { shader }
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3])
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        }
    }
}
