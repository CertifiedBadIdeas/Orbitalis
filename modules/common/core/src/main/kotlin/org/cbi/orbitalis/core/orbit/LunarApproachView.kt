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

import org.cbi.orbitalis.core.planet.PlanetFlightEnvelope
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.atan2

/** Presentation reference only: never turns a vessel or changes native collision/movement axes. */
class LunarApproachView {
    private var angle: Double? = null

    fun clear() {
        angle = null
    }

    fun up(
        frames: SurfaceFlightFrames,
        position: Vector3,
        world: SurfaceFlightFrames.World,
    ): Vector3 {
        val earth = frames.frame(SurfaceFlightFrames.World.EARTH)
        val earthAngle = atan2(earth.orientation.xAxis.y, earth.orientation.xAxis.x)
        val moon = frames.frame(SurfaceFlightFrames.World.MOON)
        val local = frames.point(position, world, SurfaceFlightFrames.World.MOON)
        // Start only inside the body's Hill sphere, fully matching all surface handoff faces.
        val outer = frames.profile.moonHillRadius
        val body = frames.profile.moonGravity
        val bounds = Vector3(body.halfWidth, body.halfHeight, body.halfDepth).length()
        val inner = minOf(bounds + PlanetFlightEnvelope.moon.ascentDistance + 200.0, outer * 0.95)
        val t = ((local.length() - inner) / (outer - inner)).coerceIn(0.0, 1.0)
        if (t == 1.0) {
            clear()
            return frames.frame(world).orientation.inverse(earth.orientation.yAxis)
        }
        val principal = atan2(moon.orientation.xAxis.y, moon.orientation.xAxis.x) - earthAngle
        val previous = if (t == 0.0) principal else angle ?: principal
        val unwrapped = previous + atan2(kotlin.math.sin(principal - previous), kotlin.math.cos(principal - previous))
        angle = unwrapped
        val weight = 1.0 - t * t * t * (10.0 + t * (-15.0 + 6.0 * t))
        val rootUp = Rotation3.axisAngle(Vector3(0.0, 0.0, 1.0), earthAngle + unwrapped * weight).yAxis
        return frames.frame(world).orientation.inverse(rootUp)
    }

    companion object {
        /** At the viewing pole gravity has no screen projection; retain transported screen up. */
        fun stableUp(
            desired: Vector3,
            forward: Vector3,
            previous: Vector3,
        ): Vector3 = if (desired.cross(forward).length() < 1e-3) previous else desired

        fun turnRay(
            forward: Vector3,
            up: Vector3,
            left: Vector3,
            x: Double,
            y: Double,
        ): Vector3 {
            val yaw = Rotation3.axisAngle(up, -x * 0.15 * PI / 180.0)
            val pitch = Rotation3.axisAngle(left, y * 0.15 * PI / 180.0)
            return (pitch * yaw).apply(forward).normalized()
        }

        /** Native camera's positive roll turns up towards minus-left. Preserve the look ray. */
        fun roll(
            desiredUp: Vector3,
            nativeUp: Vector3,
            nativeLeft: Vector3,
        ): Double {
            val y = desiredUp.dot(nativeUp)
            val x = desiredUp.dot(nativeLeft)
            return if (x * x + y * y < 1e-12) 0.0 else -atan2(x, y) * 180.0 / PI
        }
    }
}
