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

import net.minecraft.client.renderer.ShaderInstance
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.SolarShape
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.lighting.SkyLighting

/** Lighting uniforms are sampled at draw time; cached sector geometry never bakes a sun direction. */
object PlanetLighting {
    var shader: ShaderInstance? = null

    fun earthDirection(): Vector3 {
        val level =
            net.minecraft.client.Minecraft
                .getInstance()
                .level
        if (level == null ||
            !org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .available(level)
        ) {
            return Vector3(0.0, 1.0, 0.0)
        }
        val sample = CelestialView.sample()
        return SkyLighting.earthDirection(sample.frames.profile, sample.frames.tick, sample.frames.seconds)
    }

    fun prepare(direction: Vector3): ShaderInstance {
        val result = checkNotNull(shader)
        result.getUniform("SunDirection")?.set(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
        result.getUniform("EclipseEnabled")?.set(0f)
        return result
    }

    /** Earth-local eclipse coordinates are normalized to Earth half-width for GPU precision. */
    fun prepareMoon(
        profile: SolarSystemProfile,
        tick: Long,
        seconds: Double,
    ): ShaderInstance {
        val moon = profile.lunarSurfaceFrame(tick, seconds)
        val earth = profile.earthSurfaceFrame(tick, seconds).orientation
        val sun = SkyLighting.sunPosition(profile, tick, seconds)
        val result = prepare(moon.orientation.inverse((sun - moon.origin.position).normalized()))
        val scale = profile.geometry.earthHalfSize
        val origin = earth.inverse(moon.origin.position) * (1.0 / scale)
        val localSun = earth.inverse(sun) * (1.0 / scale)
        val half = Vector3(1.0, org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SEA_LEVEL / scale, 1.0)
        val source = SolarShape.orientation(tick, seconds)
        val halfEdge = profile.sunRadius / scale

        fun vector(
            name: String,
            value: Vector3,
        ) {
            result.getUniform(name)?.set(value.x.toFloat(), value.y.toFloat(), value.z.toFloat())
        }
        vector("ShadowOrigin", origin)
        vector("ShadowAxisX", earth.inverse(moon.orientation.xAxis) * (1.0 / scale))
        vector("ShadowAxisY", earth.inverse(moon.orientation.yAxis) * (1.0 / scale))
        vector("ShadowAxisZ", earth.inverse(moon.orientation.zAxis) * (1.0 / scale))
        vector("ShadowSun", localSun)
        vector("ShadowSunX", earth.inverse(source.xAxis) * halfEdge)
        vector("ShadowSunY", earth.inverse(source.yAxis) * halfEdge)
        vector("ShadowSunZ", earth.inverse(source.zAxis) * halfEdge)
        vector("ShadowHalfSize", half)
        result.getUniform("EclipseEnabled")?.set(1f)
        return result
    }
}
