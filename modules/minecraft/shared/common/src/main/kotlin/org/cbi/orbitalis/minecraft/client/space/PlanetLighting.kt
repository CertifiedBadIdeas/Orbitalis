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

    fun earthSunRadiance(): Vector3 {
        val level =
            net.minecraft.client.Minecraft
                .getInstance()
                .level
        if (level == null ||
            !org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .available(level)
        ) {
            return Vector3(1.0, 1.0, 1.0)
        }
        val sample = CelestialView.sample().frames
        return sunRadiance(sample.profile, SkyLighting.sunPosition(sample.profile, sample.tick, sample.seconds).length())
    }

    private fun sunRadiance(
        profile: SolarSystemProfile,
        distance: Double,
    ): Vector3 {
        val intensity = (profile.earthOrbitRadius / distance.coerceAtLeast(profile.sunRadius)).let { it * it }
        return Vector3(intensity, intensity, intensity)
    }

    /** Model-view includes both map focus/range and flight origin; focus is not the eye. */
    fun prepareView(
        shader: ShaderInstance,
        modelView: org.joml.Matrix4f,
        projection: org.joml.Matrix4f,
    ) {
        val inverse = org.joml.Matrix4f(modelView).invert()
        val eye = inverse.transformPosition(org.joml.Vector3f())
        val direction = inverse.transformDirection(org.joml.Vector3f(0f, 0f, 1f)).normalize()
        shader.getUniform("Observer")?.set(eye.x, eye.y, eye.z)
        shader.getUniform("ViewDirection")?.set(direction.x, direction.y, direction.z)
        shader.getUniform("ParallelView")?.set(if (kotlin.math.abs(projection.m33()) > 0.5f) 1f else 0f)
    }

    fun vector(
        shader: ShaderInstance,
        name: String,
        value: Vector3,
    ) {
        shader.getUniform(name)?.set(value.x.toFloat(), value.y.toFloat(), value.z.toFloat())
    }

    /** Shared atmosphere coefficients for surface, clouds and the single-scattering shell. */
    fun prepareAtmosphere(shader: ShaderInstance) {
        val cloudHeight = PlanetCloudRenderer.atmosphereCloudHeight()
        val (bottom, top) = PlanetLimbRenderer.atmosphereBounds(SpaceScene.HALF_SIZE, cloudHeight)
        val optics = AtmosphereOptics.parameters(bottom.y, top.y)
        shader.getUniform("AtmosphereBottom")?.set(bottom.y.toFloat())
        shader.getUniform("AtmosphereTop")?.set(top.y.toFloat())
        shader.getUniform("AtmosphereHalfSize")?.set(SpaceScene.HALF_SIZE.toFloat())
        shader.getUniform("AtmosphereFeather")?.set(PlanetLimbRenderer.atmosphereFeather(SpaceScene.HALF_SIZE).toFloat())
        vector(shader, "RayleighCoefficient", optics.rayleighPerMetre)
        shader.getUniform("MieCoefficient")?.set(optics.miePerMetre.toFloat())
        shader.getUniform("RayleighHeight")?.set(optics.rayleighHeight.toFloat())
        shader.getUniform("MieHeight")?.set(optics.mieHeight.toFloat())
    }

    fun prepareEarth(): ShaderInstance {
        val result = prepare(earthDirection(), earthSunRadiance())
        prepareAtmosphere(result)
        result.getUniform("AtmosphereEnabled")?.set(1f)
        return result
    }

    fun prepare(
        direction: Vector3,
        radiance: Vector3 = Vector3(1.0, 1.0, 1.0),
    ): ShaderInstance {
        val result = checkNotNull(shader)
        result.getUniform("SunDirection")?.set(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
        result.getUniform("EclipseEnabled")?.set(0f)
        result.getUniform("AtmosphereEnabled")?.set(0f)
        vector(result, "SunRadiance", radiance)
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
        val result =
            prepare(
                moon.orientation.inverse((sun - moon.origin.position).normalized()),
                sunRadiance(profile, (sun - moon.origin.position).length()),
            )
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
