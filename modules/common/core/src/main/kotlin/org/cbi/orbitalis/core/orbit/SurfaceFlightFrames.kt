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
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.PlanetFlightEnvelope
import org.cbi.orbitalis.core.planet.Vector3

/** One sampled catalogue/epoch for positions, derivatives and rigid orientation at a native handoff. */
class SurfaceFlightFrames(
    val profile: SolarSystemProfile,
    val tick: Long,
    val seconds: Double = 0.0,
) {
    enum class World { EARTH, SPACE, MOON }

    fun frame(world: World): FrameKinematics =
        when (world) {
            World.EARTH -> profile.earthSurfaceFrame(tick, seconds)
            World.SPACE -> FrameKinematics(CartesianState(FlatPlanetFrame.origin, Vector3.ZERO))
            World.MOON -> profile.lunarSurfaceFrame(tick, seconds)
        }

    fun state(
        value: CartesianState,
        from: World,
        to: World,
    ): CartesianState = frame(to).fromParent(frame(from).toParent(value))

    fun point(
        value: Vector3,
        from: World,
        to: World,
    ): Vector3 = state(CartesianState(value, Vector3.ZERO), from, to).position

    fun direction(
        value: Vector3,
        from: World,
        to: World,
    ): Vector3 = frame(to).orientation.inverse(frame(from).orientation.apply(value))

    fun angularVelocity(
        value: Vector3,
        from: World,
        to: World,
    ): Vector3 = frame(to).fromParentAngularVelocity(frame(from).toParentAngularVelocity(value))

    /** All-face volume handoffs with separate entry/exit bands. No nearest-body teleport selection. */
    fun destination(
        value: CartesianState,
        from: World,
    ): World? {
        if (from == World.MOON) {
            val envelope = profile.geometry.moonEnvelope
            return if (envelope.distance(value.position) >= envelope.ascentDistance) World.SPACE else null
        }
        if (from == World.EARTH) {
            return if (profile.geometry.earthEnvelope.distance(value.position) >=
                profile.geometry.earthEnvelope.ascentDistance
            ) {
                World.SPACE
            } else {
                null
            }
        }
        val lunar = state(value, World.SPACE, World.MOON)
        if (profile.geometry.moonEnvelope.distance(lunar.position) <= profile.geometry.moonEnvelope.descentDistance) {
            return World.MOON
        }
        val terrestrial = state(value, World.SPACE, World.EARTH)
        if (profile.geometry.earthEnvelope.distance(terrestrial.position) <= profile.geometry.earthEnvelope.descentDistance) {
            return World.EARTH
        }
        return null
    }
}
