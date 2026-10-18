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

import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.orbit.SurfaceFlightFrames
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.joml.Matrix4f

/** Root-space presentation seen through a camera whose local world rotates with the Moon. */
object CelestialView {
    fun rootView(
        level: Level,
        localView: Matrix4f,
    ): Matrix4f {
        if (level.dimension() != MoonWorld.KEY) return Matrix4f(localView)
        val r =
            CelestialFlight
                .sample(level)
                .frames
                .frame(SurfaceFlightFrames.World.MOON)
                .orientation
        val inverse =
            Matrix4f().set(
                floatArrayOf(
                    r.xAxis.x.toFloat(),
                    r.yAxis.x.toFloat(),
                    r.zAxis.x.toFloat(),
                    0f,
                    r.xAxis.y.toFloat(),
                    r.yAxis.y.toFloat(),
                    r.zAxis.y.toFloat(),
                    0f,
                    r.xAxis.z.toFloat(),
                    r.yAxis.z.toFloat(),
                    r.zAxis.z.toFloat(),
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                ),
            )
        return Matrix4f(localView).mul(inverse)
    }
}
