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

package org.cbi.orbitalis.neoforge

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.TickingBlockEntity
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import net.minecraft.util.Unit as MinecraftUnit

@PrefixGameTestTemplate(false)
object UnattendedShipGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "unattended_ticking", timeoutTicks = 100)
    fun loadedShipTicksWithoutPlayers(helper: GameTestHelper) {
        val orbit = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        helper.assertTrue(
            orbit.players().isEmpty() && orbit.forcedChunks.isEmpty(),
            "Test needs an unattended dimension without terrain tickets",
        )
        val container = checkNotNull(SubLevelContainer.getContainer(orbit))
        val body = container.allocateNewSubLevel(Pose3d().also { it.position().set(0.0, 100000.0, 0.0) }) as ServerSubLevel
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState(), 3)
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
        var ticks = 0
        orbit.addBlockEntityTicker(
            object : TickingBlockEntity {
                override fun tick() {
                    ticks++
                }

                override fun isRemoved(): Boolean = body.isRemoved

                override fun getPos(): BlockPos = body.plot.centerBlock

                override fun getType(): String = "orbitalis:unattended_test_ticker"
            },
        )
        // Force the real vanilla idle branch without waiting 300 ticks or adding a player.
        val idle = ServerLevel::class.java.getDeclaredField("emptyTime").also { it.isAccessible = true }
        idle.setInt(orbit, 600)
        helper.runAfterDelay(5) {
            val observed = ticks
            container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            helper.assertTrue(observed >= 3, "Loaded ship block tickers stopped in an empty dimension: $observed")
            helper.assertTrue(
                orbit.players().isEmpty() && orbit.forcedChunks.isEmpty(),
                "Ticking must not depend on players or terrain tickets",
            )
            idle.setInt(orbit, 600)
            helper.runAfterDelay(3) {
                helper.assertTrue(idle.getInt(orbit) >= 600, "Empty orbital world was kept awake after its last loaded ship was removed")
                helper.succeed()
            }
        }
    }
}
