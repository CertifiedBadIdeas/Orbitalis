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

package org.cbi.orbitalis.minecraft.client.space

import net.minecraft.client.Minecraft
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.joml.Matrix4f

/** Root-space presentation seen through a camera in a rotating native surface chart. */
object CelestialView {
    fun sample() = CelestialFlight.sampleClient(Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false))

    /** Surface/sector roots need the authoritative ephemeris; only legacy charts have a static fallback. */
    fun hasFrame(level: Level): Boolean =
        CelestialFlight.available(level) || level.dimension() == Level.OVERWORLD ||
            level.dimension() == org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE

    fun globalPosition(
        level: Level,
        position: Vector3,
    ): Vector3 =
        if (CelestialFlight.available(level)) {
            sample().rootPoint(position, level.dimension())
        } else {
            FlightSpaceCoordinates.legacyRootPosition(level.dimension(), position)
        }

    fun rotation(r: org.cbi.orbitalis.core.orbit.Rotation3): Matrix4f =
        Matrix4f().set(
            floatArrayOf(
                r.xAxis.x.toFloat(),
                r.xAxis.y.toFloat(),
                r.xAxis.z.toFloat(),
                0f,
                r.yAxis.x.toFloat(),
                r.yAxis.y.toFloat(),
                r.yAxis.z.toFloat(),
                0f,
                r.zAxis.x.toFloat(),
                r.zAxis.y.toFloat(),
                r.zAxis.z.toFloat(),
                0f,
                0f,
                0f,
                0f,
                1f,
            ),
        )

    fun earthRotation(): Matrix4f {
        val level = Minecraft.getInstance().level ?: return Matrix4f()
        return if (CelestialFlight.available(
                level,
            )
        ) {
            rotation(
                sample()
                    .frames.profile
                    .earthSurfaceFrame(sample().frames.tick, sample().frames.seconds)
                    .orientation,
            )
        } else {
            Matrix4f()
        }
    }

    fun rootView(
        level: Level,
        localView: Matrix4f,
    ): Matrix4f {
        if (!CelestialFlight.available(level)) return Matrix4f(localView)
        val r = sample().frames.frame(CelestialFlight.chart(level.dimension())).orientation
        return Matrix4f(localView).mul(rotation(r).transpose())
    }
}
