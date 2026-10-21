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
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 500, batch = "fuel_snapshot")
    fun nativeBurnedLiquidFuelSnapshot(helper: GameTestHelper) {
        val rig = Rig(helper, 21_000.0)
        val engine = rig.level.getBlockEntity(rig.origin.above()) as ThrusterBlockEntity
        helper.runAfterDelay(30) {
            // Exhaust points up into clear air; the test's iron support lies below the intake.
            rig.level.setBlock(engine.blockPos, engine.blockState.setValue(AbstractThrusterBlock.FACING, Direction.DOWN), 3)
            engine.setControlMode(
                dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode.PERIPHERAL,
            )
            engine.setDigitalInput(0.137f)
        }
        helper.runAfterDelay(83) {
            org.cbi.orbitalis.minecraft.integration.propulsion.NativeThrusterInput
                .clear(engine)
            engine.setControlMode(dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode.NORMAL)
        }
        helper.runAfterDelay(183) {
            helper.assertTrue(
                engine.fuelAmountMb in 1..374 && engine.currentThrust == 0.0f,
                "Fixture did not burn real fractional fuel then fade: fuel=${engine.fuelAmountMb} thrust=${engine.currentThrust}",
            )
            val source = SableShipSnapshot.capture(rig.body)
            val before = engine.fuelAmountMb
            val sourceBlocks = source.getCompound("plot").getCompound("chunks")
            rig.container.removeForceLoadTicket(rig.body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            rig.container.removeSubLevel(rig.body, SubLevelRemovalReason.REMOVED)
            rig.body = SableShipSnapshot.load(rig.level, source)
            rig.container.addForceLoadTicket(rig.body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            val destinationEngine =
                rig.body.plot.loadedChunks
                    .flatMap {
                        it.chunk.blockEntities.values
                    }.filterIsInstance<ThrusterBlockEntity>()
                    .single()
            // Native initialization requests a fresh client tank animation sync. It cannot
            // invalidate a reconstruction whose actual resources and counters stay exact.
            destinationEngine
                .getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TYPE)
                .primaryTank.fluidLevel
                .forceNextSync()
            val restored = SableShipSnapshot.capture(rig.body).getCompound("plot").getCompound("chunks")
            for (key in sourceBlocks.allKeys) {
                SableShipSnapshot.verifyBlockEntities(
                    sourceBlocks.getCompound(key).getList("block_entities", 10),
                    restored.getCompound(key).getList("block_entities", 10),
                )
            }
            val restoredEngine =
                rig.body.plot.loadedChunks
                    .flatMap { it.chunk.blockEntities.values }
                    .filterIsInstance<ThrusterBlockEntity>()
                    .single()
            helper.assertTrue(restoredEngine.fuelAmountMb == before, "Native burn/restore changed real fuel amount")
            val enginePayload =
                sourceBlocks.allKeys
                    .flatMap {
                        sourceBlocks.getCompound(it).getList("block_entities", 10).map { tag ->
                            tag as net.minecraft.nbt.CompoundTag
                        }
                    }.single { it.getString("id") == "createpropulsion:thruster_block_entity" }
            helper.assertTrue(
                enginePayload.getDouble("FuelDrainAccumulator") > 0.0,
                "Fixture did not retain a fractional native fuel drain",
            )
            val expected =
                net.minecraft.nbt
                    .ListTag()
                    .also { it.add(enginePayload.copy()) }
            for (field in listOf("FuelTankSync", "FuelDrainAccumulator")) {
                val changed = enginePayload.copy()
                if (field == "FuelTankSync") {
                    changed.getCompound(field).putInt("amount", before + 1)
                } else {
                    changed.putDouble(field, changed.getDouble(field) + 0.25)
                }
                val actual =
                    net.minecraft.nbt
                        .ListTag()
                        .also { it.add(changed) }
                helper.assertTrue(
                    runCatching {
                        SableShipSnapshot.verifyBlockEntities(expected, actual)
                    }.isFailure,
                    "Exact resource comparison ignored $field",
                )
            }
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 600, batch = "coal_snapshot")
    fun nativeCoalPumpFuelSnapshot(helper: GameTestHelper) {
        val rig = Rig(helper, 24_000.0)

        fun keepAwake() {
            rig.level.resetEmptyTime()
            helper.runAfterDelay(1) { if (!rig.body.isRemoved) keepAwake() }
        }
        keepAwake()

        fun state(id: String) = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState()
        val tankPos = rig.origin.offset(0, 1, -3)
        val pumpPos = rig.origin.offset(0, 1, -2)
        val powerPos = rig.origin.offset(-1, 1, -3)
        val pipePos = rig.origin.offset(0, 1, -1)
        val enginePos = rig.origin.above()
        for (dx in -1..0) for (dz in -3..0) rig.level.setBlock(rig.origin.offset(dx, 0, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3)
        rig.level.setBlock(
            pumpPos,
            state("create:mechanical_pump").setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING, Direction.SOUTH),
            3,
        )
        rig.level.setBlock(
            rig.origin.offset(-1, 1, -2),
            state("create:cogwheel").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Z),
            3,
        )
        rig.level.setBlock(
            powerPos,
            state(
                "simulated:white_portable_engine",
            ).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
            3,
        )
        rig.level.setBlock(tankPos, state("create:fluid_tank"), 3)
        val tank = rig.level.getBlockEntity(tankPos) as FluidTankBlockEntity
        tank.tankInventory.fill(
            FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("createpropulsion:turpentine")), 2000),
            IFluidHandler.FluidAction.EXECUTE,
        )
        rig.level.setBlock(pipePos, state("create:fluid_pipe"), 3)
        rig.level.setBlock(enginePos, state("createpropulsion:thruster").setValue(AbstractThrusterBlock.FACING, Direction.NORTH), 3)
        rig.level.setBlock(
            rig.origin.offset(1, 1, 0),
            state("createpropulsion:creative_thruster").setValue(AbstractThrusterBlock.FACING, Direction.UP),
            3,
        )
        val pipeState = rig.level.getBlockState(pipePos)
        rig.level.setBlock(
            pipePos,
            (pipeState.block as com.simibubi.create.content.fluids.pipes.FluidPipeBlock).updateBlockState(
                pipeState,
                Direction.NORTH,
                null,
                rig.level,
                pipePos,
            ),
            3,
        )
        val power =
            rig.level.getBlockEntity(
                powerPos,
            ) as dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity
        check(
            power.inventory
                .insertSlot(
                    net.minecraft.world.item
                        .ItemStack(net.minecraft.world.item.Items.COAL_BLOCK, 8),
                    0,
                    false,
                ).isEmpty,
        )
        val pump = rig.level.getBlockEntity(pumpPos) as com.simibubi.create.content.fluids.pump.PumpBlockEntity
        val engine = rig.level.getBlockEntity(enginePos) as ThrusterBlockEntity
        rig.body.updateMergedMassData(1.0f)
        rig.body.updateBoundingBox()
        rig.body.forceUpdateGlobalBounds()
        rig.body.updateLastPose()
        helper.runAfterDelay(200) {
            helper.assertTrue(
                kotlin.math.abs(pump.speed) == 32f && power.currentBurnTime > 0 && power.inventory.getItem(0).count == 7,
                "Coal did not drive native pump: pump=${pump.speed} generator=${power.speed}/${power.generatedSpeed} burn=${power.currentBurnTime} coal=${power.inventory.getItem(
                    0,
                ).count}",
            )
            helper.assertTrue(
                engine.fuelAmountMb > 375 && tank.tankInventory.fluidAmount < 2000,
                "Native pump did not drain finite tank into engine",
            )
            engine.setControlMode(
                dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode.PERIPHERAL,
            )
            engine.setDigitalInput(0.137f)
        }
        helper.runAfterDelay(253) {
            org.cbi.orbitalis.minecraft.integration.propulsion.NativeThrusterInput
                .clear(engine)
            engine.setControlMode(dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode.NORMAL)
        }
        helper.runAfterDelay(353) {
            helper.assertTrue(tank.tankInventory.fluidAmount + engine.fuelAmountMb < 2375, "Native fuel stock was not consumed")
            val fuelBefore = engine.fuelAmountMb
            val tankBefore = tank.tankInventory.fluidAmount
            val burnBefore = power.currentBurnTime
            val source = SableShipSnapshot.capture(rig.body)
            val sourceChunks = source.getCompound("plot").getCompound("chunks")
            rig.container.removeForceLoadTicket(rig.body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            rig.container.removeSubLevel(rig.body, SubLevelRemovalReason.REMOVED)
            rig.body = SableShipSnapshot.load(rig.level, source)
            rig.container.addForceLoadTicket(rig.body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            val restoredPower =
                rig.level.getBlockEntity(
                    powerPos,
                ) as dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity
            helper.assertTrue(restoredPower.needsSpeedUpdate(), "Native constructor did not schedule kinetic reconnection")
            helper.assertTrue(
                restoredPower.currentBurnTime == burnBefore && restoredPower.inventory.getItem(0).count == 7,
                "Reconstruction changed actual coal inventory or burn clock",
            )
            helper.assertTrue(
                (rig.level.getBlockEntity(pumpPos) as com.simibubi.create.content.fluids.pump.PumpBlockEntity).speed == pump.speed,
                "Reconstruction changed pump rotation",
            )
            helper.assertTrue(
                (rig.level.getBlockEntity(enginePos) as ThrusterBlockEntity).fuelAmountMb == fuelBefore &&
                    (rig.level.getBlockEntity(tankPos) as FluidTankBlockEntity).tankInventory.fluidAmount == tankBefore,
                "Reconstruction changed physical fuel stock",
            )
            val restoredChunks = SableShipSnapshot.capture(rig.body).getCompound("plot").getCompound("chunks")
            for (key in sourceChunks.allKeys) {
                SableShipSnapshot.verifyBlockEntities(
                    sourceChunks.getCompound(key).getList("block_entities", 10),
                    restoredChunks.getCompound(key).getList("block_entities", 10),
                )
            }
            val payloads =
                sourceChunks.allKeys.flatMap {
                    sourceChunks.getCompound(it).getList("block_entities", 10).map { tag ->
                        tag as net.minecraft.nbt.CompoundTag
                    }
                }
            val portable = payloads.single { it.getString("id") == "simulated:portable_engine" }
            val pumpPayload = payloads.single { it.getString("id") == "create:mechanical_pump" }

            fun rejects(
                payload: net.minecraft.nbt.CompoundTag,
                field: String,
            ) {
                val changed = payload.copy()
                if (field == "Inventory" || field == "Network" ||
                    field == "Source"
                ) {
                    changed.remove(field)
                } else {
                    changed.putFloat(field, 1234f)
                }
                val expected =
                    net.minecraft.nbt
                        .ListTag()
                        .also { it.add(payload.copy()) }
                val actual =
                    net.minecraft.nbt
                        .ListTag()
                        .also { it.add(changed) }
                helper.assertTrue(
                    runCatching {
                        SableShipSnapshot.verifyBlockEntities(expected, actual)
                    }.isFailure,
                    "Adapter ignored actual kinetic/fuel field $field",
                )
            }
            for (field in listOf("BurnTime", "Inventory", "Speed", "Network")) {
                helper.assertTrue(portable.contains(field), "Native portable engine fixture lacks $field")
                rejects(portable, field)
            }
            helper.assertTrue(pumpPayload.contains("Source"), "Native pump fixture lacks kinetic source")
            rejects(pumpPayload, "Source")
            // The optional adapter owns the earlier creative one-shot connectivity policy too.
            val creative =
                payloads
                    .single {
                        it.getString("id") == "createpropulsion:creative_thruster_block_entity"
                    }.copy()
                    .also { it.remove("UpdateConnectivity") }
            val linked = creative.copy().also { it.putBoolean("UpdateConnectivity", true) }
            SableShipSnapshot.verifyBlockEntities(
                net.minecraft.nbt.ListTag().also {
                    it.add(creative)
                },
                net.minecraft.nbt
                    .ListTag()
                    .also { it.add(linked) },
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
