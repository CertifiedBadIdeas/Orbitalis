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

package org.cbi.orbitalis.neoforge.integration.propulsion

import dev.propulsionteam.propulsionsimulated.PropulsionConfig
import dev.propulsionteam.propulsionsimulated.content.thruster.ThrusterFuelManager
import org.cbi.orbitalis.minecraft.integration.propulsion.LiquidThrusterSupport

/** Installed only with Propulsion; loader-specific native fuel/config types stay in NeoForge. */
object PropulsionLiquidSupport {
    fun install() {
        LiquidThrusterSupport.sampleCapacity = { engine ->
            require(engine.validFluid() && engine.fuelAmountMb > 0) { "Liquid engine has no usable fuel; refuel before engaging control" }
            val fuel = engine.fluidStack().fluid
            val properties = requireNotNull(engine.getFuelProperties(fuel)) { "Liquid engine fuel is unsupported" }
            engine.calculateObstruction(engine.level, engine.blockPos, engine.facing)
            val clearance = engine.unobstructedBlocks.toDouble() / PropulsionConfig.OBSTRUCTION_SCAN_LENGTH.get()
            require(clearance > 0.0) { "Liquid engine exhaust is obstructed" }
            // Single non-ion engines have at least their base atmospheric factor. Native
            // obstruction limits power rather than multiplying it: this lower bound reserves
            // authority conservatively without replacing native forces or fuel integration.
            PropulsionConfig.BASE_THRUST.get() * properties.thrustMultiplier() *
                ThrusterFuelManager.getEfficiency(fuel) * clearance.coerceAtMost(1.0)
        }
    }
}
