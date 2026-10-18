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

/** An untranslated-axis inertial origin relative to its parent, measured at [epochTick]. */
data class InertialFrame(
    val id: String,
    val parent: String?,
    val origin: CartesianState,
    val epochTick: Long = 0,
)

/** Immutable translation/constant-velocity hierarchy; no rotational frame corrections are implied. */
class InertialFrames(
    frames: List<InertialFrame>,
) {
    private val framesById: Map<String, InertialFrame>
    private val paths: Map<String, List<InertialFrame>>

    init {
        require(frames.size in 1..1024) { "Expected 1..1024 inertial frames" }
        val identifier = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
        require(frames.all { identifier.matches(it.id) }) { "Invalid frame identifier" }
        framesById = frames.associateBy { it.id }
        require(framesById.size == frames.size) { "Duplicate frame identifier" }
        require(frames.all { it.epochTick >= 0 }) { "Frame epoch must be nonnegative" }
        val root = frames.singleOrNull { it.parent == null }
        require(root != null) { "Expected exactly one root frame" }
        require(root.origin == CartesianState(Vector3.ZERO, Vector3.ZERO)) { "Root must be stationary at the origin" }
        paths =
            frames.associate { frame ->
                val path = mutableListOf<InertialFrame>()
                val seen = mutableSetOf<String>()
                var current: InertialFrame? = frame
                while (current != null) {
                    require(seen.add(current.id)) { "Cyclic frame hierarchy" }
                    path.add(current)
                    current = current.parent?.let { requireNotNull(framesById[it]) { "Unknown parent frame: $it" } }
                }
                frame.id to path.toList()
            }
    }

    /** [tick] is server game time at 20 Hz; velocities are metres per second. */
    fun transform(
        state: CartesianState,
        from: String,
        to: String,
        tick: Long,
    ): CartesianState {
        require(tick >= 0) { "Game tick must be nonnegative" }
        val source = requireNotNull(paths[from]) { "Unknown source frame: $from" }
        val destination = requireNotNull(paths[to]) { "Unknown destination frame: $to" }
        // Cancel shared ancestors before arithmetic, preserving local precision near distant origins.
        val shared =
            source
                .asReversed()
                .zip(destination.asReversed())
                .takeWhile { it.first.id == it.second.id }
                .size
        val a = origin(source.dropLast(shared), tick)
        val b = origin(destination.dropLast(shared), tick)
        return CartesianState(state.position + (a.position - b.position), state.velocity + (a.velocity - b.velocity))
    }

    private fun origin(
        path: List<InertialFrame>,
        tick: Long,
    ): CartesianState {
        var position = Vector3.ZERO
        var velocity = Vector3.ZERO
        for (frame in path) {
            val seconds = (tick - frame.epochTick).toDouble() / 20.0
            position += frame.origin.position + frame.origin.velocity * seconds
            velocity += frame.origin.velocity
        }
        return CartesianState(position, velocity)
    }
}
