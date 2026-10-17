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
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.surface.SurfaceAtlasPayload
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import java.util.UUID

/** Private depth attachment gives relief self-occlusion without placing sky depth in front of ships. */
object SurfacePlanetRenderer {
    private var atlas = SurfaceAtlas()
    private var session: UUID? = null
    private var receivedRevision = -1L
    private var meshRevision = -1L
    private var lastBuild = 0L
    private var mesh: VertexBuffer? = null
    private var target: TextureTarget? = null

    fun accept(payload: SurfaceAtlasPayload) {
        RenderSystem.assertOnRenderThread()
        if (payload.reset) {
            atlas = SurfaceAtlas()
            session = payload.session
            receivedRevision = -1L
            meshRevision = -1L
            lastBuild = 0L
        }
        if (payload.session != session || payload.revision < receivedRevision) return
        for (sample in payload.samples) atlas.put(sample)
        receivedRevision = payload.revision
    }

    fun clear() {
        RenderSystem.assertOnRenderThread()
        atlas = SurfaceAtlas()
        session = null
        receivedRevision = -1L
        meshRevision = -1L
        mesh?.close()
        mesh = null
        target?.destroyBuffers()
        target = null
    }

    fun render(
        observer: Vector3,
        matrix: Matrix4f,
        scale: Double,
    ) {
        val minecraft = Minecraft.getInstance()
        val width = minecraft.mainRenderTarget.width
        val height = minecraft.mainRenderTarget.height
        val now = System.nanoTime()
        if (mesh == null || (meshRevision != atlas.revision && now - lastBuild > 500_000_000L)) {
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            for (face in SurfacePlanetMesh.faces(atlas)) {
                val light = 0.18 + 0.82 * maxOf(0.0, face.normal.dot(SpaceScene.SUN_DIRECTION))
                val r = ((face.color shr 16 and 255) * light).toInt()
                val g = ((face.color shr 8 and 255) * light).toInt()
                val b = ((face.color and 255) * light).toInt()
                for (v in face.vertices) buffer.addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat()).setColor(r, g, b, 255)
            }
            val gpu = mesh ?: VertexBuffer(VertexBuffer.Usage.STATIC).also { mesh = it }
            gpu.bind()
            gpu.upload(buffer.buildOrThrow())
            VertexBuffer.unbind()
            meshRevision = atlas.revision
            lastBuild = now
        }
        if (target == null) target = TextureTarget(width, height, true, Minecraft.ON_OSX).also { it.setClearColor(0f, 0f, 0f, 0f) }
        val surface = checkNotNull(target)
        if (surface.width != width || surface.height != height) surface.resize(width, height, Minecraft.ON_OSX)
        val src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        try {
            RenderSystem.depthMask(true)
            surface.clear(Minecraft.ON_OSX)
            surface.bindWrite(true)
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.disableBlend()
            val view =
                Matrix4f(
                    matrix,
                ).scale(scale.toFloat()).translate(-observer.x.toFloat(), -observer.y.toFloat(), -observer.z.toFloat())
            checkNotNull(mesh).bind()
            checkNotNull(mesh).drawWithShader(view, RenderSystem.getProjectionMatrix(), checkNotNull(GameRenderer.getPositionColorShader()))
            VertexBuffer.unbind()
            minecraft.mainRenderTarget.bindWrite(true)
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            surface.blitToScreen(width, height, false)
        } finally {
            VertexBuffer.unbind()
            minecraft.mainRenderTarget.bindWrite(true)
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha)
            RenderSystem.depthFunc(depthFunction)
            RenderSystem.disableBlend()
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
        }
    }
}
