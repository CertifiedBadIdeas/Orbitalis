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

package org.cbi.orbitalis.core.orbit

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3

/** Engine-free evolution with bounded 20 Hz split impulses; supports static and time-dependent fields. */
object OrbitalEvolution {
    const val MAX_TICKS = 1200L
    private const val STEP = 0.05

    /**
     * Pure bounded work: callers segment longer intervals and own persistence/time bookkeeping.
     * The acceleration callback must be deterministic and depend only on position. Interruption
     * throws without returning a partially advanced state; this method never clears the interrupt.
     */
    fun advance(
        initial: CartesianState,
        ticks: Long,
        acceleration: (Vector3) -> Vector3,
    ): CartesianState = advanceTimed(initial, 0, ticks) { position, _ -> acceleration(position) }

    /**
     * Evolution in a moving field, sampled at absolute simulation ticks (20 Hz). [epochTick] is
     * independent of daylight/time commands. The deterministic callback must sample all sources
     * at the supplied tick; the final kick uses the new tick, never the start-of-step field.
     *
     * This inertial Cartesian integrator accepts position/time forces, not velocity-dependent frame
     * corrections such as Coriolis. Callers own ephemerides, frame transforms and interval segmentation.
     */
    fun advanceTimed(
        initial: CartesianState,
        epochTick: Long,
        ticks: Long,
        acceleration: (Vector3, Long) -> Vector3,
    ): CartesianState {
        require(ticks in 0..MAX_TICKS) { "Evolution requires 0..$MAX_TICKS ticks" }
        require(epochTick >= 0 && epochTick <= Long.MAX_VALUE - ticks) { "Evolution time is outside the supported range" }
        validate(initial)
        checkInterruption()
        if (ticks == 0L) return initial
        var state = initial
        var force = acceleration(state.position, epochTick).also(::validateAcceleration)
        repeat(ticks.toInt()) { index ->
            checkInterruption()
            val half = state.velocity + force * (STEP * 0.5)
            val position = state.position + half * STEP
            require(position.length() <= 1e12) { "Orbital position exceeds supported range" }
            val nextForce = acceleration(position, epochTick + index + 1L).also(::validateAcceleration)
            state = CartesianState(position, half + nextForce * (STEP * 0.5))
            validate(state)
            force = nextForce
        }
        checkInterruption()
        return state
    }

    private fun validate(state: CartesianState) {
        require(state.position.length() <= 1e12 && state.velocity.length() <= 1e9) { "Orbital state exceeds supported range" }
    }

    private fun validateAcceleration(value: Vector3) {
        require(value.length() <= 1e9) { "Acceleration exceeds supported range" }
    }

    private fun checkInterruption() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Orbital evolution cancelled")
    }
}
