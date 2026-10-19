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

import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.creative_vector_thruster.CreativeVectorThrusterBlockEntity
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.control.FlightEngineBackend
import org.joml.Quaterniond

/** Loaded by the loader only when the supported Propulsion and Sable mods are present. */
object PropulsionFlightBackend : FlightEngineBackend {
    override fun isRocket(body: ServerSubLevel): Boolean =
        body.plot.loadedChunks.any { chunk ->
            chunk.chunk.blockEntities.values
                .any { it is CreativeVectorThrusterBlockEntity }
        }

    override fun enginesFaded(body: ServerSubLevel) = AutopilotSession.enginesFaded(body)

    override fun hold(
        body: ServerSubLevel,
        target: Vector3,
        desiredAttitude: Quaterniond,
        mission: Boolean,
        earthAttitude: Boolean,
    ) = AutopilotSession(body, target, desiredAttitude, mission, earthAttitude)

    override fun rocket(
        body: ServerSubLevel,
        requireUpright: Boolean,
    ) = RocketDrive(body, requireUpright)
}
