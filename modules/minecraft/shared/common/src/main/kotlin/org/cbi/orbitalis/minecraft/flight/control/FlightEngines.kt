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

package org.cbi.orbitalis.minecraft.flight.control

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Quaterniond

interface HoldFlightDrive : AutoCloseable {
    val body: ServerSubLevel

    fun update(netAcceleration: Vector3? = null)

    fun status(): String
}

interface RocketFlightDrive : AutoCloseable {
    val maximumAcceleration: Double

    fun update(
        netAcceleration: Vector3,
        desiredNose: Vector3? = null,
        trackDirection: Boolean = false,
    )

    fun turn(direction: Vector3)

    fun alignment(direction: Vector3): Double

    fun status(): String
}

/** Engine adapters own native inputs; guidance never applies substitute forces. */
interface FlightEngineBackend {
    fun isRocket(body: ServerSubLevel): Boolean

    fun enginesFaded(body: ServerSubLevel): Boolean

    fun hold(
        body: ServerSubLevel,
        target: Vector3,
        desiredAttitude: Quaterniond = Quaterniond(body.logicalPose().orientation()),
        mission: Boolean = false,
        earthAttitude: Boolean = false,
    ): HoldFlightDrive

    fun rocket(
        body: ServerSubLevel,
        requireUpright: Boolean = false,
    ): RocketFlightDrive
}

object FlightEngines {
    var backend: FlightEngineBackend? = null

    fun requireBackend(): FlightEngineBackend = checkNotNull(backend) { "No supported engine integration is installed" }
}
