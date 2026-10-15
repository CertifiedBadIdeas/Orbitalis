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

package org.cbi.orbitalis.minecraft.integration.sable

import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.TwoBodyGravity
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** Diagnostic reference and measured errors only; never writes the authoritative Sable body state. */
class SableOrbitExperiment {
    data class Metrics(
        val seconds: Double,
        val maximumPositionError: Double,
        val maximumVelocityError: Double,
        val maximumRadiusError: Double,
        val maximumRelativeEnergyError: Double,
        val maximumRelativeMomentumError: Double,
    )

    val gravity = TwoBodyGravity(MU)
    private var reference = emptyList<CartesianState>()
    private var initialEnergy = emptyList<Double>()
    private var initialMomentum = emptyList<Vector3>()
    var metrics = Metrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        private set
    val initialized get() = reference.isNotEmpty()

    fun initialize(states: List<CartesianState>) {
        check(!initialized && states.size == 2)
        reference = states
        initialEnergy = states.map(gravity::specificEnergy)
        initialMomentum = states.map { it.position.cross(it.velocity) }
    }

    fun sample(
        states: List<CartesianState>,
        seconds: Double,
    ) {
        check(initialized && states.size == reference.size)
        // A finer, independent Verlet reference measures the native solver, without correcting it.
        reference =
            reference.map { initial ->
                var state = initial
                repeat(8) { state = gravity.step(state, seconds / 8.0) }
                state
            }
        var next = metrics.copy(seconds = metrics.seconds + seconds)
        states.forEachIndexed { index, state ->
            next =
                Metrics(
                    next.seconds,
                    max(next.maximumPositionError, (state.position - reference[index].position).length()),
                    max(next.maximumVelocityError, (state.velocity - reference[index].velocity).length()),
                    max(next.maximumRadiusError, abs(state.position.length() - RADIUS)),
                    max(
                        next.maximumRelativeEnergyError,
                        abs((gravity.specificEnergy(state) - initialEnergy[index]) / initialEnergy[index]),
                    ),
                    max(
                        next.maximumRelativeMomentumError,
                        (state.position.cross(state.velocity) - initialMomentum[index]).length() / initialMomentum[index].length(),
                    ),
                )
        }
        metrics = next
    }

    companion object {
        const val MU = 1024.0
        const val RADIUS = 16.0
        const val SPEED = 8.0
        const val PERIOD = 4.0 * PI
        val CENTRE = Vector3(0.0, 96.0, 120.0)
    }
}
