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
import com.mojang.blaze3d.platform.GlStateManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ReceivingLevelScreen
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.PlanetFlightEnvelope
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.util.UUID

/** Optional native-ship adapter; default client loading never resolves optional Sable classes. */
object FlightTransitionContact {
    var ship: () -> UUID? = { null }
    var ready: (UUID) -> Boolean = { false }
}

/** One bounded last-frame texture; native loading, screen ticks and server authority continue. */
object FlightTransitionView {
    private val timing = HeldFrameTiming()
    private var image: TextureTarget? = null
    private var source: ResourceKey<Level>? = null
    private var destination: ResourceKey<Level>? = null
    private var connection: Any? = null
    private var capturedAt = 0.0
    private var expectedShip: UUID? = null
    private var lastContactAt = 0.0
    private var previousPosition: Vector3? = null
    private var previousAt = 0.0
    private var previousDimension: ResourceKey<Level>? = null
    val active get() = timing.active

    private fun now() = System.nanoTime() * 1e-9

    private fun supported(dimension: ResourceKey<Level>) = dimension == Level.OVERWORLD || dimension == FlatPlanetSpace.SPACE

    fun begin(target: ResourceKey<Level>) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (active) {
            // Recovery or another respawn must expose native loading instead of holding a stale trip.
            clear()
            return
        }
        if (target == level.dimension()) return
        if (!supported(target) || !supported(level.dimension()) || mc.player?.isAlive != true) {
            clear()
            return
        }
        if (image == null || source != level.dimension() || connection !== mc.connection || now() - capturedAt > 1.0) return
        destination = target
        timing.begin(now())
    }

    /** Rendering alone is suppressed; the native ReceivingLevelScreen still ticks and closes itself. */
    fun hideLoadingScreen(): Boolean {
        if (!validImage() || !active) return false
        if (timing.opacity(now(), false) <= 0f) {
            clear()
            return false
        }
        return true
    }

    private fun validImage(): Boolean {
        val mc = Minecraft.getInstance()
        val frame = image ?: return false
        if (connection !== mc.connection || frame.width != mc.mainRenderTarget.width || frame.height != mc.mainRenderTarget.height) {
            clear()
            return false
        }
        return true
    }

    fun frame() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level == null || mc.player?.isAlive != true || mc.connection == null) {
            clear()
            return
        }
        val time = now()
        if (active) {
            if (!validImage()) return
            if ((mc.screen != null && mc.screen !is ReceivingLevelScreen) ||
                (level.dimension() != source && level.dimension() != destination)
            ) {
                clear()
                return
            }
            val ready =
                level.dimension() == destination && mc.screen == null &&
                    (expectedShip?.let(FlightTransitionContact.ready) ?: true)
            val opacity = timing.opacity(time, ready)
            if (opacity > 0f) {
                ScreenTexture.draw(checkNotNull(image).colorTextureId, opacity)
            } else {
                clear()
            }
            return
        }
        if (!supported(level.dimension()) || mc.screen != null) {
            releaseImage()
            return
        }
        val p = mc.gameRenderer.mainCamera.position
        val position = FlightSpaceCoordinates.globalPosition(level, Vector3(p.x, p.y, p.z))
        val dt = time - previousAt
        val velocity =
            if (previousDimension == level.dimension() && dt in 0.001..0.5) {
                (position - checkNotNull(previousPosition)) * (1.0 / dt)
            } else {
                Vector3.ZERO
            }
        previousPosition = position
        previousAt = time
        previousDimension = level.dimension()
        val ascending = level.dimension() == Level.OVERWORLD
        val arrival = PlanetFlightEnvelope.arrival(position, velocity, Vector3.ZERO, ascending, 3.0)
        val eligible = arrival != null
        if (!eligible) {
            if (time - capturedAt > 1.0) releaseImage()
            return
        }
        val ship = FlightTransitionContact.ship()
        if (ship == null && expectedShip != null && source == level.dimension() && time - lastContactAt < 0.5) return
        capture()
        source = level.dimension()
        connection = mc.connection
        expectedShip = ship
        if (ship != null) lastContactAt = time
        capturedAt = time
    }

    private fun capture() {
        val mc = Minecraft.getInstance()
        val main = mc.mainRenderTarget
        val draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        try {
            var frame = image
            if (frame == null || frame.width != main.width || frame.height != main.height) {
                releaseImage()
                frame = TextureTarget(main.width, main.height, false, Minecraft.ON_OSX)
                image = frame
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId)
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frame.frameBufferId)
            GL30.glBlitFramebuffer(
                0,
                0,
                main.width,
                main.height,
                0,
                0,
                frame.width,
                frame.height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST,
            )
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw)
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read)
        }
    }

    private fun releaseImage() {
        image?.destroyBuffers()
        image = null
        source = null
        expectedShip = null
    }

    fun clear() {
        timing.clear()
        releaseImage()
        destination = null
        connection = null
        previousPosition = null
        previousDimension = null
    }
}
