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

package org.cbi.orbitalis.consumer.flight

import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel

/** Native Create radio inputs; switch the controller off before handing engines to autopilot. */
object ExpeditionManualControls {
    private val frequencies =
        mapOf(
            Direction.NORTH to "red_dye",
            Direction.SOUTH to "blue_dye",
            Direction.WEST to "green_dye",
            Direction.EAST to "yellow_dye",
            Direction.UP to "diamond",
            Direction.DOWN to "feather",
        )

    fun place(
        level: ServerLevel,
        origin: BlockPos,
    ) {
        val source =
            level.server
                .createCommandSourceStack()
                .withLevel(level)
                .withSuppressedOutput()
        for (engine in ExpeditionFuelSupply.engines) {
            val attachment = if (engine.thrust.axis == Direction.Axis.Z) Direction.EAST else Direction.SOUTH
            val pos = origin.offset(engine.offset).relative(attachment)
            val frequency = frequencies.getValue(engine.thrust)
            val command =
                "setblock ${pos.x} ${pos.y} ${pos.z} create:redstone_link[facing=${attachment.serializedName},receiver=true]" +
                    "{FrequencyFirst:{id:\"minecraft:$frequency\",count:1},FrequencyLast:{id:\"minecraft:cobblestone\",count:1}}"
            check(
                level.server.commands.dispatcher
                    .execute(command, source) > 0,
            ) { "Could not place manual receiver at $pos" }
        }
    }

    fun equip(source: CommandSourceStack) {
        val items =
            listOf("red_dye", "blue_dye", "green_dye", "yellow_dye", "diamond", "feather")
                .flatMap { listOf(it, "cobblestone") }
                .mapIndexed { index, item -> "{slot:$index,item:{id:\"minecraft:$item\",count:1}}" }
                .joinToString(",")
        source.server.commands.performPrefixedCommand(source, "give @s create:linked_controller[create:linked_controller_items=[$items]]")
        source.server.commands.performPrefixedCommand(source, "give @s create:wrench")
        source.server.commands.performPrefixedCommand(source, "give @s minecraft:lever")
    }
}
