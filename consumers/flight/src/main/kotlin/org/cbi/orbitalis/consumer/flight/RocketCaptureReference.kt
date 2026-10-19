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

import org.cbi.orbitalis.core.orbit.CelestialDynamics
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction

/** A coast reference belongs to its actual insertion epoch, not an earlier orbit's arbitrary phase. */
internal class RocketCaptureReference(
    private val model: CelestialDynamics,
    initial: CartesianState,
) {
    private var tick = model.epochTick
    private var state = initial

    fun at(currentTick: Long): CartesianState {
        require(currentTick >= tick)
        if (currentTick == tick) return state
        val seconds = (currentTick - tick) / 20.0
        val result =
            CoastPrediction.predict(
                model.earth,
                state,
                CoastPrediction.Limits(horizon = seconds, range = model.profile.flightRange),
                model,
                (tick - model.epochTick) / 20.0,
            )
        check(result.end == CoastPrediction.End.HORIZON) { "Insertion reference left its safe coast: ${result.end}" }
        state = result.terminal.state
        tick = currentTick
        return state
    }
}
