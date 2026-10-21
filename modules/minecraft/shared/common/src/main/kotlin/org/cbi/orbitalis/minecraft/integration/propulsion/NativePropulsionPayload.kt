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

package org.cbi.orbitalis.minecraft.integration.propulsion

import net.minecraft.nbt.CompoundTag

/** Pinned optional-engine reconstruction policies; never mutates live state or saved payloads. */
object NativePropulsionPayload {
    fun forComparison(payload: CompoundTag): CompoundTag {
        val type = payload.getString("id")
        if (type == "simulated:portable_engine") {
            // Create 6.0.10 initializes updateSpeed=true in the kinetic constructor and
            // deliberately does not restore it in read(). The first native attachKinetics
            // clears this work request. Coal, burn time, direction, speed and network stay exact.
            check(
                !payload.contains("NeedsSpeedUpdate") ||
                    (payload.contains("NeedsSpeedUpdate", 1) && payload.getByte("NeedsSpeedUpdate") == 1.toByte()),
            ) { "Invalid portable engine kinetic rebuild flag" }
            return payload.copy().also { it.remove("NeedsSpeedUpdate") }
        }
        if (type == "createpropulsion:thruster_block_entity") {
            return payload.copy().also { copy ->
                for (entry in copy.getList("Tanks", 10)) {
                    val level = (entry as CompoundTag).getCompound("Level")
                    // Create 6.0.10 LerpedFloat.writeNBT consumes this one-shot client sync
                    // request; native tank initialization may re-arm it after reconstruction.
                    // Animated values, actual TankContent, FuelTankSync and every fractional
                    // fuel/oxidizer drain accumulator remain part of the exact comparison.
                    check(!level.contains("Force") || (level.contains("Force", 1) && level.getByte("Force") == 1.toByte())) {
                        "Invalid liquid tank animation synchronization flag"
                    }
                    level.remove("Force")
                }
            }
        }
        if (type != "createpropulsion:creative_thruster_block_entity") return payload
        // Propulsion 1.1.5 read() re-arms this connectivity work flag for single engines.
        // Keep it armed on the live destination: its first resumed tick must rebuild links.
        // It is not cargo or engine configuration; all other fields remain exact.
        check(
            !payload.contains("UpdateConnectivity") ||
                (payload.contains("UpdateConnectivity", 1) && payload.getByte("UpdateConnectivity") == 1.toByte()),
        ) { "Invalid creative thruster connectivity work flag" }
        return payload.copy().also { it.remove("UpdateConnectivity") }
    }
}
