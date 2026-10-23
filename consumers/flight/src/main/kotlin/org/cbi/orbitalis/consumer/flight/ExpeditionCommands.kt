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

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** Explicit, non-destructive fixture placement in the development client only. */
@EventBusSubscriber(modid = FlightConsumerMod.ID)
object ExpeditionCommands {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands
                .literal("orbitalis_test")
                .requires { it.hasPermission(2) }
                .then(
                    Commands
                        .literal("expedition")
                        .executes { context ->
                            val source = context.source
                            val player = source.playerOrException
                            if (source.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                                throw SimpleCommandExceptionType(
                                    Component.literal("Create the expedition ship at an Overworld launch site."),
                                ).create()
                            }
                            val origin = player.blockPosition().offset(0, 1, 24)
                            val level = source.level
                            val minimum = origin.offset(-6, 0, -15)
                            val maximum = origin.offset(6, 9, 15)
                            if (minimum.y < level.minBuildHeight || maximum.y >= level.maxBuildHeight ||
                                BlockPos.betweenClosed(minimum, maximum).any { !level.hasChunkAt(it) || !level.isEmptyBlock(it) }
                            ) {
                                throw SimpleCommandExceptionType(
                                    Component.literal(
                                        "Need a loaded, empty 13 x 31 x 10 space, 24 blocks south and one block above your feet.",
                                    ),
                                ).create()
                            }
                            ExpeditionBlueprint.place(level, origin)
                            ExpeditionManualControls.equip(source)
                            source.sendSuccess({
                                Component.literal(
                                    "Fuelled expedition ship placed at $origin. Board through the aft hatch, wait for pumps, " +
                                        "then hold right-click on the cabin Physics Assembler, drag upward and release. " +
                                        "Programmable flight control is provided by Compukters. " +
                                        "Finite stock: ${ExpeditionFuelSupply.INITIAL_FUEL_MB / 1000} buckets turpentine, " +
                                        "${ExpeditionFuelSupply.COAL_BLOCKS_PER_ENGINE * ExpeditionFuelSupply.powerOffsets.size} coal blocks.",
                                )
                            }, false)
                            1
                        },
                ),
        )
    }
}
