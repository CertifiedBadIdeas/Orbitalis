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
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.client.space.SurfacePlanetRenderer
import org.cbi.orbitalis.minecraft.map.MapObject
import org.cbi.orbitalis.minecraft.map.MapRequest
import org.cbi.orbitalis.minecraft.map.MapSnapshot
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Screen-local camera and selection; the server remains the only source of object motion. */
class OrbitalMapScreen(
    private val toggleKey: net.minecraft.client.KeyMapping? = null,
) : Screen(Component.translatable("map.orbitalis.title")) {
    private val token = UUID.randomUUID()
    private val camera = MapCamera()
    private val maneuvers = ManeuverPanel(token)
    private var trajectories: MapTrajectories? = null
    private var pathRenderer: MapTrajectoryRenderer? = null
    private var snapshot: MapSnapshot? = null
    private var receivedAt = 0L
    private var selected: UUID? = null
    private var heartbeat = 0
    private var listOffset = 0
    private var startX = 0.0
    private var startY = 0.0
    private var dragged = false
    private var dragButton = -1
    private var panDrag = false
    private val panKeys = mutableSetOf<Int>()
    private var lastPanAt = 0L
    private var subscribed = false
    private var focusButton: Button? = null
    private var celestialBodies = emptyList<CelestialMapScene.Body>()
    private var bodyFocus: String? = null
    private var celestialRenderTick = 0L
    private var celestialRenderSeconds = 0.0
    private val celestialButtons = mutableListOf<Button>()
    private val viewX = 12
    private val viewY = 79
    private val panelWidth get() = (width * 0.28).toInt().coerceIn(140, 220)
    private val viewWidth get() = (width - panelWidth - 36).coerceAtLeast(1)
    private val viewHeight get() = (height - viewY - 37).coerceAtLeast(1)
    private val panelX get() = viewX + viewWidth + 12
    private val listY get() = viewY + 126
    private val listRows get() = ((height - 32 - listY) / 20).coerceIn(0, 12)
    private var items = emptyList<MapObject>()

    override fun init() {
        super.init()
        clearCameraInput()
        celestialButtons.clear()
        val frameWidth = ((viewWidth - 4) / 2).coerceIn(1, 110)
        val views: List<Pair<String, () -> Unit>> =
            listOf(
                "earth" to { resetEarthView() },
                "moon" to
                    {
                        frameBody(
                            SolarSystemProfile.MOON,
                            (snapshot?.celestialProfile?.geometry?.moonHalfSize ?: SolarSystemProfile.MOON_HALF_SIZE) * 3.0,
                        )
                    },
                "earth_moon" to { snapshot?.celestialProfile?.let { frameBody(SolarSystemProfile.EARTH, it.moonOrbitRadius * 1.3) } },
                "system" to { snapshot?.celestialProfile?.let { frameBody(SolarSystemProfile.SUN, it.earthOrbitRadius * 1.15) } },
            )
        for ((index, view) in views.withIndex()) {
            celestialButtons.add(
                addRenderableWidget(
                    Button
                        .builder(Component.translatable("map.orbitalis.view." + view.first)) { view.second() }
                        .bounds(viewX + (index % 2) * (frameWidth + 4), 29 + (index / 2) * 24, frameWidth, 20)
                        .build(),
                ),
            )
        }
        focusButton =
            addRenderableWidget(
                Button
                    .builder(Component.translatable("map.orbitalis.focus")) {
                        snapshot?.objects?.find { it.id == selected }?.let {
                            bodyFocus = null
                            camera.focus = it.position
                        }
                    }.bounds(panelX, viewY + 104, panelWidth - 12, 20)
                    .build(),
            )
        addRenderableWidget(Button.builder(Component.translatable("gui.done")) { onClose() }.bounds(width - 76, 9, 64, 20).build())
        maneuvers.init(font, panelX, viewY, panelWidth - 12) { addRenderableWidget(it) }
        if (!subscribed) {
            resetEarthView()
            trajectories = MapTrajectories()
            pathRenderer = MapTrajectoryRenderer()
            subscribed = true
            send(MapRequest(token, true))
        }
    }

    fun accept(value: MapSnapshot) {
        if (value.token != token || value.tick < (snapshot?.tick ?: -1L)) return
        snapshot = value
        celestialBodies = value.celestialProfile?.let { CelestialMapScene.sample(it, value.simulationTick) }.orEmpty()
        bodyFocus?.let { id -> celestialBodies.firstOrNull { it.id == id }?.let { camera.focus = it.position } }
        items =
            value.objects
                .map { it to label(it).lowercase(Locale.ROOT) }
                .sortedWith(compareBy<Pair<MapObject, String>> { it.second }.thenBy { it.first.id })
                .map { it.first }
        receivedAt = Util.getMillis()
        if (selected != null && value.objects.none { it.id == selected }) selected = null
        listOffset = listOffset.coerceIn(0, (value.objects.size - listRows).coerceAtLeast(0))
    }

    fun accept(value: org.cbi.orbitalis.minecraft.navigation.FlightPlanState) = maneuvers.accept(value)

    override fun tick() {
        if (minecraft?.player == null ||
            minecraft?.level?.let {
                !org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
                    .contains(it)
            } == true
        ) {
            onClose()
            return
        }
        if (++heartbeat >= 40) {
            heartbeat = 0
            send(MapRequest(token, true))
            maneuvers.poll()
        }
        selected = maneuvers.select(selected)
        maneuvers.tick()
        trajectories?.update(snapshot, selected, camera.focus, maneuvers.draft, maneuvers.clock)
        celestialButtons.forEachIndexed { index, button -> button.active = index == 0 || celestialBodies.isNotEmpty() }
        focusButton?.visible = !maneuvers.open
        focusButton?.active = snapshot?.objects?.any { it.id == selected } == true
    }

    // This screen paints its own opaque scene; vanilla's menu blur would blur that scene too.
    override fun renderBackground(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) = Unit

    override fun isPauseScreen() = false

    override fun removed() {
        clearCameraInput()
        pathRenderer?.close()
        pathRenderer = null
        trajectories?.close()
        trajectories = null
        if (subscribed) {
            send(MapRequest(token, false))
            subscribed = false
        }
        super.removed()
    }

    override fun keyPressed(
        key: Int,
        scan: Int,
        modifiers: Int,
    ): Boolean {
        if (focused is net.minecraft.client.gui.components.EditBox) return super.keyPressed(key, scan, modifiers)
        if (toggleKey?.matches(key, scan) == true) {
            onClose()
            return true
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            resetEarthView()
            return true
        }
        if (isPanKey(key)) {
            panKeys.add(key)
            return true
        }
        return super.keyPressed(key, scan, modifiers)
    }

    override fun keyReleased(
        key: Int,
        scan: Int,
        modifiers: Int,
    ): Boolean {
        if (panKeys.remove(key)) return true
        return super.keyReleased(key, scan, modifiers)
    }

    private fun isPanKey(key: Int) =
        key in
            setOf(
                GLFW.GLFW_KEY_W,
                GLFW.GLFW_KEY_A,
                GLFW.GLFW_KEY_S,
                GLFW.GLFW_KEY_D,
                GLFW.GLFW_KEY_UP,
                GLFW.GLFW_KEY_LEFT,
                GLFW.GLFW_KEY_DOWN,
                GLFW.GLFW_KEY_RIGHT,
            )

    private fun clearCameraInput() {
        panKeys.clear()
        dragButton = -1
        lastPanAt = Util.getMillis()
    }

    private fun updateCamera() {
        val now = Util.getMillis()
        val seconds = ((now - lastPanAt).coerceIn(0L, 100L)) / 1000.0
        lastPanAt = now
        if (minecraft?.isWindowActive != true || focused is net.minecraft.client.gui.components.EditBox) {
            clearCameraInput()
            return
        }

        fun down(
            first: Int,
            second: Int,
        ) = if (first in panKeys || second in panKeys) 1.0 else 0.0
        val dx = down(GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT) - down(GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT)
        val dy = down(GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN) - down(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP)
        val length = kotlin.math.hypot(dx, dy)
        if (length > 0.0) {
            bodyFocus = null
            val pixels = viewHeight * 0.8 * seconds / length
            camera.pan(dx * pixels, dy * pixels, viewHeight)
        }
    }

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        updateCamera()
        updateCelestialView()
        graphics.fill(0, 0, width, height, 0xFF080E19.toInt())
        graphics.drawString(font, title, 12, 11, 0xE8F4FF, false)
        graphics.fill(viewX - 1, viewY - 1, viewX + viewWidth + 1, viewY + viewHeight + 1, 0xFF294358.toInt())
        graphics.fill(viewX, viewY, viewX + viewWidth, viewY + viewHeight, 0xFF030812.toInt())
        graphics.flush()
        drawPlanet()
        graphics.enableScissor(viewX, viewY, viewX + viewWidth, viewY + viewHeight)
        if (Util.getMillis() - receivedAt <= 3000L) {
            for (burn in trajectories?.burns.orEmpty()) {
                marker(graphics, burn.state.position, 0xD08CFF, true, false)
                camera.project(burn.state.position, viewWidth, viewHeight)?.let {
                    graphics.drawString(font, "${burn.index + 1}", viewX + it.x.toInt() + 6, viewY + it.y.toInt() - 9, 0xD08CFF, false)
                }
            }
        }
        for (body in celestialBodies) {
            if (body.id == SolarSystemProfile.EARTH) continue
            val point = camera.project(body.position, viewWidth, viewHeight) ?: continue
            val x = viewX + point.x.roundToInt()
            val y = viewY + point.y.roundToInt()
            val color = if (body.id == SolarSystemProfile.SUN) 0xFFE19B else 0xCFD9E5
            val solarPixels = if (body.id == SolarSystemProfile.SUN) body.radius / camera.range * viewHeight * 0.5 else 0.0
            if (solarPixels <= 6.0) {
                for (row in -4..4) {
                    val extent = kotlin.math.sqrt((16 - row * row).toDouble()).toInt()
                    graphics.fill(x - extent, y + row, x + extent + 1, y + row + 1, color or 0xFF000000.toInt())
                }
            }
            graphics.drawString(
                font,
                Component.translatable("body." + body.id.replace(':', '.')),
                x + solarPixels.coerceAtMost(viewWidth.toDouble()).roundToInt() + 8,
                y - 4,
                color,
                false,
            )
        }
        snapshot?.let { data ->
            for (item in data.objects) {
                marker(
                    graphics,
                    item.position,
                    if (item.id ==
                        selected
                    ) {
                        0xFFE19B
                    } else if (item.loaded) {
                        0xD6E9FF
                    } else {
                        0x8C9AB1
                    },
                    item.id == selected,
                    false,
                )
            }
            marker(graphics, data.player, 0x55E5DD, false, true)
        }
        graphics.disableScissor()
        graphics.drawString(
            font,
            Component.translatable("map.orbitalis.scale", metric(camera.range * 2)),
            viewX + 8,
            viewY + viewHeight - 16,
            0x8098AF,
            false,
        )
        if (maneuvers.open) maneuvers.render(graphics, font, panelX, viewY, panelWidth - 12, height - 26) else drawPanel(graphics)
        font.split(Component.translatable("map.orbitalis.controls"), width - 24).take(2).forEachIndexed { index, line ->
            graphics.drawString(font, line, viewX, height - 24 + index * 10, 0x8098AF, false)
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun updateCelestialView() {
        val data = snapshot ?: return
        val profile = data.celestialProfile ?: return
        val level = minecraft?.level ?: return
        val sampled =
            if (org.cbi.orbitalis.minecraft.flight.CelestialFlight
                    .available(level)
            ) {
                org.cbi.orbitalis.minecraft.client.space.CelestialView
                    .sample()
                    .frames
            } else {
                null
            }
        celestialRenderTick = if (sampled?.profile == profile) sampled.tick else data.simulationTick
        celestialRenderSeconds = if (sampled?.profile == profile) sampled.seconds else 0.0
        celestialBodies = CelestialMapScene.sample(profile, celestialRenderTick, celestialRenderSeconds)
        CelestialMapScene.follow(camera, bodyFocus, celestialBodies)
    }

    private fun resetEarthView() {
        clearCameraInput()
        bodyFocus = null
        camera.reset()
        val profile = snapshot?.celestialProfile ?: org.cbi.orbitalis.minecraft.flight.CelestialFlight.clientProfileSnapshot
        camera.frame(Vector3.ZERO, (profile?.geometry?.earthHalfSize ?: 5000.0) * 1.7, viewWidth, viewHeight)
    }

    private fun frameBody(
        id: String,
        range: Double,
    ) {
        val body = celestialBodies.firstOrNull { it.id == id } ?: return
        bodyFocus = id
        camera.frame(body.position, range, viewWidth, viewHeight)
    }

    private fun drawPlanet() {
        val mc = checkNotNull(minecraft)
        val window = mc.window
        val sx = window.width.toDouble() / width
        val sy = window.height.toDouble() / height
        val x = (viewX * sx).roundToInt()
        val y = ((height - viewY - viewHeight) * sy).roundToInt()
        val w = (viewWidth * sx).roundToInt().coerceAtLeast(1)
        val h = (viewHeight * sy).roundToInt().coerceAtLeast(1)
        val viewport = IntArray(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val shader = RenderSystem.getShader()
        try {
            RenderSystem.viewport(x, y, w, h)
            RenderSystem.enableScissor(x, y, w, h)
            RenderSystem.depthMask(true)
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.disableBlend()
            RenderSystem.disableCull()
            val direction = camera.direction
            val up = camera.up
            val view =
                Matrix4f().lookAt(
                    direction.x.toFloat(),
                    direction.y.toFloat(),
                    direction.z.toFloat(),
                    0f,
                    0f,
                    0f,
                    up.x.toFloat(),
                    up.y.toFloat(),
                    up.z.toFloat(),
                )
            val aspect = viewWidth.toFloat() / viewHeight
            val projection = Matrix4f().ortho(-aspect, aspect, -1f, 1f, -100f, 100f)
            snapshot?.celestialProfile?.let { profile ->
                org.cbi.orbitalis.minecraft.client.space.SolarRenderer.render(
                    profile,
                    celestialRenderTick,
                    celestialRenderSeconds,
                    camera.focus,
                    view,
                    projection,
                    1.0 / camera.range,
                )
            }
            SurfacePlanetRenderer.renderScene(
                camera.focus,
                view,
                projection,
                1.0 / camera.range,
            )
            snapshot?.let { data ->
                data.celestialProfile?.let { profile ->
                    org.cbi.orbitalis.minecraft.client.space.LunarPlanetRenderer.render(
                        profile,
                        celestialRenderTick,
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight.lunarSeed,
                        camera.focus,
                        view,
                        projection,
                        1.0 / camera.range,
                        celestialRenderSeconds,
                    )
                }
            }
            if (Util.getMillis() - receivedAt <= 3000L) {
                pathRenderer?.render(camera, view, projection, trajectories?.paths.orEmpty(), selected, celestialBodies)
            }
        } finally {
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
            RenderSystem.disableScissor()
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            RenderSystem.depthMask(mask)
            RenderSystem.depthFunc(func)
            RenderSystem.setShader { shader }
        }
    }

    private fun marker(
        graphics: GuiGraphics,
        position: Vector3,
        color: Int,
        chosen: Boolean,
        player: Boolean,
    ) {
        val point = camera.project(position, viewWidth, viewHeight) ?: return
        val x = viewX + point.x.roundToInt()
        val y = viewY + point.y.roundToInt()
        val rgb = color or 0xFF000000.toInt()
        if (player) {
            graphics.fill(x - 4, y - 1, x + 5, y + 2, rgb)
            graphics.fill(x - 1, y - 4, x + 2, y + 5, rgb)
        } else {
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF080E19.toInt())
            graphics.renderOutline(x - 3, y - 3, 7, 7, rgb)
        }
        if (chosen) graphics.renderOutline(x - 6, y - 6, 13, 13, rgb)
    }

    private fun drawPanel(graphics: GuiGraphics) {
        val data = snapshot

        fun line(
            y: Int,
            component: Component,
            color: Int = 0xB9CCDC,
        ) {
            graphics.drawString(
                font,
                font.plainSubstrByWidth(
                    component.string,
                    panelWidth - 14,
                ),
                panelX,
                viewY + y,
                color,
                false,
            )
        }
        line(0, Component.translatable("map.orbitalis.objects", data?.objects?.size ?: 0, data?.total ?: 0), 0xE8F4FF)
        if (data == null || Util.getMillis() - receivedAt > 3000L) {
            line(12, Component.translatable("map.orbitalis.waiting"), 0xFFC078)
        } else {
            line(
                12,
                Component.translatable(
                    "map.orbitalis.you",
                    metric(data.player.y - org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SEA_LEVEL),
                ),
                0x55E5DD,
            )
        }
        val item = data?.objects?.find { it.id == selected }
        if (item == null) {
            line(28, Component.translatable("map.orbitalis.select"))
        } else {
            line(28, Component.literal(label(item)), 0xFFE19B)
            line(40, Component.translatable(if (item.loaded) "map.orbitalis.loaded" else "map.orbitalis.unloaded"))
            line(
                52,
                Component.translatable(
                    "map.orbitalis.altitude",
                    metric(item.position.y - org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SEA_LEVEL),
                ),
            )
            line(64, Component.translatable("map.orbitalis.speed", number(item.velocity.length())))
            line(76, Component.translatable("map.orbitalis.distance", metric((item.position - data.player).length())))
            val path =
                trajectories
                    ?.paths
                    .orEmpty()
                    .firstOrNull { it.source.id == selected }
                    ?.prediction
            line(
                88,
                if (item.position.length() >
                    100000.0
                ) {
                    Component.translatable("map.orbitalis.forecast.unavailable")
                } else if (path ==
                    null
                ) {
                    Component.translatable("map.orbitalis.forecast.pending")
                } else {
                    Component.translatable(
                        "map.orbitalis.forecast." + path.end.name.lowercase(Locale.ROOT),
                        number(path.terminal.seconds),
                    )
                },
            )
        }
        for ((index, entry) in items.drop(listOffset).take(listRows).withIndex()) {
            val y = listY + index * 20
            graphics.fill(panelX, y, width - 12, y + 18, if (entry.id == selected) 0xFF284058.toInt() else 0xFF111F2E.toInt())
            graphics.drawString(
                font,
                font.plainSubstrByWidth(label(entry), panelWidth - 24),
                panelX + 5,
                y + 5,
                if (entry.id ==
                    selected
                ) {
                    0xFFE19B
                } else {
                    0xB9CCDC
                },
                false,
            )
        }
    }

    private fun inView(
        x: Double,
        y: Double,
    ) = x >= viewX && x < viewX + viewWidth && y >= viewY && y < viewY + viewHeight

    override fun mouseClicked(
        x: Double,
        y: Double,
        button: Int,
    ): Boolean {
        if (super.mouseClicked(x, y, button)) return true
        if (button == 1 && inView(x, y) && selected != null && Util.getMillis() - receivedAt <= 3000L) {
            trajectories
                ?.paths
                ?.firstOrNull { it.source.id == selected && !it.planned }
                ?.prediction
                ?.points
                ?.map { point ->
                    val projected = camera.projectRaw(point.state.position, viewWidth, viewHeight)
                    point to kotlin.math.hypot(projected.x + viewX - x, projected.y + viewY - y)
                }?.filter { it.second <= 10.0 && it.first.seconds >= 1.0 }
                ?.minByOrNull { it.second }
                ?.first
                ?.let { maneuvers.addAt(it.seconds) }
            return true
        }
        if ((button == 0 || button == 2) && inView(x, y)) {
            setFocused(null)
            dragButton = button
            panDrag = button == 2 || hasShiftDown()
            startX = x
            startY = y
            dragged = false
            return true
        }
        if (!maneuvers.open && button == 0 && x >= panelX && x < width - 12 && y >= listY && y < listY + listRows * 20) {
            items.getOrNull(listOffset + ((y - listY) / 20).toInt())?.let { selected = it.id }
            return true
        }
        return false
    }

    override fun mouseDragged(
        x: Double,
        y: Double,
        button: Int,
        dx: Double,
        dy: Double,
    ): Boolean {
        if (button == dragButton) {
            if (kotlin.math.hypot(x - startX, y - startY) > 3.0) dragged = true
            if (panDrag) {
                bodyFocus = null
                camera.pan(dx, dy, viewHeight)
            } else if (dragged) {
                camera.rotate(dx, dy)
            }
            return true
        }
        return super.mouseDragged(x, y, button, dx, dy)
    }

    override fun mouseReleased(
        x: Double,
        y: Double,
        button: Int,
    ): Boolean {
        if (button == dragButton) {
            if (!panDrag && !maneuvers.open && !dragged && inView(x, y)) {
                selected =
                    snapshot
                        ?.objects
                        ?.mapNotNull { item ->
                            camera.project(item.position, viewWidth, viewHeight)?.let {
                                item.id to
                                    kotlin.math.hypot(it.x + viewX - x, it.y + viewY - y)
                            }
                        }?.filter { it.second <= 9.0 }
                        ?.minByOrNull { it.second }
                        ?.first
            }
            dragButton = -1
            return true
        }
        return super.mouseReleased(x, y, button)
    }

    override fun mouseScrolled(
        x: Double,
        y: Double,
        horizontal: Double,
        vertical: Double,
    ): Boolean {
        if (inView(x, y)) {
            camera.zoom(vertical)
            return true
        }
        if (!maneuvers.open && x >= panelX &&
            y >= listY
        ) {
            listOffset = (listOffset - vertical.toInt()).coerceIn(0, (items.size - listRows).coerceAtLeast(0))
            return true
        }
        return super.mouseScrolled(x, y, horizontal, vertical)
    }

    companion object {
        var send: (MapRequest) -> Unit = {}

        private fun label(item: MapObject) =
            item.name.ifBlank { Component.translatable("map.orbitalis.object", item.id.toString().take(8)).string }

        private fun number(value: Double) = String.format(Locale.ROOT, "%.1f", value)

        private fun metric(value: Double) = if (kotlin.math.abs(value) >= 1000.0) "${number(value / 1000.0)} km" else "${number(value)} m"
    }
}
