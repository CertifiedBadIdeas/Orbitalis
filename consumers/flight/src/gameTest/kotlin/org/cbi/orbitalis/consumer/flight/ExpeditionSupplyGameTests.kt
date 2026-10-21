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
import com.simibubi.create.content.fluids.pump.PumpBlockEntity
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object ExpeditionSupplyGameTests {
    private class Rig(
        helper: GameTestHelper,
        x: Double,
    ) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        private val container = checkNotNull(SubLevelContainer.getContainer(level))
        val body = container.allocateNewSubLevel(Pose3d().also { it.position().set(x, 10_000.0, 0.0) }) as ServerSubLevel
        val origin: BlockPos = body.plot.centerBlock.offset(0, 64, 0)
        val engines get() = ExpeditionFuelSupply.engines.map { level.getBlockEntity(origin.offset(it.offset)) as ThrusterBlockEntity }
        val powers get() = ExpeditionFuelSupply.powerOffsets.map { level.getBlockEntity(origin.offset(it)) as PortableEngineBlockEntity }
        val pumps get() = ExpeditionFuelSupply.pumpOffsets.map { level.getBlockEntity(origin.offset(it)) as PumpBlockEntity }
        val tanks get() = ExpeditionFuelSupply.tankOffsets.map { level.getBlockEntity(origin.offset(it)) as FluidTankBlockEntity }

        init {
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            onFinished(helper) {
                VacuumTestBlueprint.discardGlue(body)
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                if (!body.isRemoved) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
            for (cx in ((origin.x - 6) shr 4)..((origin.x + 6) shr 4)) {
                for (cz in ((origin.z - 15) shr 4)..((origin.z + 15) shr 4)) body.plot.newEmptyChunk(ChunkPos(cx, cz))
            }
            // Native connectivity splits unbound components into separate constructions.
            // Join the four independent fluid banks with an ordinary structural deck.
            for (x in -6..6) {
                for (z in -15..15) {
                    if (kotlin.math.abs(x) in 3..5 && kotlin.math.abs(z) in 7..9) continue
                    level.setBlock(origin.offset(x, 0, z), Blocks.WHITE_CONCRETE.defaultBlockState(), 3)
                }
            }
            for (tank in ExpeditionFuelSupply.tankOffsets) {
                for (y in 1..4) {
                    level.setBlock(origin.offset(tank.x, y, tank.z), Blocks.WHITE_CONCRETE.defaultBlockState(), 3)
                }
            }
            level.addFreshEntity(SuperGlueEntity(level, SuperGlueEntity.span(origin.offset(-6, 0, -15), origin.offset(6, 9, 15))))
            val thruster = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:thruster"))
            for (site in ExpeditionFuelSupply.engines) {
                level.setBlock(
                    origin.offset(site.offset),
                    thruster.defaultBlockState().setValue(AbstractThrusterBlock.FACING, site.thrust),
                    3,
                )
            }
            ExpeditionFuelSupply.place(level, origin)
            helper.assertTrue(engines.all { it.fuelAmountMb == 0 }, "Thrusters must start empty and receive fuel through native pipes")
            body.updateMergedMassData(1.0f)
            body.updateBoundingBox()
            body.forceUpdateGlobalBounds()
            body.updateLastPose()

            // A native plot loading ticket does not prevent Minecraft idling an empty
            // dimension. Keep this uncrewed fixture's world ticking like the flight tests.
            fun keepAwake() {
                level.resetEmptyTime()
                helper.runAfterDelay(1) { if (!body.isRemoved) keepAwake() }
            }
            keepAwake()
        }

        fun fuelRemaining() = tanks.sumOf { it.tankInventory.fluidAmount } + engines.sumOf { it.fuelAmountMb }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 1000, batch = "expedition_supply")
    fun nativeExpeditionFuelSupply(helper: GameTestHelper) {
        val rig = Rig(helper, 24_000.0)
        helper.runAfterDelay(600) {
            helper.assertTrue(rig.pumps.all { abs(it.speed) == 32f }, "Coal engines did not drive all four native pumps at 32 RPM")
            helper.assertTrue(
                rig.powers.all { it.inventory.getItem(0).count == 7 && it.currentBurnTime > 0 },
                "Native coal block was not consumed",
            )
            helper.assertTrue(
                rig.engines.all { it.fuelAmountMb > 0 && !it.isMultiblock },
                "Pipe network failed to fuel all 24 independent engines",
            )
            helper.assertTrue(rig.fuelRemaining() == ExpeditionFuelSupply.INITIAL_FUEL_MB, "Native routing created or lost fuel")
            val burnTimes = rig.powers.map { it.currentBurnTime }
            val fuelBefore = rig.fuelRemaining()
            rig.engines.forEach {
                it.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL)
                it.setDigitalInput(0.25f)
            }
            helper.runAfterDelay(80) {
                helper.assertTrue(
                    rig.powers.zip(burnTimes).all { (power, before) ->
                        power.currentBurnTime < before
                    },
                    "Coal burn clock did not advance: before=$burnTimes after=${rig.powers.map {
                        it.currentBurnTime
                    }} fuelBefore=$fuelBefore fuelAfter=${rig.fuelRemaining()} thrust=${rig.engines.map { it.currentThrust }}",
                )
                helper.assertTrue(rig.fuelRemaining() < fuelBefore, "Fractional native throttle consumed no finite onboard fuel")
                helper.assertTrue(rig.engines.all { it.currentThrust > 0f }, "Fuel-fed native engines did not produce thrust")
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 400, batch = "expedition_supply")
    fun nativeExpeditionEmptySupply(helper: GameTestHelper) {
        val rig = Rig(helper, 25_000.0)
        for (p in ExpeditionFuelSupply.occupiedOffsets) {
            val be = rig.level.getBlockEntity(rig.origin.offset(p))
            if (be is FluidTankBlockEntity) be.tankInventory.drain(Int.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE)
        }
        rig.powers.forEach { it.clearContent() }
        rig.engines.forEach {
            it.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL)
            it.setDigitalInput(1f)
        }
        helper.runAfterDelay(240) {
            helper.assertTrue(rig.fuelRemaining() == 0, "Empty finite supply generated fuel")
            helper.assertTrue(rig.pumps.all { it.speed == 0f }, "Empty coal supply generated rotation")
            helper.assertTrue(rig.engines.all { it.currentThrust == 0f }, "Empty engines generated thrust")
            helper.succeed()
        }
    }
}
