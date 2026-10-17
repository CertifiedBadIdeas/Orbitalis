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

package org.cbi.orbitalis.minecraft.client.ship

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Pure visual geometry. The instrument's rocket frame is nose=+Y, screen-up=-Z, right=+X. */
object ShipViewMath {
    fun fitDistance(
        radius: Double,
        verticalFov: Double,
        aspect: Double,
    ): Double {
        require(radius.isFinite() && radius > 0.0 && verticalFov in 1.0..179.0 && aspect.isFinite() && aspect > 0.0)
        val vertical = Math.toRadians(verticalFov) * 0.5
        val horizontal = atan(tan(vertical) * aspect)
        return radius / sin(min(vertical, horizontal)) * 1.12
    }

    fun direction(
        yaw: Double,
        pitch: Double,
    ): Vector3 {
        val y = Math.toRadians(yaw)
        val p = Math.toRadians(pitch)
        return Vector3(-sin(y) * cos(p), -sin(p), cos(y) * cos(p))
    }

    fun zoom(
        current: Double,
        scroll: Double,
    ): Double = (current * exp(-scroll.coerceIn(-20.0, 20.0) * 0.12)).coerceIn(1.0, 8.0)

    /** Camera-facing hemisphere; x is right, y is up, z is towards the rocket's nose. */
    fun hemisphere(
        x: Double,
        y: Double,
    ): Vector3? {
        val z2 = 1.0 - x * x - y * y
        return if (z2 < 0.0) null else Vector3(x, y, sqrt(z2))
    }

    fun instrument(
        local: Vector3,
        rocketAxes: Boolean,
    ): Vector3 = if (rocketAxes) Vector3(local.x, -local.z, local.y) else local

    fun marker(directionInInstrument: Vector3): Vector3? {
        if (directionInInstrument.length() < 0.25) return null
        val v = directionInInstrument.normalized()
        return if (v.z < -0.02) null else v
    }
}
