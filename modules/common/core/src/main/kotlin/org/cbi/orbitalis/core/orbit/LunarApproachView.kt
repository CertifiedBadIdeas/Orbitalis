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
    private var bodyId: String? = null

    fun clear() {
        angle = null
        earthPhase = null
        bodyId = null
    }

    fun up(
        frames: SurfaceFlightFrames,
        position: Vector3,
        world: SurfaceFlightFrames.World,
    ): Vector3 =
        up(
            frames,
            position,
            when (world) {
                SurfaceFlightFrames.World.EARTH -> FlightFrame.Surface(SolarSystemProfile.EARTH)
                SurfaceFlightFrames.World.MOON -> FlightFrame.Surface(SolarSystemProfile.MOON)
                SurfaceFlightFrames.World.SPACE -> FlightFrame.LegacySpace
            },
        )

    /** One physical horizon regardless of whether the observer uses a surface or orbital sector chart. */
    fun up(
        frames: SurfaceFlightFrames,
        position: Vector3,
        world: FlightFrame,
    ): Vector3 {
        val chart = frames.frame(world)
        val root =
            chart
                .toParent(
                    org.cbi.orbitalis.core.planet
                        .CartesianState(position, Vector3.ZERO),
                ).position
        val earth = frames.frame(SurfaceFlightFrames.World.EARTH)
        val dimensions = frames.profile.geometry
        val earthInner =
            minOf(
                Vector3(dimensions.earthHalfSize, 64.0, dimensions.earthHalfSize).length() +
                    dimensions.earthEnvelope.ascentDistance + 200.0,
                frames.profile.earthHillRadius * 0.95,
            )
        val earthT = ((root.length() - earthInner) / (frames.profile.earthHillRadius - earthInner)).coerceIn(0.0, 1.0)
        val principalEarth = atan2(earth.orientation.xAxis.y, earth.orientation.xAxis.x)
        val prior = if (earthT == 0.0 || earthT == 1.0) principalEarth else earthPhase ?: principalEarth
        val unwrappedEarth = unwrap(principalEarth, prior)
        earthPhase = if (earthT == 1.0) null else unwrappedEarth
        val earthAngle = unwrappedEarth * weight(earthT)

        // The nearest body's presentation sphere captures the horizon. It encloses the full
        // handoff even when a small preset places that envelope beyond its physical Hill radius.
        // The enclosing Earth field remains
        // the base for lunar approach, preserving its existing smooth release into interplanetary space.
        var selected: HorizonBody? = null
        var nearest = if (root.length() < frames.profile.earthHillRadius) root.length() else Double.POSITIVE_INFINITY

        fun consider(candidate: HorizonBody) {
            val distance = (root - candidate.frame.origin.position).length()
            if (distance < candidate.outer && distance < nearest) {
                selected = candidate
                nearest = distance
            }
        }
        val moon = frames.profile.moonGravity
        consider(
            HorizonBody(
                SolarSystemProfile.MOON,
                frames.frame(SurfaceFlightFrames.World.MOON),
                Vector3(moon.halfWidth, moon.halfHeight, moon.halfDepth).length() + dimensions.moonEnvelope.ascentDistance + 200.0,
                frames.profile.moonHillRadius,
            ),
        )
        for (planet in frames.profile.innerPlanets) {
            consider(
                HorizonBody(
                    planet.id,
                    frames.frame(FlightFrame.Surface(planet.id)),
                    Vector3(planet.halfSize, 64.0, planet.halfSize).length() + planet.envelope.ascentDistance + 200.0,
                    planet.hillRadius,
                ),
            )
        }
        val body = selected
        if (body?.id != bodyId) {
            angle = null
            bodyId = body?.id
        }
        if (body == null) {
            return chart.orientation.inverse(Rotation3.axisAngle(Vector3(0.0, 0.0, 1.0), earthAngle).yAxis)
        }
        val inner = minOf(body.inner, body.outer * 0.95)
        val t = ((nearest - inner) / (body.outer - inner)).coerceIn(0.0, 1.0)
        val principal = atan2(body.frame.orientation.xAxis.y, body.frame.orientation.xAxis.x) - earthAngle
        val previous = if (t == 0.0) principal else angle ?: principal
        val unwrapped = unwrap(principal, previous)
        angle = unwrapped
        val rootUp = Rotation3.axisAngle(Vector3(0.0, 0.0, 1.0), earthAngle + unwrapped * weight(t)).yAxis
        return chart.orientation.inverse(rootUp)
    }

    private data class HorizonBody(
        val id: String,
        val frame: FrameKinematics,
        val inner: Double,
        val hillRadius: Double,
    ) {
        val outer: Double get() = maxOf(hillRadius, inner / 0.95)
    }

    private fun weight(t: Double): Double = 1.0 - t * t * t * (10.0 + t * (-15.0 + 6.0 * t))

    private fun unwrap(
        principal: Double,
        prior: Double,
    ): Double = prior + atan2(kotlin.math.sin(principal - prior), kotlin.math.cos(principal - prior))

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
