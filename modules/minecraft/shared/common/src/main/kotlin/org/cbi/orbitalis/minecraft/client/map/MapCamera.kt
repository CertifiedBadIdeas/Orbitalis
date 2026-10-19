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

package org.cbi.orbitalis.minecraft.client.map

import org.cbi.orbitalis.core.planet.Vector3
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Orthographic orbit camera, shared by drawing and marker hit testing. Units are physical metres. */
class MapCamera {
    var yaw = 0.65
    var pitch = 0.85
        private set
    var range = 8500.0
        private set
    var focus = Vector3.ZERO
    val direction: Vector3 get() = Vector3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
    val right: Vector3 get() = Vector3(cos(yaw), 0.0, -sin(yaw))
    val up: Vector3 get() = direction.cross(right)

    data class Point(
        val x: Double,
        val y: Double,
    )

    fun rotate(
        dx: Double,
        dy: Double,
    ) {
        if (!dx.isFinite() || !dy.isFinite()) return
        yaw = (yaw - dx * 0.008) % (Math.PI * 2)
        // Keep the same continuous camera basis through both poles, including an inverted view.
        pitch = (pitch + dy * 0.008) % (Math.PI * 2)
    }

    /** Move the scene by GUI pixels in the current camera plane, at the current zoom. */
    fun pan(
        dx: Double,
        dy: Double,
        viewportHeight: Int,
    ) {
        if (viewportHeight <= 0 || !dx.isFinite() || !dy.isFinite()) return
        val metresPerPixel = range * 2.0 / viewportHeight
        focus = focus - right * (dx * metresPerPixel) + up * (dy * metresPerPixel)
    }

    fun zoom(scroll: Double) {
        range = (range * exp(-scroll.coerceIn(-20.0, 20.0) * 0.15)).coerceIn(128.0, 1e8)
    }

    /** Fit a physical bounding radius even when the viewport is taller than it is wide. */
    fun frame(
        position: Vector3,
        range: Double,
        width: Int = 1,
        height: Int = 1,
    ) {
        require(range.isFinite() && range > 0.0 && width > 0 && height > 0)
        focus = position
        this.range = (range * maxOf(1.0, height.toDouble() / width)).coerceIn(128.0, 1e8)
    }

    fun reset() {
        focus = Vector3.ZERO
        range = 8500.0
        yaw = 0.65
        pitch = 0.85
    }

    fun project(
        position: Vector3,
        width: Int,
        height: Int,
    ): Point? {
        if (width <= 0 || height <= 0) return null
        val relative = position - focus
        val point = projectRaw(position, width, height)
        val x = point.x
        val y = point.y
        return if (x in 0.0..width.toDouble() && y in 0.0..height.toDouble() &&
            kotlin.math.abs(relative.dot(direction) / range - 1.0) < 99.0
        ) {
            Point(x, y)
        } else {
            null
        }
    }

    /** Unclipped coordinates for GPU-scissored segments, including crossings with both ends outside. */
    fun projectRaw(
        position: Vector3,
        width: Int,
        height: Int,
    ): Point {
        val relative = position - focus
        return Point(
            relative.dot(right) / range * height * 0.5 + width * 0.5,
            -relative.dot(up) / range * height * 0.5 + height * 0.5,
        )
    }
}
