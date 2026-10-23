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

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import org.cbi.orbitalis.core.planet.ManeuverNode
import org.cbi.orbitalis.core.planet.ManeuverPlan
import org.cbi.orbitalis.minecraft.navigation.FlightPlanCommand
import org.cbi.orbitalis.minecraft.navigation.FlightPlanState
import java.util.UUID

/** Local draft only; saved revisions always come from the server. */
class ManeuverPanel(
    private val token: UUID,
) {
    var open = false
        private set
    var ship: UUID? = null
        private set
    var draft: ManeuverPlan? = null
        private set
    var clock = 0L
        private set
    private var received: FlightPlanState? = null
    private var revision = 0L
    private var index = 0
    private var dirty = false
    private var pending = false
    private var editing = false
    private var valid = true
    private var error = ""
    private val widgets = mutableListOf<AbstractWidget>()
    private val fields = mutableListOf<EditBox>()
    private var toggle: Button? = null
    private var save: Button? = null
    private var add: Button? = null
    private var remove: Button? = null

    fun init(
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        register: (AbstractWidget) -> Unit,
    ) {
        widgets.clear()
        fields.clear()

        fun button(
            label: String,
            bx: Int,
            by: Int,
            w: Int,
            action: () -> Unit,
        ): Button {
            val b = Button.builder(Component.translatable("map.orbitalis.plan." + label)) { action() }.bounds(bx, by, w, 20).build()
            widgets.add(b)
            register(b)
            return b
        }
        toggle =
            button("edit", x, 29, width) {
                open = !open
                refresh()
            }
        val part = (width - 9) / 4
        button("previous", x, y, part) {
            if (!flush()) return@button
            index = (index - 1).coerceAtLeast(0)
            populate()
        }
        button("next", x + part + 3, y, part) {
            if (!flush()) return@button
            index = (index + 1).coerceAtMost((draft?.nodes?.lastIndex ?: 0).coerceAtLeast(0))
            populate()
        }
        add =
            button("add", x + 2 * (part + 3), y, part) {
                if (flush()) {
                    val old = draft ?: return@button
                    val plan = if (old.nodes.isEmpty()) old.copy(epochTick = received?.gameTick ?: clock) else old
                    val time = maxOf((clock - plan.epochTick) / 20.0 + 120.0, (plan.nodes.lastOrNull()?.seconds ?: 0.0) + 120.0)
                    if (time <= 3600.0) {
                        draft = plan.copy(nodes = plan.nodes + ManeuverNode(time, 0.0, 0.0, 0.0))
                        index = plan.nodes.size
                        dirty = true
                        populate()
                    } else {
                        error = Component.translatable("map.orbitalis.plan.expired").string
                    }
                }
            }
        remove =
            button("remove", x + 3 * (part + 3), y, width - 3 * (part + 3)) {
                draft?.let {
                    draft = it.copy(nodes = it.nodes.filterIndexed { n, _ -> n != index })
                    index =
                        index.coerceAtMost((draft!!.nodes.size - 1).coerceAtLeast(0))
                    dirty = true
                    populate()
                }
            }
        repeat(4) { row ->
            val box = EditBox(font, x + 55, y + 25 + row * 19, width - 55, 18, Component.translatable("map.orbitalis.plan.field.$row"))
            box.setMaxLength(18)
            box.setResponder {
                if (!editing) {
                    dirty = true
                    flush()
                }
            }
            fields.add(box)
            widgets.add(box)
            register(box)
        }
        val half = (width - 4) / 2
        save = button("save", x, y + 104, half) { if (flush()) command(FlightPlanCommand.Action.SAVE) }
        button("reload", x + half + 4, y + 104, width - half - 4) {
            dirty = false
            pending = false
            command(FlightPlanCommand.Action.READ)
        }
        populate()
    }

    fun select(id: UUID?): UUID? {
        if (id == ship) return ship
        if (dirty) {
            open = true
            error = Component.translatable("map.orbitalis.plan.change_selection").string
            return ship
        }
        ship = id
        received = null
        draft = null
        revision = 0
        index = 0
        dirty = false
        pending = false
        error = ""
        open = false
        populate()
        command(FlightPlanCommand.Action.READ)
        return ship
    }

    fun addAt(seconds: Double) {
        if (!flush()) return
        val old = draft ?: return
        val plan = if (old.nodes.isEmpty()) old.copy(epochTick = received?.gameTick ?: clock) else old
        try {
            require(plan.nodes.size < 8) { "A plan supports at most eight nodes" }
            val node = ManeuverNode((clock - plan.epochTick) / 20.0 + seconds, 0.0, 0.0, 0.0)
            draft = plan.copy(nodes = (plan.nodes + node).sortedBy { it.seconds })
            index = draft!!.nodes.indexOf(node)
            dirty = true
            open = true
            populate()
        } catch (failure: IllegalArgumentException) {
            error = failure.message.orEmpty()
        }
    }

    fun accept(state: FlightPlanState) {
        if (state.token != token || state.ship != ship || state.gameTick < (received?.gameTick ?: -1L)) return
        clock = state.gameTick
        received = state
        if (state.error.isNotEmpty()) {
            error = state.error
            pending = false
        }
        if (!dirty || (pending && state.revision > revision)) {
            draft = state.plan
            revision = state.revision
            dirty = false
            pending = false
            index =
                index.coerceAtMost((state.plan.nodes.size - 1).coerceAtLeast(0))
            populate()
        }
        refresh()
    }

    fun tick() {
        if (received != null) clock++
        refresh()
    }

    fun poll() {
        command(FlightPlanCommand.Action.READ)
    }

    private fun command(action: FlightPlanCommand.Action) {
        val id = ship ?: return
        if (action != FlightPlanCommand.Action.READ) error = ""
        if (action == FlightPlanCommand.Action.SAVE) pending = true
        send(FlightPlanCommand(token, id, action, revision, draft ?: ManeuverPlan(0, emptyList())))
    }

    private fun flush(): Boolean {
        val plan = draft ?: return false
        if (plan.nodes.isEmpty()) {
            valid = true
            refresh()
            return true
        }
        return try {
            val v = fields.map { it.value.toDoubleOrNull() ?: throw IllegalArgumentException("Enter four finite numbers") }
            val n = ManeuverNode(v[0], v[1], v[2], v[3])
            draft = plan.copy(nodes = plan.nodes.mapIndexed { i, old -> if (i == index) n else old })
            valid = true
            error = ""
            refresh()
            true
        } catch (failure: IllegalArgumentException) {
            valid = false
            error = failure.message.orEmpty()
            refresh()
            false
        }
    }

    private fun populate() {
        editing = true
        val n = draft?.nodes?.getOrNull(index)
        val v = n?.let { listOf(it.seconds, it.prograde, it.normal, it.radial) }
        fields.forEachIndexed { i, box -> box.value = v?.get(i)?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "" }
        editing = false
        valid = true
        refresh()
    }

    private fun refresh() {
        widgets.forEach {
            it.visible = open && ship != null
            it.active = received != null && !pending
        }
        toggle?.let {
            it.visible = true
            it.active = ship != null
            it.message =
                Component.translatable("map.orbitalis.plan." + if (open) "objects" else "edit")
        }
        fields.forEach { it.active = open && draft?.nodes?.isNotEmpty() == true && !pending }
        save?.active = dirty && valid && !pending
        add?.active = !pending && received != null && (draft?.nodes?.size ?: 0) < 8
        remove?.active = !pending && draft?.nodes?.isNotEmpty() == true
    }

    fun render(
        g: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (!open) return
        for (i in 0..3) g.drawString(font, Component.translatable("map.orbitalis.plan.field.$i"), x, y + 30 + i * 19, 0xB9CCDC, false)
        val count = draft?.nodes?.size ?: 0
        val status =
            when {
                error.isNotEmpty() -> {
                    error
                }

                pending -> {
                    Component.translatable("map.orbitalis.plan.saving").string
                }

                dirty -> {
                    Component.translatable("map.orbitalis.plan.unsaved").string
                }

                else -> {
                    Component
                        .translatable(
                            "map.orbitalis.plan.node",
                            if (count ==
                                0
                            ) {
                                0
                            } else {
                                index + 1
                            },
                            count,
                        ).string + " · T+%.0f s".format(java.util.Locale.ROOT, (clock - (draft?.epochTick ?: clock)) / 20.0)
                }
            }
        font.split(Component.literal(status), width).take(((height - y - 151) / 10).coerceIn(1, 4)).forEachIndexed { i, line ->
            g.drawString(
                font,
                line,
                x,
                y + 151 + i * 10,
                if (error.isEmpty()) 0xB9CCDC else 0xFF9797,
                false,
            )
        }
    }

    companion object {
        var send: (FlightPlanCommand) -> Unit = {}
    }
}
