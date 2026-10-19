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

import org.cbi.orbitalis.core.orbit.CelestialDynamics
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.SurfaceGravityLayer
import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.abs
import kotlin.math.sqrt

/** Bounded search over burn epochs and continuous return curves; no fixed longitude trigger. */
internal object RocketReturn {
    const val TURN_LEAD = 30.0

    /** Search one pad-relative revolution; the launch pad moves during the vessel's inertial turn. */
    fun searchHorizon(
        orbitPeriod: Double,
        earthRate: Double,
    ): Double {
        require(orbitPeriod.isFinite() && orbitPeriod > 0.0 && earthRate.isFinite())
        val relativeRate = kotlin.math.abs(2.0 * Math.PI / orbitPeriod - earthRate)
        val relativePeriod = if (relativeRate > 1e-8) 2.0 * Math.PI / relativeRate else 3600.0
        return (maxOf(orbitPeriod, relativePeriod) + TURN_LEAD).coerceAtMost(3600.0)
    }

    /** The flown orbit can have a different period from the insertion reference. */
    fun searchHorizon(
        model: CelestialDynamics,
        initial: CartesianState,
    ): Double {
        val frame = model.profile.earthSurfaceFrame(model.epochTick)
        val coast =
            CoastPrediction.orbit(
                model.earth,
                frame.toParent(initial),
                CoastPrediction
                    .Limits(range = model.profile.flightRange),
                model,
            )
        require(
            coast.end in
                setOf(
                    CoastPrediction.End.ORBIT,
                    CoastPrediction.End.REVOLUTION,
                ),
        ) { "Return requires a bounded free orbit: ${coast.end}" }
        val horizon = searchHorizon(coast.terminal.seconds, frame.angularVelocity.z)
        val safeCoast =
            CoastPrediction.predict(
                model.earth,
                frame.toParent(initial),
                CoastPrediction.Limits(
                    horizon = horizon,
                    step = maxOf(0.05, horizon / 11999.0),
                    sampleInterval = maxOf(0.5, horizon / 599.0),
                    range = model.profile.flightRange,
                    hullRadius = 32.0,
                ),
                model,
            )
        // A valid first turn does not make every later turn safe in a rotating nonspherical field.
        // Never choose a burn after a coast that would already have intersected a celestial body.
        require(safeCoast.terminal.seconds >= TURN_LEAD) { "No safe coast remaining for the return turn" }
        return minOf(horizon, safeCoast.terminal.seconds)
    }

    private class Rejections {
        private val counts = mutableMapOf<String, Int>()

        fun reject(reason: String): Double? {
            counts[reason] = (counts[reason] ?: 0) + 1
            return null
        }

        override fun toString() = counts.toSortedMap().toString()
    }

    /** Worker-local immutable samples. Ephemeris state is shared, never a cached position force. */
    fun earthForces(model: CelestialDynamics): (CartesianState, Double) -> Vector3 {
        val epochs = HashMap<Double, CelestialDynamics.Sample>()
        return { state, seconds ->
            // Keep a bounded moving working set. Permanently filling the cache with the earliest
            // epochs would disable reuse for every later candidate in a long return forecast.
            if (epochs.size >= 50000) epochs.clear()
            epochs.getOrPut(seconds) { model.at(seconds) }.earthAcceleration(state)
        }
    }

    class Path(
        val segments: List<RocketConnection>,
        val deltaV: Double,
    ) {
        val duration get() = segments.sumOf { it.duration }

        fun at(time: Double): RocketConnection.Sample {
            var remaining = time
            for (segment in segments) {
                if (remaining <= segment.duration) return segment.at(remaining)
                remaining -= segment.duration
            }
            return segments.last().at(segments.last().duration)
        }
    }

    data class Departure(
        val delay: Double,
        val path: Path,
    )

    /** Immutable free coast, then a burn. Turn lead is included before comparing candidate costs. */
    fun departure(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        home: Vector3,
        authority: Double,
        direction: Vector3,
        horizon: Double,
        acceleration: (CartesianState, Double) -> Vector3 = { state, _ -> field.acceleration(state.position) },
        surfaceDrag: Double = 0.0,
        entryHeight: Double = field.body.halfHeight + field.fullHeight * (2.0 / 3.0),
    ): Departure {
        require(horizon.isFinite() && horizon in TURN_LEAD..3600.0) { "Return search exceeds one bounded forecast: $horizon seconds" }
        require(surfaceDrag.isFinite() && surfaceDrag >= 0.0 && entryHeight.isFinite())
        val step = maxOf(0.05, horizon / 11999.0)
        val rejections = Rejections()
        var state = initial
        var time = 0.0
        var next = TURN_LEAD
        var best: Departure? = null
        while (time <= horizon) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Return planning cancelled")
            if (time + 1e-7 >= next) {
                if (state.position.y > field.body.halfHeight + 32.0) {
                    candidate(
                        field,
                        state,
                        home,
                        authority,
                        direction,
                        rejections,
                        surfaceDrag,
                        entryHeight,
                    ) { s, t -> acceleration(s, time + t) }?.let { path ->
                        if (best == null || path.deltaV < best.path.deltaV) best = Departure(time, path)
                    }
                }
                next += 2.0
            }
            state = coastStep(state, time, step, acceleration)
            time += step
        }
        return checkNotNull(best) {
            "No efficient return window to $home in the next $horizon seconds from $initial along $direction; rejected=$rejections"
        }
    }

    /** Revalidate the fixed burn epoch from an actual short-horizon coast snapshot. */
    fun refine(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        home: Vector3,
        authority: Double,
        direction: Vector3,
        delay: Double,
        acceleration: (CartesianState, Double) -> Vector3,
        surfaceDrag: Double,
        entryHeight: Double,
    ): Path {
        require(delay.isFinite() && delay in 0.0..TURN_LEAD) { "Return refinement exceeds turn lead: $delay seconds" }
        require(surfaceDrag.isFinite() && surfaceDrag >= 0.0 && entryHeight.isFinite())
        var state = initial
        var time = 0.0
        while (time < delay) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Return refinement cancelled")
            val dt = minOf(0.05, delay - time)
            state = coastStep(state, time, dt, acceleration)
            time += dt
        }
        val rejections = Rejections()
        val path =
            if (state.position.y > field.body.halfHeight + 32.0) {
                candidate(field, state, home, authority, direction, rejections, surfaceDrag, entryHeight) { s, t ->
                    acceleration(s, delay + t)
                }
            } else {
                null
            }
        return checkNotNull(path) {
            "No bounded refined return to $home after $delay seconds from $initial along $direction; rejected=$rejections"
        }
    }

    private fun coastStep(
        state: CartesianState,
        time: Double,
        dt: Double,
        acceleration: (CartesianState, Double) -> Vector3,
    ): CartesianState {
        val k1 = acceleration(state, time)
        val mid1 = CartesianState(state.position + state.velocity * (dt * 0.5), state.velocity + k1 * (dt * 0.5))
        val k2 = acceleration(mid1, time + dt * 0.5)
        val mid2 = CartesianState(state.position + mid1.velocity * (dt * 0.5), state.velocity + k2 * (dt * 0.5))
        val k3 = acceleration(mid2, time + dt * 0.5)
        val end = CartesianState(state.position + mid2.velocity * dt, state.velocity + k3 * dt)
        val k4 = acceleration(end, time + dt)
        return CartesianState(
            state.position + (state.velocity + mid1.velocity * 2.0 + mid2.velocity * 2.0 + end.velocity) * (dt / 6.0),
            state.velocity + (k1 + k2 * 2.0 + k3 * 2.0 + k4) * (dt / 6.0),
        )
    }

    private fun candidate(
        field: SurfaceGravityLayer,
        initial: CartesianState,
        home: Vector3,
        authority: Double,
        direction: Vector3,
        rejections: Rejections,
        surfaceDrag: Double,
        entryHeight: Double,
        acceleration: (CartesianState, Double) -> Vector3,
    ): Path? {
        require(authority.isFinite() && authority >= 25.0)
        val remaining = (home - initial.position).dot(direction)
        // The approach axis is fixed by the orbit, not by instantaneous velocity at a corner.
        // Reject a pad that has already been passed, while allowing natural orbital curvature.
        if (remaining < 20.0) {
            rejections.reject("pad-behind")
            return null
        }
        if (initial.velocity.dot(direction) <= 0.0) {
            rejections.reject("moving-away-from-pad")
            return null
        }
        val scale = field.body.halfWidth / 1000.0
        val timeScale = sqrt(scale)
        var best: Path? = null
        for (height in listOf(80.0, 160.0, 300.0)) {
            val approach = CartesianState(home + Vector3(0.0, height * scale, 0.0), Vector3(0.0, -10.0, 0.0))
            for (landingDuration in listOf(maxOf(8.0, height * scale / 25.0), maxOf(12.0, height * scale / 18.0))) {
                val landing =
                    RocketConnection(
                        approach,
                        CartesianState(home + Vector3(0.0, 0.6, 0.0), Vector3(0.0, -0.2, 0.0)),
                        Vector3.ZERO,
                        Vector3.ZERO,
                        landingDuration,
                    )
                for (duration in 8..(80 * timeScale).toInt() step 2) {
                    for (initialThrust in listOf(2.0, 6.0, 10.0)) {
                        val braking =
                            RocketConnection(
                                initial,
                                approach,
                                acceleration(initial, 0.0) - initial.velocity.normalized() * initialThrust,
                                Vector3.ZERO,
                                duration.toDouble(),
                            )
                        val path = Path(listOf(braking, landing), 0.0)
                        val cost =
                            cost(
                                field,
                                path,
                                initial,
                                home,
                                direction,
                                remaining,
                                authority,
                                rejections,
                                surfaceDrag,
                                entryHeight,
                                acceleration,
                            ) ?: continue
                        if (best == null || cost < best.deltaV) best = Path(listOf(braking, landing), cost)
                    }
                }
            }
        }
        // Separate along-track braking from an off-plane approach. A single shared-duration
        // polynomial cannot stop above a near-edge pad while also translating hundreds of metres
        // sideways without overshoot. Every join retains velocity and acceleration continuity.
        val cross = Vector3(home.x - initial.position.x, 0.0, home.z - initial.position.z) - direction * remaining
        if (cross.length() > 50.0) {
            for (height in listOf(300.0, 500.0)) {
                for (fraction in listOf(0.0, 0.25, 0.5)) {
                    val joinPosition = initial.position + direction * remaining + cross * fraction
                    val join =
                        CartesianState(
                            Vector3(joinPosition.x, maxOf(home.y + height * scale, initial.position.y), joinPosition.z),
                            cross.normalized() * minOf(30.0 * timeScale, cross.length() / 12.0) + Vector3(0.0, -10.0, 0.0),
                        )
                    val approach = CartesianState(home + Vector3(0.0, 80.0 * scale, 0.0), Vector3(0.0, -10.0, 0.0))
                    val landing =
                        RocketConnection(
                            approach,
                            CartesianState(home + Vector3(0.0, 0.6, 0.0), Vector3(0.0, -0.2, 0.0)),
                            Vector3.ZERO,
                            Vector3.ZERO,
                            maxOf(8.0, 80.0 * scale / 25.0),
                        )
                    for (crossDuration in listOf(16.0, 24.0, 32.0)) {
                        val crossing = RocketConnection(join, approach, Vector3.ZERO, Vector3.ZERO, crossDuration * timeScale)
                        for (duration in 8..(40 * timeScale).toInt() step 2) {
                            for (initialThrust in listOf(2.0, 6.0, 10.0)) {
                                val braking =
                                    RocketConnection(
                                        initial,
                                        join,
                                        acceleration(initial, 0.0) - initial.velocity.normalized() * initialThrust,
                                        Vector3.ZERO,
                                        duration.toDouble(),
                                    )
                                val path = Path(listOf(braking, crossing, landing), 0.0)
                                val cost =
                                    cost(
                                        field,
                                        path,
                                        initial,
                                        home,
                                        direction,
                                        remaining,
                                        authority,
                                        rejections,
                                        surfaceDrag,
                                        entryHeight,
                                        acceleration,
                                    ) ?: continue
                                if (best == null || cost < best.deltaV) best = Path(path.segments, cost)
                            }
                        }
                    }
                }
            }
        }
        return best
    }

    /** Integral of required thrust acceleration, not kinetic energy or creative engine power. */
    private fun cost(
        field: SurfaceGravityLayer,
        path: Path,
        initial: CartesianState,
        home: Vector3,
        direction: Vector3,
        remaining: Double,
        authority: Double,
        rejections: Rejections,
        surfaceDrag: Double,
        entryHeight: Double,
        acceleration: (CartesianState, Double) -> Vector3,
    ): Double? {
        val attitude = RocketAttitudeControl.Envelope(initial.velocity * -1.0)
        var cost = 0.0
        var surface = false
        var earlyThrust = Vector3.ZERO
        var t = 0.0
        while (true) {
            val sample = path.at(t)
            val p = sample.state.position
            val horizontal = Vector3(p.x - home.x, 0.0, p.z - home.z).length()
            val progress = (p - initial.position).dot(direction)
            val clearance =
                Vector3(
                    maxOf(0.0, abs(p.x) - field.body.halfWidth),
                    maxOf(0.0, abs(p.y) - field.body.halfHeight),
                    maxOf(0.0, abs(p.z) - field.body.halfDepth),
                ).length()
            if (p.y <= entryHeight && sample.state.velocity.y < 0.0 &&
                abs(p.x) < field.body.halfWidth - 40.0 &&
                abs(p.z) < field.body.halfDepth - 40.0
            ) {
                surface = true
            }
            // Surface entry is latched for this candidate, just like the real dimension transfer.
            // Never put this state into the force closure shared by different candidate paths.
            val drag = if (surface) sample.state.velocity * -surfaceDrag else Vector3.ZERO
            val thrust = sample.acceleration - acceleration(sample.state, t) - drag
            if (abs(t - 2.0) < 1e-8) earlyThrust = thrust
            val inside = abs(p.x) < field.body.halfWidth && abs(p.z) < field.body.halfDepth
            val rejection =
                when {
                    horizontal >= 30.0 && clearance < 32.0 -> "terrain-clearance"
                    inside && p.y < home.y -> "below-pad"
                    p.y >= maxOf(field.body.halfHeight + field.body.halfWidth, initial.position.y) + 200.0 -> "altitude"
                    sample.state.velocity.length() >= 180.0 * sqrt(field.body.halfWidth / 1000.0) -> "speed"
                    thrust.length() > minOf(authority * 0.6, 25.0) -> "thrust"
                    surface && (abs(p.x) >= field.body.halfWidth - 20.0 || abs(p.z) >= field.body.halfDepth - 20.0) -> "surface-footprint"
                    inside && p.y < maxOf(field.body.halfHeight + 32.0, home.y + 120.0) && horizontal >= 30.0 -> "low-sideways-approach"
                    progress > remaining + 0.5 -> "pad-overshoot"
                    else -> null
                }
            if (rejection != null) return rejections.reject(rejection)
            val dt = minOf(0.25, path.duration - t)
            if (dt > 0.0 && !attitude.step(thrust, dt)) return rejections.reject("attitude-cone")
            cost += thrust.length() * dt
            if (dt <= 0.0) break
            t += dt
        }
        if (earlyThrust.dot(initial.velocity) >= 0.0) {
            return rejections.reject("not-braking")
        }
        return cost
    }
}
