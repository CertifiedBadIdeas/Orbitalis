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
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/** Gravity frame shared by raster grid and its projected pitch labels. */
object NavballGrid {
    data class Frame(
        val up: Vector3,
        val north: Vector3,
        val east: Vector3,
    )

    enum class Line { NONE, MINOR, MAJOR, HORIZON }

    fun worldFrame(up: Vector3): Frame {
        val axis = if (abs(up.z) < 0.95) Vector3(0.0, 0.0, -1.0) else Vector3(1.0, 0.0, 0.0)
        val north = (axis - up * axis.dot(up)).normalized()
        return Frame(up, north, north.cross(up).normalized())
    }

    fun pitch(
        point: Vector3,
        frame: Frame,
    ): Double = Math.toDegrees(asin(point.dot(frame.up).coerceIn(-1.0, 1.0)))

    fun point(
        frame: Frame,
        pitchDegrees: Double,
        headingDegrees: Double,
    ): Vector3 {
        val latitude = Math.toRadians(pitchDegrees)
        val longitude = Math.toRadians(headingDegrees)
        return frame.up * sin(latitude) +
            (frame.north * cos(longitude) + frame.east * sin(longitude)) * cos(latitude)
    }

    fun line(
        point: Vector3,
        frame: Frame,
    ): Line {
        val latitude = pitch(point, frame)
        if (abs(latitude) < 1.0) return Line.HORIZON
        val band = round(latitude / 15.0)
        if (abs(latitude - band * 15.0) < 0.65) return if (band.toInt() % 2 == 0) Line.MAJOR else Line.MINOR
        // Meridians identify attitude even when one hemisphere fills the entire ball.
        if (abs(latitude) < 80.0) {
            val longitude = Math.toDegrees(atan2(point.dot(frame.east), point.dot(frame.north)))
            if (abs(longitude - round(longitude / 30.0) * 30.0) < 0.75) return Line.MINOR
        }
        return Line.NONE
    }
}
