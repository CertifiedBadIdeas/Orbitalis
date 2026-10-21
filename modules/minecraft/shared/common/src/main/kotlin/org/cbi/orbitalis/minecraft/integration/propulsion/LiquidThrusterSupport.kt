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

import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity

/** Loader adapter for Propulsion's NeoForge fluid/config types; never consumes or creates fuel. */
object LiquidThrusterSupport {
    var sampleCapacity: ((ThrusterBlockEntity) -> Double)? = null

    fun capacity(engine: ThrusterBlockEntity): Double {
        val capacity = checkNotNull(sampleCapacity) { "Native liquid engine support is not installed" }(engine)
        require(capacity.isFinite() && capacity > 0.0) { "Liquid engine has no usable thrust capacity" }
        return capacity
    }
}
