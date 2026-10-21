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

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.control.FlightBody
import org.cbi.orbitalis.minecraft.integration.propulsion.AutopilotSession
import org.cbi.orbitalis.minecraft.integration.propulsion.NativeThrusterInput
import kotlin.math.abs

/** Real finite tanks and native Propulsion ticks; no software fuel consumption or refill loop. */
@PrefixGameTestTemplate(false)
object FuelThrusterGameTests {
    private fun liquidRig(helper: GameTestHelper): Pair<PropulsionGameTests.Rig, List<ThrusterBlockEntity>> {
        val rig = PropulsionGameTests.Rig(helper, 2200.0)
        val block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:thruster"))
        val fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse("createpropulsion:turpentine"))
        val engines =
            rig.engines.map { previous ->
                val pos = previous.blockPos
                rig.body.plot.embeddedLevelAccessor.setBlock(
                    pos,
                    block.defaultBlockState().setValue(
                        dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock.FACING,
                        previous.facing,
                    ),
                    3,
                )
                (rig.body.level.getBlockEntity(pos) as ThrusterBlockEntity).also {
                    it.tank.primaryHandler.fill(FluidStack(fluid, 1000), IFluidHandler.FluidAction.EXECUTE)
                }
            }
        rig.body.updateMergedMassData(1.0f)
        return rig to engines
    }

    private fun normal(engine: ThrusterBlockEntity): Boolean =
        engine.saveWithFullMetadata(engine.level!!.registryAccess()).getInt("ControlMode") ==
            AbstractThrusterBlockEntity.ControlMode.NORMAL.ordinal

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 600, batch = "fuel_throttle")
    fun nativeLiquidProportionalFuelAndCutoff(helper: GameTestHelper) {
        val (_, engines) = liquidRig(helper)
        val first = engines.first { it.facing == Direction.UP }
        val second = engines.last { it.facing == Direction.UP }
        onFinished(helper) { engines.filterNot { it.isRemoved }.forEach { NativeThrusterInput.clear(it) } }
        helper.runAfterDelay(30) {
            for (engine in listOf(first, second)) engine.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL)
            NativeThrusterInput.set(first, 0.25f)
            NativeThrusterInput.set(second, 0.5f)
        }
        helper.runAfterDelay(190) {
            val low = 1000 - first.fuelAmountMb
            val high = 1000 - second.fuelAmountMb
            helper.assertTrue(low > 10 && high > low, "Native liquid tanks did not consume proportional fuel: $low/$high")
            helper.assertTrue(abs(high - 2 * low) <= 4, "Fractional native fuel consumption changed: $low/$high")
            helper.assertTrue(
                first.currentThrust > 0 && second.currentThrust > first.currentThrust,
                "Fractional native thrust was not produced",
            )
            NativeThrusterInput.clear(first)
            NativeThrusterInput.clear(second)
            first.setControlMode(AbstractThrusterBlockEntity.ControlMode.NORMAL)
            second.setControlMode(AbstractThrusterBlockEntity.ControlMode.NORMAL)
        }
        helper.runAfterDelay(310) {
            helper.assertTrue(first.currentThrust == 0.0f && second.currentThrust == 0.0f, "Liquid engines failed to fade after cutoff")
            val remaining = first.fuelAmountMb to second.fuelAmountMb
            helper.runAfterDelay(60) {
                helper.assertTrue(remaining == (first.fuelAmountMb to second.fuelAmountMb), "Stopped liquid engines consumed fuel")
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 300, batch = "fuel_dry")
    fun nativeLiquidDryPreflight(helper: GameTestHelper) {
        val (rig, engines) = liquidRig(helper)
        helper.runAfterDelay(30) {
            engines
                .first()
                .tank.primaryHandler
                .drain(1000, IFluidHandler.FluidAction.EXECUTE)
            val failure = runCatching { AutopilotSession(rig.body, FlightBody.position(rig.body)) }.exceptionOrNull()
            helper.assertTrue(
                failure is IllegalArgumentException && failure.message!!.contains("fuel"),
                "Dry liquid engine did not reject preflight: $failure",
            )
            helper.assertTrue(engines.all { normal(it) && it.power == 0.0f }, "Rejected fuel preflight acquired native controls")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 1000, batch = "fuel_depletion")
    fun nativeLiquidDepletionReleasesControl(helper: GameTestHelper) {
        val (rig, engines) = liquidRig(helper)
        var session: AutopilotSession? = null
        onFinished(helper) { session?.close() }
        helper.runAfterDelay(30) {
            session = AutopilotSession(rig.body, FlightBody.position(rig.body))
            val lift = engines.first { it.facing == Direction.UP }
            lift.tank.primaryHandler.drain(999, IFluidHandler.FluidAction.EXECUTE)
            var ticks = 0

            fun step() {
                val failure = runCatching { session!!.update() }.exceptionOrNull()
                if (failure != null) {
                    helper.assertTrue(
                        failure is IllegalArgumentException && failure.message!!.contains("fuel"),
                        "Unexpected liquid failure: $failure",
                    )
                    helper.assertTrue(lift.fuelAmountMb == 0, "Depletion failure occurred before native fuel consumption")
                    helper.assertTrue(engines.all { normal(it) && it.power == 0.0f }, "Depleted drive retained engine commands")
                    helper.runAfterDelay(120) {
                        helper.assertTrue(engines.all { it.currentThrust == 0.0f }, "Native liquid output survived depletion/cutoff")
                        helper.succeed()
                    }
                    return
                }
                helper.assertTrue(++ticks < 600, "Native lift engine never exhausted its finite tank")
                helper.runAfterDelay(1) { step() }
            }
            step()
        }
    }
}
