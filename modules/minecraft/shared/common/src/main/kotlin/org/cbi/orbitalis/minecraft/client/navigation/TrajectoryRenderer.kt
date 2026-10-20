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

package org.cbi.orbitalis.minecraft.client.navigation

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.navigation.TrajectorySnapshot
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11

/** Navigation overlay in physical space, independent of HUD and native ship rendering. */
object TrajectoryRenderer {
    fun render(
        level: ClientLevel,
        camera: Camera,
        matrix: Matrix4f,
        snapshot: TrajectorySnapshot,
        marker: Vector3?,
    ) {
        val points = snapshot.prediction.points
        val end = snapshot.prediction.end
        val warning = end == CoastPrediction.End.SURFACE_ENTRY || end == CoastPrediction.End.BODY_INTERSECTION
        if (points.size < 2 && marker == null && !warning) return
        if (!org.cbi.orbitalis.minecraft.client.space.CelestialView
                .hasFrame(level)
        ) {
            return
        }
        val c = camera.position
        val observer =
            org.cbi.orbitalis.minecraft.client.space.CelestialView
                .globalPosition(level, Vector3(c.x, c.y, c.z))
        val rootView =
            org.cbi.orbitalis.minecraft.client.space.CelestialView
                .rootView(level, matrix)
        val previousColor = RenderSystem.getShaderColor().copyOf()
        val previousShader = RenderSystem.getShader()
        val depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        try {
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)

            fun vertex(
                p: Vector3,
                red: Int,
                green: Int,
                blue: Int,
            ) {
                val relative = p - observer
                // Preserve direction while keeping distant navigation lines inside the native far plane.
                val v = relative * minOf(1.0, 128.0 / maxOf(128.0, relative.length()))
                buffer.addVertex(rootView, v.x.toFloat(), v.y.toFloat(), v.z.toFloat()).setColor(red, green, blue, 255)
            }
            val elapsed = maxOf(0.0, (level.gameTime - snapshot.sampledTick) / 20.0)
            for (index in 1 until points.size) {
                if (end != CoastPrediction.End.ORBIT && points[index].seconds < elapsed) continue
                vertex(points[index - 1].state.position, 70, 220, 255)
                vertex(points[index].state.position, 70, 220, 255)
            }
            if (end == CoastPrediction.End.ORBIT && points.size > 1) {
                vertex(points.last().state.position, 70, 220, 255)
                vertex(points.first().state.position, 70, 220, 255)
            }
            if (marker != null) {
                for (axis in listOf(Vector3(8.0, 0.0, 0.0), Vector3(0.0, 16.0, 0.0), Vector3(0.0, 0.0, 8.0))) {
                    vertex(marker - axis, 80, 255, 100)
                    vertex(marker + axis, 80, 255, 100)
                }
            }
            if (warning) {
                val p = snapshot.prediction.terminal.state.position
                val green = if (end == CoastPrediction.End.SURFACE_ENTRY) 220 else 55
                for (axis in listOf(Vector3(10.0, 0.0, 0.0), Vector3(0.0, 10.0, 0.0), Vector3(0.0, 0.0, 10.0))) {
                    vertex(p - axis, 255, green, 40)
                    vertex(p + axis, 255, green, 40)
                }
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            RenderSystem.setShader { previousShader }
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3])
            if (depthTest) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            RenderSystem.depthMask(depthWrite)
        }
    }
}
