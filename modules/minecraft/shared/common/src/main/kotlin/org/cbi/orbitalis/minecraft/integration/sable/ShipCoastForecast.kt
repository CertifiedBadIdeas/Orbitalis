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

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.resources.ResourceLocation
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.navigation.TrajectorySnapshot
import java.util.UUID

/** Immutable copy of live server state. Contains no body, level or physics handle on the worker. */
class ShipCoastForecast private constructor(
    private val ship: UUID,
    private val dimension: ResourceLocation,
    private val sampledTick: Long,
    private val state: CartesianState,
    private val field: SurfaceGravityLayer,
    private val limits: CoastPrediction.Limits,
) {
    fun calculate(): TrajectorySnapshot = TrajectorySnapshot(ship, dimension, sampledTick, CoastPrediction.predict(field, state, limits))

    companion object {
        /** Call only on the server thread, after native mass initialization. */
        fun capture(body: ServerSubLevel): ShipCoastForecast? {
            if (body.isRemoved || !FlatPlanetSpace.contains(body.level) || body.massTracker.centerOfMass == null) return null
            val sample = SableFlightGravity.reading(body)
            val global = SableFlightGravity.globalPosition(body.level, sample.position)
            if (global.length() > 30000.0 || sample.velocity.length() > 9990.0) return null
            return ShipCoastForecast(
                body.uniqueId,
                body.level.dimension().location(),
                body.level.gameTime,
                CartesianState(global, Vector3(sample.velocity.x, sample.velocity.y, sample.velocity.z)),
                SableFlightGravity.flatField(body.level),
                CoastPrediction.Limits(
                    entryHeight = FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT,
                    entryArmHeight = FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT,
                    entryInitiallyArmed = body.level.dimension() == FlatPlanetSpace.SPACE,
                    footprintHalfSize = FlatPlanetSpace.HALF_SIZE,
                ),
            )
        }
    }
}
