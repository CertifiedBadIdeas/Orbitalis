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

package org.cbi.orbitalis.minecraft.client.ship

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.joml.Vector3d
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Attitude follows the hull, never the observation camera. +Y rocket / +Z ship nose presets. */
object ShipNavball {
    private const val PIXELS = 96
    private val gravity = FlatPlanetSpace.gravityField(1.0)
    private val normals =
        Array(PIXELS * PIXELS) { index ->
            ShipViewMath.hemisphere(
                (index % PIXELS + 0.5 - PIXELS / 2) / (PIXELS / 2 - 2.0),
                (PIXELS / 2 - index / PIXELS - 0.5) / (PIXELS / 2 - 2.0),
            )
        }
    private var texture: DynamicTexture? = null
    private var location: net.minecraft.resources.ResourceLocation? = null
    private var rocketAxes = true
    private var lastSnapshot = Int.MIN_VALUE
    private var lastSnapshotAt = 0L
    private var velocityShip: java.util.UUID? = null

    fun cycleAxes() {
        rocketAxes = !rocketAxes
    }

    private fun instrument(local: Vector3d): Vector3 = ShipViewMath.instrument(Vector3(local.x, local.y, local.z), rocketAxes)

    fun render(
        gui: GuiGraphics,
        partialTick: Float,
    ) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.options.hideGui || minecraft.screen != null) return
        val body = SableShipView.contact() ?: return
        if (!FlatPlanetSpace.contains(body.level) ||
            !org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .available(body.level)
        ) {
            return
        }
        val pose = body.renderPose(partialTick)
        val position = pose.position()
        val global = FlightSpaceCoordinates.globalPosition(body.level, Vector3(position.x(), position.y(), position.z()))
        val sample =
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .sample(body.level)
        val nativeState =
            org.cbi.orbitalis.core.planet
                .CartesianState(Vector3(position.x(), position.y(), position.z()), Vector3.ZERO)
        val chart =
            sample.frames.frame(
                org.cbi.orbitalis.minecraft.flight.CelestialFlight
                    .chart(body.level.dimension()),
            )
        val physical = chart.toParent(nativeState)
        val acceleration = chart.fromParentAcceleration(physical, sample.dynamics.acceleration(physical.position))
        if (acceleration.length() < 1e-8) return
        val upWorld = acceleration.normalized() * -1.0

        fun local(direction: Vector3) = instrument(pose.orientation().transformInverse(Vector3d(direction.x, direction.y, direction.z)))
        val world = NavballGrid.worldFrame(upWorld)
        val frame = NavballGrid.Frame(local(world.up), local(world.north), local(world.east))
        if (texture == null) {
            texture = DynamicTexture(NativeImage(PIXELS, PIXELS, false))
            location = minecraft.textureManager.register("orbitalis_navball", texture!!)
        }
        val image = checkNotNull(texture!!.pixels)
        for (y in 0 until PIXELS) {
            for (x in 0 until PIXELS) {
                val n = normals[y * PIXELS + x]
                val color =
                    if (n == null) {
                        0
                    } else {
                        val sky = n.dot(frame.up) > 0.0
                        // NativeImage uses ABGR; keep distinct sky/ground under the grid.
                        when (NavballGrid.line(n, frame)) {
                            NavballGrid.Line.HORIZON -> 0xffe8eee9.toInt()
                            NavballGrid.Line.MAJOR -> if (sky) 0xffeed2aa.toInt() else 0xffa0bccd.toInt()
                            NavballGrid.Line.MINOR -> if (sky) 0xffdda76a.toInt() else 0xff5c7b9e.toInt()
                            NavballGrid.Line.NONE -> if (sky) 0xffd68b30.toInt() else 0xff284e83.toInt()
                        }
                    }
                image.setPixelRGBA(x, y, color)
            }
        }
        texture!!.upload()
        val size = minOf(96, gui.guiWidth() / 4, (gui.guiHeight() - 50).coerceAtLeast(48))
        val left = gui.guiWidth() - size - 8
        val top = gui.guiHeight() - size - 28
        com.mojang.blaze3d.systems.RenderSystem
            .enableBlend()
        com.mojang.blaze3d.systems.RenderSystem
            .defaultBlendFunc()
        gui.blit(checkNotNull(location), left, top, 0f, 0f, size, size, size, size)
        com.mojang.blaze3d.systems.RenderSystem
            .disableBlend()
        val cx = left + size / 2
        val cy = top + size / 2
        graduations(gui, cx, cy, size, frame)
        gui.fill(cx - 8, cy, cx - 2, cy + 1, -1)
        gui.fill(cx + 3, cy, cx + 9, cy + 1, -1)
        gui.fill(cx, cy - 2, cx + 1, cy + 4, -1)

        // Native received pose samples, not rider position (which includes seat/teleport offsets).
        val buffer = body.interpolator.buffer
        val samples = synchronized(buffer) { buffer.toList().takeLast(2) }
        var speed: Double? = null
        if (samples.size == 2) {
            val a = samples[0]
            val b = samples[1]
            if (velocityShip != body.uniqueId || lastSnapshot != b.gameTick()) {
                velocityShip = body.uniqueId
                lastSnapshot = b.gameTick()
                lastSnapshotAt = System.nanoTime()
            }
            val ticks = b.gameTick() - a.gameTick()
            if (ticks in 1..20 && System.nanoTime() - lastSnapshotAt < 500_000_000L) {
                val velocity = Vector3d(b.pose().position()).sub(a.pose().position()).mul(20.0 / ticks)
                speed = velocity.length()
                val local = instrument(pose.orientation().transformInverse(velocity))
                marker(gui, cx, cy, size, local, false)
                marker(gui, cx, cy, size, local * -1.0, true)
            }
        }
        gui.drawCenteredString(minecraft.font, if (rocketAxes) "+Y" else "+Z", cx, top - 10, -1)
        val label = speed?.let { String.format(Locale.ROOT, "%.1f m/s", it) } ?: "— m/s"
        gui.drawCenteredString(minecraft.font, label, cx, top + size + 2, -1)
    }

    private fun graduations(
        gui: GuiGraphics,
        cx: Int,
        cy: Int,
        size: Int,
        frame: NavballGrid.Frame,
    ) {
        val radius = size / 2.0 - 1
        for (angle in 0 until 360 step 15) {
            val radians = Math.toRadians(angle.toDouble())
            val length = if (angle % 30 == 0) 4 else 2
            for (step in 0 until length) {
                val r = radius - step
                val x = cx + (sin(radians) * r).roundToInt()
                val y = cy - (cos(radians) * r).roundToInt()
                gui.fill(x, y, x + 1, y + 1, 0xffe6e6df.toInt())
            }
        }
        val font = Minecraft.getInstance().font
        for (pitch in listOf(-60, -30, 30, 60)) {
            val point = (0 until 360 step 90).map { NavballGrid.point(frame, pitch.toDouble(), it.toDouble()) }.maxBy { it.z }
            if (point.z < 0.3) continue
            val x = cx + point.x * (radius - 4)
            val y = cy - point.y * (radius - 4)
            if (kotlin.math.abs(x - cx) < 12 && kotlin.math.abs(y - cy) < 9) continue
            gui.pose().pushPose()
            try {
                gui.pose().translate(x, y - 2, 0.0)
                gui.pose().scale(0.65f, 0.65f, 1f)
                gui.drawCenteredString(font, if (pitch > 0) "+$pitch" else "$pitch", 0, 0, -1)
            } finally {
                gui.pose().popPose()
            }
        }
        val horizontal = sqrt(frame.up.x * frame.up.x + frame.up.y * frame.up.y)
        if (horizontal > 0.05) {
            val x = cx + (frame.up.x / horizontal * (radius - 5)).roundToInt()
            val y = cy - (frame.up.y / horizontal * (radius - 5)).roundToInt()
            gui.fill(x - 1, y - 1, x + 2, y + 2, 0xffffd060.toInt())
        }
    }

    private fun marker(
        gui: GuiGraphics,
        cx: Int,
        cy: Int,
        size: Int,
        velocity: Vector3,
        retrograde: Boolean,
    ) {
        val n = ShipViewMath.marker(velocity) ?: return
        val x = cx + (n.x * (size / 2 - 3)).roundToInt()
        val y = cy - (n.y * (size / 2 - 3)).roundToInt()
        val color = if (retrograde) 0xffff9560.toInt() else 0xffffff40.toInt()
        gui.fill(x - 3, y - 3, x + 4, y - 2, color)
        gui.fill(x - 3, y + 3, x + 4, y + 4, color)
        gui.fill(x - 3, y - 2, x - 2, y + 3, color)
        gui.fill(x + 3, y - 2, x + 4, y + 3, color)
        if (retrograde) {
            gui.fill(x - 1, y - 1, x + 2, y + 2, color)
        } else {
            gui.fill(x - 6, y, x - 3, y + 1, color)
            gui.fill(x + 4, y, x + 7, y + 1, color)
            gui.fill(x, y - 6, x + 1, y - 3, color)
        }
    }
}
