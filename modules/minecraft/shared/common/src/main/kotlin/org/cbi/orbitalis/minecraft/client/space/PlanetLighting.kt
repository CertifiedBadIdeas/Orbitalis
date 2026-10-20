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
        return result
    }
}
