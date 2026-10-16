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

import com.mojang.authlib.GameProfile
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.PlanetCell
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetNeighbours
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerMode
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours
import java.util.UUID

/** Native player placement and native shape updates, without authored pipe ports or foreign air chunks. */
@PrefixGameTestTemplate(false)
object CreatePlacementGameTests {
    private fun block(name: String) = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:$name")).defaultBlockState()

    private fun scene(helper: GameTestHelper): FaceFrameScene {
        val s = FaceFrameScene(checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)))
        s.planet.clear()
        s.prepare()
        PlanetNeighbours.reset(s.level)
        helper.onEachTick { s.level.resetEmptyTime() }
        onFinished(helper) { s.close() }
        return s
    }

    private fun native(
        s: FaceFrameScene,
        face: Direction,
        x: Int,
        z: Int,
    ) = SablePlanetNeighbours.nativePosition(s.planet.body(face), PlanetCell(x, 16, z))

    private fun place(
        helper: GameTestHelper,
        s: FaceFrameScene,
        face: Direction,
        pos: BlockPos,
        name: String,
    ) {
        val actor =
            object : Player(s.level, BlockPos.ZERO, 0f, GameProfile(UUID.randomUUID(), "create-place")) {
                override fun isSpectator() = false

                override fun isCreative() = true

                override fun isLocalPlayer() = true
            }
        try {
            (actor as CubePlayerMode).orbitalisSetCubeEnrolled(true)
            (actor as CubePlayerFrame).orbitalisSetUp(face)
            val world =
                s.planet
                    .body(face)
                    .logicalPose()
                    .transformPosition(Vec3.atCenterOf(pos))
            actor.setPos(world.add(Vec3.atLowerCornerOf(face.normal).scale(3.0)).add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
            CubePlayerPhysics.refresh(actor)
            actor.yRot = 0f
            actor.xRot = 0f
            val support = pos.south()
            s.level.setBlock(support, Blocks.BEDROCK.defaultBlockState(), 3)
            val item = block(name).block.asItem() as BlockItem
            val stack = ItemStack(item)
            actor.setItemInHand(InteractionHand.MAIN_HAND, stack)
            val hit = BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.0, -.5), Direction.NORTH, support, false)
            val context = BlockPlaceContext(actor, InteractionHand.MAIN_HAND, stack, hit)
            helper.assertTrue(context.clickedPos == pos, "Placement did not target the intended native cell")
            helper.assertTrue(item.place(context).consumesAction(), "Native BlockItem placement rejected $name")
        } finally {
            actor.discard()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "create_player_shaft", timeoutTicks = 150)
    fun nativePlayerShaftJoinsForeignMotor(helper: GameTestHelper) {
        val s = scene(helper)
        val motor = native(s, Direction.UP, 15, 4)
        val shaft = native(s, Direction.EAST, 16, 4)
        s.level.setBlock(motor, block("creative_motor").setValue(BlockStateProperties.FACING, Direction.EAST), 3)
        place(helper, s, Direction.EAST, shaft, "shaft")
        helper.assertTrue(
            s.level.getBlockState(shaft).getValue(BlockStateProperties.AXIS) == Direction.Axis.Y,
            "Native shaft placement did not follow the foreign motor axis",
        )
        helper.runAfterDelay(10) {
            val drive = s.level.getBlockEntity(motor) as CreativeMotorBlockEntity
            drive.generatedSpeed.setValue(32)
            drive.updateGeneratedRotation()
            helper.runAfterDelay(25) {
                val result = s.level.getBlockEntity(shaft) as KineticBlockEntity
                helper.assertTrue(
                    result.speed != 0f && result.network == drive.network,
                    "Player-placed shaft did not join the native motor network",
                )
                println("ORBITALIS_CREATE_PLAYER_SHAFT nativeBlockItem=true preferredAxis=true nativeRotation=true")
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "create_player_pipe", timeoutTicks = 200)
    fun nativePlayerPipesFormWorkingSeam(helper: GameTestHelper) {
        val s = scene(helper)
        val source = native(s, Direction.UP, 15, 6)
        val pump = native(s, Direction.UP, 15, 7)
        val first = native(s, Direction.UP, 15, 8)
        val second = native(s, Direction.EAST, 16, 8)
        val target = native(s, Direction.EAST, 17, 8)
        val motor = pump.west().north()
        s.level.setBlock(source, block("fluid_tank"), 3)
        s.level.setBlock(target, block("fluid_tank"), 3)
        s.level.setBlock(pump, block("mechanical_pump").setValue(BlockStateProperties.FACING, Direction.SOUTH), 3)
        s.level.setBlock(pump.west(), block("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3)
        s.level.setBlock(motor, block("creative_motor").setValue(BlockStateProperties.FACING, Direction.SOUTH), 3)
        place(helper, s, Direction.UP, first, "fluid_pipe")
        place(helper, s, Direction.EAST, second, "fluid_pipe")

        fun tank(p: BlockPos) = checkNotNull(s.level.getCapability(Capabilities.FluidHandler.BLOCK, p, null))
        helper.assertTrue(
            tank(source).fill(FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000,
            "Native source tank did not fill",
        )
        helper.runAfterDelay(10) {
            helper.assertTrue(
                FluidPipeBlock.isOpenAt(s.level.getBlockState(first), Direction.EAST) &&
                    FluidPipeBlock.isOpenAt(s.level.getBlockState(second), Direction.DOWN),
                "Native pipe placement/shape updates did not open both seam ports",
            )
            val drive = s.level.getBlockEntity(motor) as CreativeMotorBlockEntity
            drive.generatedSpeed.setValue(32)
            drive.updateGeneratedRotation()
            helper.runAfterDelay(100) {
                val a = tank(source).getFluidInTank(0).amount
                val b = tank(target).getFluidInTank(0).amount
                helper.assertTrue(b > 0 && a + b == 4000, "Player-placed native pipes did not transfer conserved fluid: $a/$b")
                println("ORBITALIS_CREATE_PLAYER_PIPE nativeBlockItem=true nativeShapes=true nativePump=true conserved=true")
                helper.succeed()
            }
        }
    }
}
