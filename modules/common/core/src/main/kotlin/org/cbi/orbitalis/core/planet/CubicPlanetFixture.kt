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

import kotlin.math.abs

/** Small disposable solid planet, shared by placement and tests; not a production generator. */
object CubicPlanetFixture {
    const val HALF_EXTENT = 16
    const val BOUND = 20

    /** Block centres are half-integral. The reference cube stays solid through every edge and corner. */
    fun solid(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val axes = listOf(abs(x + 0.5), abs(y + 0.5), abs(z + 0.5)).sortedDescending()
        val outer = axes[0]
        val second = axes[1]
        if (outer > BOUND) return false
        if (outer < HALF_EXTENT) return true
        // Unequal relief recedes before the shared convex edge; never carve a diagonal trench.
        val relief =
            when {
                y + 0.5 == outer && z in -5..5 -> 3
                x + 0.5 == outer && z in -7..7 -> 2
                else -> 0
            }
        val edgeRelief = minOf(relief.toDouble(), HALF_EXTENT - 0.5 - second).coerceAtLeast(0.0)
        return outer < HALF_EXTENT + edgeRelief
    }
}
