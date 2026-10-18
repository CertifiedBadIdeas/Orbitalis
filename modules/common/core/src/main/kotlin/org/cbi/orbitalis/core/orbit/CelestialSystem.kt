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
import org.cbi.orbitalis.core.planet.Vector3

/** A source with gravitational parameter [mu] (m³/s²) and a prescribed orbit; the root has no orbit. */
data class CelestialBody(
    val id: String,
    val parent: String?,
    val mu: Double,
    val orbit: KeplerOrbit? = null,
)

/**
 * Immutable body hierarchy. Child paths are prescribed around their parent's focus, using its mu;
 * they neither perturb the parent nor react to ships. This is not an N-body or barycentric solver.
 */
class CelestialSystem(
    bodies: List<CelestialBody>,
) {
    private val bodiesById: Map<String, CelestialBody>
    private val paths: Map<String, List<String>>

    init {
        require(bodies.size in 1..1024) { "Expected 1..1024 celestial bodies" }
        bodiesById = bodies.associateBy { it.id }
        require(bodiesById.size == bodies.size) { "Duplicate celestial identifier" }
        val identifier = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
        require(bodies.all { identifier.matches(it.id) && it.mu.isFinite() && it.mu > 0.0 }) { "Invalid celestial identifier or mass" }
        require(bodies.count { it.parent == null } == 1) { "Expected exactly one root body" }
        paths =
            bodies.associate { body ->
                require((body.parent == null) == (body.orbit == null)) { "Only the root may omit an orbit" }
                val path = mutableListOf<CelestialBody>()
                val seen = mutableSetOf<String>()
                var current: CelestialBody? = body
                while (current != null) {
                    require(seen.add(current.id)) { "Cyclic celestial hierarchy" }
                    path.add(current)
                    current = current.parent?.let { requireNotNull(bodiesById[it]) { "Unknown parent body: $it" } }
                }
                body.parent?.let { parent ->
                    require(body.orbit!!.mu == bodiesById.getValue(parent).mu) { "Orbit must use its parent's gravitational parameter" }
                }
                body.id to path.map { it.id }
            }
    }

    /** The caller supplies the authoritative simulation clock; sampling never advances it. */
    fun sample(
        tick: Long,
        seconds: Double = 0.0,
    ): CelestialSnapshot {
        require(seconds.isFinite()) { "Sample offset must be finite" }
        require(tick >= 0) { "Simulation tick must be nonnegative" }
        val zero = FrameKinematics(CartesianState(Vector3.ZERO, Vector3.ZERO))
        val relative = bodiesById.mapValues { (_, body) -> body.orbit?.sample(tick, seconds) ?: zero }
        return CelestialSnapshot(tick, paths, relative)
    }
}

/** One immutable ephemeris sample, with parallel inertial axes and no surface rotation. */
class CelestialSnapshot internal constructor(
    val tick: Long,
    private val paths: Map<String, List<String>>,
    private val relative: Map<String, FrameKinematics>,
) {
    /** Body origin and its derivatives relative to [reference], cancelling shared distant ancestors. */
    fun frame(
        body: String,
        reference: String,
    ): FrameKinematics {
        val source = requireNotNull(paths[body]) { "Unknown body: $body" }
        val destination = requireNotNull(paths[reference]) { "Unknown reference body: $reference" }
        val shared =
            source
                .asReversed()
                .zip(destination.asReversed())
                .takeWhile { it.first == it.second }
                .size
        val a = origin(source.dropLast(shared))
        val b = origin(destination.dropLast(shared))
        return FrameKinematics(
            CartesianState(a.origin.position - b.origin.position, a.origin.velocity - b.origin.velocity),
            originAcceleration = a.originAcceleration - b.originAcceleration,
        )
    }

    private fun origin(path: List<String>): FrameKinematics {
        var position = Vector3.ZERO
        var velocity = Vector3.ZERO
        var acceleration = Vector3.ZERO
        for (id in path) {
            val frame = relative.getValue(id)
            position += frame.origin.position
            velocity += frame.origin.velocity
            acceleration += frame.originAcceleration
        }
        return FrameKinematics(CartesianState(position, velocity), originAcceleration = acceleration)
    }
}
