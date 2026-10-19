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

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.PI
import kotlin.math.atan2

/** Presentation reference only: never turns a vessel or changes native collision/movement axes. */
class LunarApproachView {
    private var angle: Double? = null
    private var earthPhase: Double? = null

    fun clear() {
        angle = null
        earthPhase = null
    }

    fun up(
        frames: SurfaceFlightFrames,
        position: Vector3,
        world: SurfaceFlightFrames.World,
    ): Vector3 {
        val earth = frames.frame(SurfaceFlightFrames.World.EARTH)
        val root =
            frames
                .frame(world)
                .toParent(
                    org.cbi.orbitalis.core.planet
                        .CartesianState(position, Vector3.ZERO),
                ).position
        val dimensions = frames.profile.geometry
        val earthInner =
            minOf(
                Vector3(
                    dimensions.earthHalfSize,
                    64.0,
                    dimensions.earthHalfSize,
                ).length() + dimensions.earthEnvelope.ascentDistance + 200.0,
                frames.profile.earthHillRadius * 0.95,
            )
        val earthT = ((root.length() - earthInner) / (frames.profile.earthHillRadius - earthInner)).coerceIn(0.0, 1.0)
        val principalEarth = atan2(earth.orientation.xAxis.y, earth.orientation.xAxis.x)
        val prior = if (earthT == 0.0 || earthT == 1.0) principalEarth else earthPhase ?: principalEarth
        val unwrappedEarth = prior + atan2(kotlin.math.sin(principalEarth - prior), kotlin.math.cos(principalEarth - prior))
        earthPhase = if (earthT == 1.0) null else unwrappedEarth
        val earthWeight = 1.0 - earthT * earthT * earthT * (10.0 + earthT * (-15.0 + 6.0 * earthT))
        val earthAngle = unwrappedEarth * earthWeight
        val earthUp = Rotation3.axisAngle(Vector3(0.0, 0.0, 1.0), earthAngle).yAxis
        val moon = frames.frame(SurfaceFlightFrames.World.MOON)
        val local = frames.point(position, world, SurfaceFlightFrames.World.MOON)
        // Start only inside the body's Hill sphere, fully matching all surface handoff faces.
        val outer = frames.profile.moonHillRadius
        val body = frames.profile.moonGravity
        val bounds = Vector3(body.halfWidth, body.halfHeight, body.halfDepth).length()
        val inner = minOf(bounds + frames.profile.geometry.moonEnvelope.ascentDistance + 200.0, outer * 0.95)
        val t = ((local.length() - inner) / (outer - inner)).coerceIn(0.0, 1.0)
        if (t == 1.0) {
            angle = null
            return frames.frame(world).orientation.inverse(earthUp)
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

        /** Native-style yaw and bounded elevation in the local horizon, not the screen plane. */
        fun turnRay(
            forward: Vector3,
            up: Vector3,
            left: Vector3,
            x: Double,
            y: Double,
        ): Vector3 {
            val vertical = up.normalized()
            val elevation = kotlin.math.asin(forward.dot(vertical).coerceIn(-1.0, 1.0))
            val projected = forward - vertical * forward.dot(vertical)
            val heading = if (projected.length() > 1e-9) projected.normalized() else left.cross(vertical).normalized()
            val yaw = Rotation3.axisAngle(vertical, -x * 0.15 * PI / 180.0)
            // Keep a unique horizontal heading outside stableUp's undefined projection cone.
            // Without this bound one mouse step crosses the zenith, reverses screen up, and
            // makes the following pitch step cross back, repeatedly flipping the horizon.
            val limit = 89.9 * PI / 180.0
            val pitch = (elevation - y * 0.15 * PI / 180.0).coerceIn(-limit, limit)
            return (yaw.apply(heading) * kotlin.math.cos(pitch) + vertical * kotlin.math.sin(pitch)).normalized()
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
