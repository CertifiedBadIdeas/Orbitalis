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

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Disposable long industrial vessel. Its complete 13 x 31 x 10 envelope fits transfer bounds. */
object ExpeditionBlueprint {
    val minimum = BlockPos(-6, 0, -15)
    val maximum = BlockPos(6, 9, 15)
    val assembler = BlockPos(0, 5, 0)
    val boarding = BlockPos(0, 5, 12)
    val cargoOffsets = listOf(BlockPos(-1, 5, -3), BlockPos(1, 5, -3), BlockPos(-1, 5, 3), BlockPos(1, 5, 3))

    private val engines = ExpeditionFuelSupply.engines.associateBy { it.offset }
    private val reserved = ExpeditionFuelSupply.occupiedOffsets + engines.keys

    /** Caller owns site clearing/build-height checks. No player movement, recurring tick or refills. */
    fun place(
        level: ServerLevel,
        origin: BlockPos,
    ) {
        val hull = linkedMapOf<BlockPos, BlockState>()

        fun panel(
            x: Int,
            y: Int,
            z: Int,
            state: BlockState = Blocks.WHITE_CONCRETE.defaultBlockState(),
        ) {
            val p = BlockPos(x, y, z)
            check(x in -6..6 && y in 0..9 && z in -15..15)
            // Lift nozzles retain an unobstructed vertical column down to the four feet.
            if (p in reserved || (kotlin.math.abs(x) == 4 && kotlin.math.abs(z) == 8 && y < 5)) return
            hull[p] = state
        }
        val dark = Blocks.GRAY_CONCRETE.defaultBlockState()
        val trim = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
        val glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()
        // Hollow main pressure hull: walking deck, low roof, narrow bow and open aft hatch.
        for (z in -11..11) {
            for (x in -3..3) {
                panel(x, 4, z, trim)
                panel(x, 8, z)
            }
            for (y in 5..7) {
                for (x in listOf(-3, 3)) {
                    panel(
                        x,
                        y,
                        z,
                        if (z in -11..-7 && y >= 6) {
                            glass
                        } else if (y == 5) {
                            dark
                        } else {
                            Blocks.WHITE_CONCRETE.defaultBlockState()
                        },
                    )
                }
            }
        }
        // Stepped glazed bow reads as an angular cockpit from the exterior and the cabin.
        for (z in -14..-12) {
            val width =
                if (z == -14) {
                    0
                } else if (z == -13) {
                    1
                } else {
                    2
                }
            for (x in -width..width) {
                panel(x, 4, z, trim)
                panel(x, 8, z)
                for (y in 5..7) if (z == -14 || kotlin.math.abs(x) == width) panel(x, y, z, glass)
            }
        }
        // Aft bulkhead leaves a two-block-high entrance and a short loading step.
        for (x in -2..2) for (y in 5..7) if (x != 0 || y == 7) panel(x, y, 12, trim)
        // Five native stair runs reach the deck from ground level inside the aft envelope.
        val boardingStairs =
            Blocks.POLISHED_ANDESITE_STAIRS
                .defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
        for (z in 11..15) for (x in -1..1) panel(x, 15 - z, z, boardingStairs)
        // Long segmented side pods surround, but never replace, the native fuel machinery.
        for (sign in listOf(-1, 1)) {
            for (z in -11..11) {
                for (distance in 4..5) {
                    panel(sign * distance, 4, z, dark)
                    panel(sign * distance, 8, z, if (z % 4 == 0) dark else trim)
                }
                for (y in 5..7) panel(sign * 5, y, z, if (z % 4 == 0) dark else Blocks.WHITE_CONCRETE.defaultBlockState())
            }
            // Four separated landing feet support the hull without blocking lift exhaust.
            for (z in listOf(-10, 10)) {
                for (y in 1..3) panel(sign * 5, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState())
                panel(sign * 5, 0, z, Blocks.IRON_BLOCK.defaultBlockState())
                panel(sign * 4, 0, z, Blocks.IRON_BLOCK.defaultBlockState())
            }
        }
        for ((offset, state) in hull) level.setBlock(origin.offset(offset), state, 3)
        // Separated standard liquid engines have native proportional throttle and finite tanks.
        val thruster = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:thruster"))
        for ((offset, engine) in engines) {
            level.setBlock(origin.offset(offset), thruster.defaultBlockState().setValue(AbstractThrusterBlock.FACING, engine.thrust), 3)
        }
        ExpeditionFuelSupply.place(level, origin)
        for (offset in cargoOffsets) {
            level.setBlock(origin.offset(offset), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3)
            (level.getBlockEntity(origin.offset(offset)) as ChestBlockEntity).setItem(0, ItemStack(Items.COBBLESTONE, 16))
        }

        fun block(id: String) = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState()
        level.setBlock(
            origin.offset(assembler),
            block("simulated:physics_assembler")
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH),
            3,
        )
        // The assembled volume excludes the launch pad below y=0 and includes every supply part.
        val bounds = AABB(Vec3.atLowerCornerOf(origin.offset(minimum)), Vec3.atLowerCornerOf(origin.offset(maximum.offset(1, 1, 1))))
        check(level.addFreshEntity(SuperGlueEntity(level, bounds))) { "Could not create expedition assembly glue" }
    }
}
