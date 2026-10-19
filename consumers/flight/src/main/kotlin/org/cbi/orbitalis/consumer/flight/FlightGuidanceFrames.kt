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

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity

/** Pad guidance uses rotating Earth coordinates; engine allocation and coast use inertial root. */
internal object FlightGuidanceFrames {
    fun rootState(body: ServerSubLevel): CartesianState {
        val native = SableFlightGravity.reading(body)
        return CelestialFlight
            .sample(
                body.level,
            ).rootState(CartesianState(native.position, AutopilotSession.vector(native.velocity)), body.level.dimension())
    }

    fun earthState(body: ServerSubLevel): CartesianState = CelestialFlight.sample(body.level).nativeState(rootState(body), Level.OVERWORLD)

    fun rootAcceleration(
        body: ServerSubLevel,
        earthAcceleration: Vector3,
    ): Vector3 {
        val sample = CelestialFlight.sample(body.level)
        val earth = sample.frames.profile.earthSurfaceFrame(sample.frames.tick)
        return earth.toParentAcceleration(earthState(body), earthAcceleration)
    }

    fun nativeAcceleration(
        body: ServerSubLevel,
        rootAcceleration: Vector3,
    ): Vector3 {
        val sample = CelestialFlight.sample(body.level)
        return sample.frames.frame(CelestialFlight.world(body.level.dimension())).fromParentAcceleration(rootState(body), rootAcceleration)
    }

    fun rootDirection(
        body: ServerSubLevel,
        earthDirection: Vector3,
    ): Vector3 = CelestialFlight.sample(body.level).rootDirection(earthDirection, Level.OVERWORLD)

    fun nativeDirection(
        body: ServerSubLevel,
        rootDirection: Vector3,
    ): Vector3 = CelestialFlight.sample(body.level).nativeDirection(rootDirection, body.level.dimension())
}
