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

package org.cbi.orbitalis.core.planet

import org.cbi.orbitalis.core.orbit.Rotation3

/** Compatibility chart for one origin-centred opaque body. Sampling matches the surface shader. */
class SolarOcclusion(
    val sun: Vector3,
    val halfEdge: Double,
    val orientation: Rotation3,
    val halfSize: Vector3,
) {
    private val emitter = SolarEmitterCube(sun, halfEdge, orientation)
    private val illumination = SolarVisibility(emitter, listOf(OpaqueSolarBox(Vector3.ZERO, halfSize)))

    fun visibility(point: Vector3): Double = illumination.visibility(point)

    fun sample(
        point: Vector3,
        index: Int,
    ): Vector3 = emitter.sample(point, index)

    companion object {
        const val SAMPLE_COUNT = 32
        const val SAMPLE_STRIDE = 0.6180339887498949
    }
}
