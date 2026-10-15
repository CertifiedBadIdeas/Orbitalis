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

package org.cbi.orbitalis.minecraft.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer
import org.cbi.orbitalis.core.planet.CubeFace
import org.cbi.orbitalis.core.planet.OrbitalTestFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.sqrt

/** Unlit diagnostic sky geometry, not collision terrain or a map of generated surface blocks. */
object OrbitPlanetReference {
    private val centre = OrbitalTestFrame.toOrbit(FlightTestSpace.CENTRE)
    private val halfExtent = FlightTestSpace.gravity.halfExtent
    private val colours = intArrayOf(0x536b89, 0x384963, 0x477860, 0x29384c, 0x67617e, 0x44566d)

    /** Caller owns GL depth/blend/cull state restoration; all coordinates are interpolated camera-relative. */
    fun render(
        camera: Camera,
        view: Matrix4f,
    ) {
        val eye = Vector3(camera.position.x, camera.position.y, camera.position.z)
        val offset = centre - eye
        // Uniform sky scaling preserves angular size/parallax while keeping every vertex inside the
        // vanilla far plane. No chunk loading, extended projection or depth-buffer planet is needed.
        val scale = 64.0 / (offset.length() + sqrt(3.0) * halfExtent)
        val shader = RenderSystem.getShader()
        val colour = RenderSystem.getShaderColor().copyOf()
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix()
        stack.identity()
        RenderSystem.applyModelViewMatrix()
        try {
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            RenderSystem.disableBlend()
            RenderSystem.enableCull()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

            fun quad(
                origin: Vector3,
                u: Vector3,
                v: Vector3,
                rgb: Int,
            ) {
                for (point in arrayOf(origin, origin + u, origin + u + v, origin + v)) {
                    val relative = (offset + point) * scale
                    buffer
                        .addVertex(view, relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
                        .setColor((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255, 255)
                }
            }

            for (face in CubeFace.entries) {
                // Do not draw the hidden faces over the front ones: the sky pass has no depth writes.
                if ((eye - centre).dot(face.normal) <= halfExtent) continue
                val origin = face.normal * halfExtent - face.uAxis * halfExtent - face.vAxis * halfExtent
                quad(origin, face.uAxis * (halfExtent * 2), face.vAxis * (halfExtent * 2), colours[face.ordinal])
                val gridColour = 0x91a7a8
                val gridWidth = 8.0
                for (i in 0..12) {
                    val step = (halfExtent * 2 - gridWidth) * i / 12.0
                    quad(origin + face.uAxis * step, face.uAxis * gridWidth, face.vAxis * (halfExtent * 2), gridColour)
                    quad(origin + face.vAxis * step, face.uAxis * (halfExtent * 2), face.vAxis * gridWidth, gridColour)
                }
                if (face == CubeFace.POSITIVE_Y) {
                    // The existing transfer patch is centred above the top face; this is a location
                    // reference, not a promise that distant procedural terrain is represented here.
                    val patch = face.normal * halfExtent - face.uAxis * 2048.0 - face.vAxis * 2048.0
                    for (step in listOf(0.0, 4080.0)) {
                        quad(patch + face.uAxis * step, face.uAxis * 16.0, face.vAxis * 4096.0, 0xffc95c)
                        quad(patch + face.vAxis * step, face.uAxis * 4096.0, face.vAxis * 16.0, 0xffc95c)
                    }
                    quad(
                        face.normal * halfExtent - face.uAxis * 200.0 - face.vAxis * 12.0,
                        face.uAxis * 400.0,
                        face.vAxis * 24.0,
                        0xffffff,
                    )
                    quad(
                        face.normal * halfExtent - face.uAxis * 12.0 - face.vAxis * 200.0,
                        face.uAxis * 24.0,
                        face.vAxis * 400.0,
                        0xffffff,
                    )
                }
            }
            // An orange cross deliberately shows the gravitational centre through the reference
            // faces. Constant angular size keeps it useful at both low and high altitude.
            val right = Vector3f(1f, 0f, 0f).rotate(camera.rotation())
            val up = Vector3f(0f, 1f, 0f).rotate(camera.rotation())
            val markerSize = offset.length() * 0.009
            val u = Vector3(right.x.toDouble(), right.y.toDouble(), right.z.toDouble()) * markerSize
            val v = Vector3(up.x.toDouble(), up.y.toDouble(), up.z.toDouble()) * markerSize
            quad(u * -1.0 - v * 0.12, u * 2.0, v * 0.24, 0xffa640)
            quad(u * -0.12 - v, u * 0.24, v * 2.0, 0xffa640)
            BufferUploader.drawWithShader(buffer.buildOrThrow())
        } finally {
            stack.popMatrix()
            RenderSystem.applyModelViewMatrix()
            RenderSystem.setShaderColor(colour[0], colour[1], colour[2], colour[3])
            RenderSystem.setShader { shader }
        }
    }
}
