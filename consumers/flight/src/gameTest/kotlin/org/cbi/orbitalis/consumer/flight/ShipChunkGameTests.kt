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

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.control.FlightBody
import org.cbi.orbitalis.minecraft.integration.propulsion.AutopilotSession
import org.cbi.orbitalis.minecraft.integration.propulsion.RocketDrive
import org.cbi.orbitalis.minecraft.integration.sable.SableShipChunks
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot

/** The four-chunk limit applies to persisted content, including state outside solid sections. */
@PrefixGameTestTemplate(false)
object ShipChunkGameTests {
    private class Rig(
        helper: GameTestHelper,
        x: Double,
    ) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        var body = container.allocateNewSubLevel(Pose3d().also { it.position().set(x, 10_000.0, 0.0) }) as ServerSubLevel
        val centre = body.plot.centerChunk
        val origin = BlockPos((centre.x shl 4) + 15, 80, (centre.z shl 4) + 15)

        init {
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            onFinished(helper) {
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                if (!body.isRemoved) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
            for (cx in 0..1) for (cz in 0..1) body.plot.newEmptyChunk(ChunkPos(centre.x + cx, centre.z + cz))
            for (dx in 0..1) for (dz in 0..1) level.setBlock(origin.offset(dx, 0, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3)
            val thruster = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:thruster"))
            level.setBlock(origin.above(), thruster.defaultBlockState().setValue(AbstractThrusterBlock.FACING, Direction.UP), 3)
            val tank = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:fluid_tank"))
            level.setBlock(origin.offset(1, 1, 1), tank.defaultBlockState(), 3)
            val fuel = BuiltInRegistries.FLUID.get(ResourceLocation.parse("createpropulsion:turpentine"))
            (
                level.getBlockEntity(
                    origin.above(),
                ) as ThrusterBlockEntity
            ).tank.primaryHandler.fill(FluidStack(fuel, 375), IFluidHandler.FluidAction.EXECUTE)
            (
                level.getBlockEntity(
                    origin.offset(1, 1, 1),
                ) as FluidTankBlockEntity
            ).tankInventory.fill(FluidStack(fuel, 2000), IFluidHandler.FluidAction.EXECUTE)
            body.plot.newEmptyChunk(ChunkPos(centre.x, centre.z - 1))
            body.plot.newEmptyChunk(ChunkPos(centre.x, centre.z + 2))
            body.updateMergedMassData(1.0f)
            body.updateBoundingBox()
            body.forceUpdateGlobalBounds()
            body.updateLastPose()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 500, batch = "chunk_snapshot")
    fun nativeReadChunksPreserveFuelSnapshot(helper: GameTestHelper) {
        val rig = Rig(helper, 22_000.0)
        helper.runAfterDelay(30) {
            helper.assertTrue(
                rig.body.plot.loadedChunks.size >= 6 && SableShipChunks.occupied(rig.body).size == 4,
                "Read-created chunks were confused with occupied chunks",
            )
            val source = SableShipSnapshot.capture(rig.body)
            helper.assertTrue(source.getCompound("plot").getCompound("chunks").size() == 4, "Snapshot retained empty read chunks")
            val sourceBlocks = source.getCompound("plot").getCompound("chunks")
            rig.container.removeForceLoadTicket(rig.body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            rig.container.removeSubLevel(rig.body, SubLevelRemovalReason.REMOVED)
            rig.body = SableShipSnapshot.load(rig.level, source)
            rig.container.addForceLoadTicket(rig.body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            val restored = SableShipSnapshot.capture(rig.body).getCompound("plot").getCompound("chunks")
            helper.assertTrue(restored.allKeys == sourceBlocks.allKeys, "Occupied chunk addresses changed during reconstruction")
            for (key in sourceBlocks.allKeys) {
                SableShipSnapshot.verifyBlockEntities(
                    sourceBlocks.getCompound(key).getList("block_entities", 10),
                    restored.getCompound(key).getList("block_entities", 10),
                )
            }
            val entities =
                rig.body.plot.loadedChunks
                    .flatMap { it.chunk.blockEntities.values }
            helper.assertTrue(
                entities.filterIsInstance<ThrusterBlockEntity>().single().fuelAmountMb == 375,
                "Liquid engine fuel changed during reconstruction",
            )
            helper.assertTrue(
                entities
                    .filterIsInstance<FluidTankBlockEntity>()
                    .single()
                    .tankInventory.fluidAmount == 2000,
                "Finite tank changed during reconstruction",
            )
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 500, batch = "chunk_rejection")
    fun nativeFiveOccupiedChunksRejected(helper: GameTestHelper) {
        val rig = Rig(helper, 23_000.0)
        helper.runAfterDelay(30) {
            rig.body.plot.newEmptyChunk(ChunkPos(rig.centre.x + 2, rig.centre.z))
            // Connect the fifth occupied chunk to the same hull; no detached fixture bodies.
            for (dx in 1..17) rig.level.setBlock(rig.origin.offset(dx, 0, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3)
            rig.body.updateMergedMassData(1.0f)
            helper.assertTrue(SableShipChunks.occupied(rig.body).size == 5, "Fifth occupied chunk was not counted")
            val snapshotFailure = runCatching { SableShipSnapshot.capture(rig.body) }.exceptionOrNull()
            val holdFailure = runCatching { AutopilotSession(rig.body, FlightBody.position(rig.body)) }.exceptionOrNull()
            val rocketFailure = runCatching { RocketDrive(rig.body) }.exceptionOrNull()
            for (failure in listOf(snapshotFailure, holdFailure, rocketFailure)) {
                helper.assertTrue(
                    failure is IllegalArgumentException && failure.message!!.contains("four"),
                    "Five occupied chunks bypassed a boundary: $failure",
                )
            }
            helper.succeed()
        }
    }
}
