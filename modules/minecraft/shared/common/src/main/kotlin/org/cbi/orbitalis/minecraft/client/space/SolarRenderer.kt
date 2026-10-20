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
import net.minecraft.client.renderer.ShaderInstance
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/** Analytic photosphere and corona: no terrain, Sable body or persistent GPU meshes. */
object SolarRenderer {
    var shader: ShaderInstance? = null

    fun render(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double,
        observer: Vector3,
        view: Matrix4f,
        projection: Matrix4f,
        scale: Double,
    ) {
        val program = shader ?: return
        val sphere = SolarScene.sample(profile, tick, seconds, observer)
        val radiusScale = (sphere.radius * scale).toFloat()
        // Rotation and camera-relative translation share the caller's scene units.
        val rotation = Matrix4f(view).setTranslation(0f, 0f, 0f)
        val centre = rotation.transformDirection(Vector3f(sphere.centre.x.toFloat(), sphere.centre.y.toFloat(), sphere.centre.z.toFloat()))
        centre.add(view.m30() / radiusScale, view.m31() / radiusScale, view.m32() / radiusScale)
        val previous = RenderSystem.getShader()
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.depthMask(true)
            RenderSystem.disableCull()
            RenderSystem.enableBlend()
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE)
            program.getUniform("InverseProjection")?.set(Matrix4f(projection).invert())
            program.getUniform("SolarProjection")?.set(projection)
            program.getUniform("InverseView")?.set(rotation.invert())
            program.getUniform("Centre")?.set(centre.x, centre.y, centre.z)
            program.getUniform("RadiusScale")?.set(radiusScale)
            program.getUniform("Phase")?.set(sphere.phase.toFloat())
            program.apply()
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION)
            buffer.addVertex(-1f, -1f, 0f)
            buffer.addVertex(1f, -1f, 0f)
            buffer.addVertex(1f, 1f, 0f)
            buffer.addVertex(-1f, 1f, 0f)
            BufferUploader.draw(buffer.buildOrThrow())
        } finally {
            program.clear()
            RenderSystem.setShader { previous }
            RenderSystem.depthFunc(func)
            RenderSystem.depthMask(write)
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha)
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        }
    }
}
