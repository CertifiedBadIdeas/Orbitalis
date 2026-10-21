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

import com.simibubi.create.api.connectivity.ConnectivityHandler
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock
import com.simibubi.create.content.fluids.pump.PumpBlock
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler

/** Finite fixture stock, moved and consumed exclusively by the native machinery after placement. */
object ExpeditionFuelSupply {
    data class EngineSite(
        val offset: BlockPos,
        val thrust: Direction,
    )

    private data class Bank(
        val x: Int,
        val zSign: Int,
    ) {
        val outward get() = if (zSign > 0) Direction.SOUTH else Direction.NORTH
        val pump get() = BlockPos(x, 5, zSign * 7)
        val cog get() = BlockPos(x / 2 * 3, 6, zSign * 7)
        val power get() = BlockPos(x / 2 * 3, 6, zSign * 6)
    }

    private val banks = listOf(-2, 2).flatMap { x -> listOf(-1, 1).map { Bank(x, it) } }
    val tankOffsets = listOf(BlockPos(1, TANK_BASE_Y, 0))
    private val tankBlocks =
        buildSet {
            for (x in 1..2) for (z in 0..1) for (y in TANK_BASE_Y until TANK_BASE_Y + TANK_HEIGHT) add(BlockPos(x, y, z))
        }
    val pumpOffsets = banks.map { it.pump }
    val powerOffsets = banks.map { it.power }
    const val TANK_BASE_Y = 3
    const val TANK_HEIGHT = 6
    const val TANK_WIDTH = 2
    const val TANK_CAPACITY_MB = TANK_WIDTH * TANK_WIDTH * TANK_HEIGHT * 8_000
    const val INITIAL_FUEL_MB = TANK_CAPACITY_MB
    const val COAL_BLOCKS_PER_ENGINE = 8
    const val GENERATOR_RPM = 32
    const val PUMP_RPM = GENERATOR_RPM * 2

    val engines: List<EngineSite> =
        buildList {
            for (x in listOf(-4, 4)) {
                for (z in listOf(-12, -8, -6, -4, -2, 2, 4, 6, 8, 12)) add(EngineSite(BlockPos(x, 5, z), Direction.UP))
                for (z in listOf(-8, 8)) add(EngineSite(BlockPos(x, 9, z), Direction.DOWN))
            }
            for (x in listOf(-6, 6)) {
                for (y in listOf(4, 7)) {
                    for (z in listOf(-8, 8)) {
                        add(EngineSite(BlockPos(x, y, z), if (x < 0) Direction.EAST else Direction.WEST))
                    }
                }
            }
            for (x in listOf(-2, 2)) {
                for (y in listOf(4, 7)) {
                    for (z in listOf(-15, 15)) {
                        add(EngineSite(BlockPos(x, y, z), if (z < 0) Direction.SOUTH else Direction.NORTH))
                    }
                }
            }
        }

    private val pipes: Set<BlockPos> =
        buildSet {
            for (bank in banks) {
                val x = bank.x
                val sideX = x / 2 * 5
                val branchZ = bank.zSign * 8
                for (z in 8..14) add(BlockPos(x, 5, bank.zSign * z))
                for (y in 4..7) add(BlockPos(x, y, bank.zSign * 14))
                for (distance in 2..3) add(BlockPos(x / 2 * distance, 5, branchZ))
                for (distance in 3..5) add(BlockPos(x / 2 * distance, 6, branchZ))
                for (y in 4..7) add(BlockPos(sideX, y, branchZ))
                // The upward engine occupies y=5: its intake starts at y=6.
                // Reach this riser from the neighbouring x=5 manifold.
                for (y in 6..8) add(BlockPos(x / 2 * 4, y, branchZ))
                // Keep the lift manifold two blocks away from the tank columns: an
                // adjacent ordinary pipe would reconnect to the downstream reservoir.
                for (z in 2..12) add(BlockPos(x / 2 * 4, 7, bank.zSign * z))
                for (z in listOf(2, 4, 6, 12)) add(BlockPos(x / 2 * 4, 6, bank.zSign * z))
            }
            // Each pump has a separate suction path into one native shared reservoir.
            // Separate ports prevent equal-pressure pumps cancelling the cross-pod flow.
            for (sign in listOf(-1, 1)) {
                val nearTankZ = if (sign < 0) -1 else 2
                val zRange = if (sign < 0) -6..-1 else 2..6
                for (z in zRange) add(BlockPos(2, 5, z))
                for (y in 3..5) add(BlockPos(-2, y, sign * 6))
                for (z in zRange) add(BlockPos(-1, 3, z))
                add(BlockPos(-2, 3, sign * 6))
                for (x in -1..1) add(BlockPos(x, 3, nearTankZ))
            }
        }

    val occupiedOffsets: Set<BlockPos> =
        buildSet {
            addAll(pipes)
            addAll(tankBlocks)
            for (bank in banks) {
                add(bank.pump)
                add(bank.cog)
                add(bank.power)
            }
        }

    private fun block(id: String) = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState()

    /** Does not place or fill thrusters. Call once while creating the fixture, before normal ticking. */
    fun place(
        level: ServerLevel,
        origin: BlockPos,
    ) {
        val states = linkedMapOf<BlockPos, BlockState>()
        for (p in pipes) states[p] = block("create:fluid_pipe")
        for (p in tankBlocks) states[p] = block("create:fluid_tank")
        val engineBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:white_portable_engine"))
        for (bank in banks) {
            states[bank.pump] = block("create:mechanical_pump").setValue(PumpBlock.FACING, bank.outward)
            states[bank.cog] = block("create:large_cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Z)
            states[bank.power] = engineBlock.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, bank.outward)
        }
        for ((p, state) in states) check(level.setBlock(origin.offset(p), state, 3)) { "Could not place supply at $p" }
        // Recompute after all neighbours exist, using Create's own connection rules.
        for (p in pipes) {
            val world = origin.offset(p)
            val state = level.getBlockState(world)
            level.setBlock(world, (state.block as FluidPipeBlock).updateBlockState(state, Direction.NORTH, null, level, world), 3)
        }
        val fuel = BuiltInRegistries.FLUID.get(ResourceLocation.parse("createpropulsion:turpentine"))
        for (tankOffset in tankOffsets) {
            // Native placement forms the normal 2×2×6 tank. Fill through its native
            // capability once; runtime flow and stock depletion then belong to Create.
            ConnectivityHandler.formMulti(level.getBlockEntity(origin.offset(tankOffset)) as FluidTankBlockEntity)
            val tank = checkNotNull(level.getCapability(Capabilities.FluidHandler.BLOCK, origin.offset(tankOffset), null))
            check(tank.getTankCapacity(0) == TANK_CAPACITY_MB)
            check(tank.fill(FluidStack(fuel, TANK_CAPACITY_MB), IFluidHandler.FluidAction.EXECUTE) == TANK_CAPACITY_MB)
        }
        for (bank in banks) {
            val engine = level.getBlockEntity(origin.offset(bank.power)) as PortableEngineBlockEntity
            check(engine.inventory.insertSlot(ItemStack(Items.COAL_BLOCK, COAL_BLOCKS_PER_ENGINE), 0, false).isEmpty)
        }
    }
}
