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
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.lighting.SkyLighting

/** Tint native clouds with the same finite atmospheric Sun column as the orbital cloud layer. */
object SurfaceCloudColor {
    @JvmStatic fun modify(
        level: ClientLevel,
        partialTick: Float,
        native: Vec3,
    ): Vec3 {
        if (!SkyLighting.physicalOverworld(level)) return native
        val height = level.effects().cloudHeight.toDouble()
        if (!height.isFinite()) return native
        val camera =
            Minecraft
                .getInstance()
                .gameRenderer.mainCamera.position
        val point = Vector3(camera.x, height, camera.z)
        val sample = CelestialFlight.sampleClient(partialTick)
        val profile = sample.frames.profile
        val sun = SkyLighting.sunPosition(profile, sample.frames.tick, sample.frames.seconds)
        val direction = (sample.nativePoint(sun, Level.OVERWORLD) - point).normalized()
        val half = profile.geometry.earthHalfSize
        val (bottom, top) = PlanetLimbRenderer.atmosphereBounds(half, height)
        val transmission =
            AtmosphereOptics.sunTransmission(
                point,
                direction,
                half,
                bottom.y,
                top.y,
                PlanetLimbRenderer.atmosphereFeather(half),
            )
        val color = tint(Vector3(native.x, native.y, native.z), transmission, direction.y)
        return Vec3(color.x, color.y, color.z)
    }

    /** Keep native weather/daylight intensity; normalized absorption can only attenuate channels. */
    internal fun tint(
        native: Vector3,
        transmission: Vector3,
        sunElevation: Double,
    ): Vector3 {
        require(sunElevation.isFinite() && sunElevation in -1.0..1.0)
        require(listOf(transmission.x, transmission.y, transmission.z).all { it in 0.0..1.0 })
        val peak = maxOf(transmission.x, transmission.y, transmission.z)
        if (peak == 0.0) return native // Slab occlusion is not a source of warm ambient light.
        val twilight = 1.0 - smooth(0.12, 0.35, sunElevation)
        val belowHorizon = smooth(-0.16, 0.02, sunElevation)
        // Do not amplify the chroma of an almost extinguished direct ray at the shadow boundary.
        val weight = twilight * belowHorizon * smooth(0.0, 0.12, peak)
        if (weight == 0.0) return native

        fun factor(value: Double) = 1.0 - weight + weight * value / peak
        return Vector3(native.x * factor(transmission.x), native.y * factor(transmission.y), native.z * factor(transmission.z))
    }

    private fun smooth(
        low: Double,
        high: Double,
        value: Double,
    ): Double = ((value - low) / (high - low)).coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
}
