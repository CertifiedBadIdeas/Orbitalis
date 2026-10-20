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
import net.minecraft.client.renderer.ShaderInstance
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/** Clip-space textured composite without changing the caller's view/projection or framebuffer. */
object ScreenTexture {
    var compositeShader: ShaderInstance? = null

    fun draw(
        texture: Int,
        opacity: Float,
        premultiplied: Boolean = false,
    ) {
        val shader = if (premultiplied) checkNotNull(compositeShader) else checkNotNull(GameRenderer.getPositionTexColorShader())
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            RenderSystem.disableCull()
            RenderSystem.enableBlend()
            if (premultiplied) {
                RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
            } else {
                RenderSystem.defaultBlendFunc()
            }
            shader.MODEL_VIEW_MATRIX?.set(Matrix4f())
            shader.PROJECTION_MATRIX?.set(Matrix4f())
            val alpha = opacity.coerceIn(0f, 1f)
            val rgb = if (premultiplied) alpha else 1f
            shader.COLOR_MODULATOR?.set(rgb, rgb, rgb, alpha)
            shader.setSampler("Sampler0", texture)
            shader.apply()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            buffer.addVertex(-1f, -1f, 0f).setUv(0f, 0f).setColor(-1)
            buffer.addVertex(1f, -1f, 0f).setUv(1f, 0f).setColor(-1)
            buffer.addVertex(1f, 1f, 0f).setUv(1f, 1f).setColor(-1)
            buffer.addVertex(-1f, 1f, 0f).setUv(0f, 1f).setColor(-1)
            BufferUploader.draw(buffer.buildOrThrow())
        } finally {
            shader.clear()
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha)
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            RenderSystem.depthMask(write)
        }
    }
}
