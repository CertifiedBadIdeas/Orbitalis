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
import org.cbi.orbitalis.core.planet.LunarSurface
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs

/** One sampled catalogue/epoch for positions, derivatives and rigid orientation at a native handoff. */
class SurfaceFlightFrames(
    val profile: SolarSystemProfile,
    val tick: Long,
) {
    enum class World { EARTH, SPACE, MOON }

    fun frame(world: World): FrameKinematics =
        when (world) {
            World.EARTH -> FrameKinematics(CartesianState(Vector3.ZERO, Vector3.ZERO))
            World.SPACE -> FrameKinematics(CartesianState(FlatPlanetFrame.origin, Vector3.ZERO))
            World.MOON -> profile.lunarSurfaceFrame(tick)
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

    /** Local top-patch handoffs with separate entry/exit bands. No nearest-body teleport selection. */
    fun destination(
        value: CartesianState,
        from: World,
    ): World? {
        if (from == World.MOON) {
            val p = value.position
            return if ((p.y >= LunarSurface.SEA_LEVEL + LunarSurface.ASCENT_HEIGHT && value.velocity.y >= 0.0) ||
                abs(p.x) >= LunarSurface.HALF_SIZE + 128.0 || abs(p.z) >= LunarSurface.HALF_SIZE + 128.0 || p.y < -192.0
            ) {
                World.SPACE
            } else {
                null
            }
        }
        if (from == World.EARTH) {
            return if (abs(value.position.x) < FlatPlanetDefinition.HALF_SIZE && abs(value.position.z) < FlatPlanetDefinition.HALF_SIZE &&
                value.position.y >= FlatPlanetDefinition.SEA_LEVEL + FlatPlanetDefinition.ASCENT_HEIGHT && value.velocity.y >= 0.0
            ) {
                World.SPACE
            } else {
                null
            }
        }
        val lunar = state(value, World.SPACE, World.MOON)
        if (abs(lunar.position.x) < LunarSurface.HALF_SIZE && abs(lunar.position.z) < LunarSurface.HALF_SIZE &&
            lunar.position.y in LunarSurface.SEA_LEVEL.toDouble()..(LunarSurface.SEA_LEVEL + LunarSurface.DESCENT_HEIGHT) &&
            lunar.velocity.y <= 0.0
        ) {
            return World.MOON
        }
        val terrestrial = state(value, World.SPACE, World.EARTH)
        val earthEntryTop = FlatPlanetDefinition.SEA_LEVEL + FlatPlanetDefinition.DESCENT_HEIGHT
        if (abs(terrestrial.position.x) < FlatPlanetDefinition.HALF_SIZE && abs(terrestrial.position.z) < FlatPlanetDefinition.HALF_SIZE &&
            terrestrial.position.y in FlatPlanetDefinition.SEA_LEVEL..earthEntryTop &&
            terrestrial.velocity.y <= 0.0
        ) {
            return World.EARTH
        }
        return null
    }
}
