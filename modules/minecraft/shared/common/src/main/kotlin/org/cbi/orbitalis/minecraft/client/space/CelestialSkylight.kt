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

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlightAtmosphere
import org.cbi.orbitalis.minecraft.lighting.SolarLightScene
import kotlin.math.roundToInt

/** Dynamic external skylight only: never rewrites stored plot light, block light or terrain meshes. */
object CelestialSkylight {
    // Installed only when the optional Sable client is present. Plot coordinates are not physical points.
    var isPlot: (ClientLevel, BlockPos) -> Boolean = { _, _ -> false }
    private val local = ThreadLocal.withInitial { Cache() }

    private class Cache {
        var level: ClientLevel? = null
        var time = Long.MIN_VALUE
        var profile: org.cbi.orbitalis.core.orbit.SolarSystemProfile? = null
        var flight: CelestialFlight? = null
        var scene: SolarLightScene? = null
        val values = HashMap<BlockPos, Double>()
    }

    /** Chunk skylight has no storage below the world floor or above its ceiling, unlike physical space. */
    @JvmStatic
    fun outsideLightStorage(
        level: ClientLevel,
        position: BlockPos,
    ): Boolean =
        CelestialFlight.supported(level.dimension()) && !isPlot(level, position) &&
            (position.y < level.minBuildHeight || position.y >= level.maxBuildHeight)

    @JvmStatic
    fun scale(
        level: ClientLevel,
        position: BlockPos,
        native: Int,
    ): Int {
        if (native <= 0 || !CelestialFlight.available(level) || isPlot(level, position)) return native
        val dimension = level.dimension()
        if (!CelestialFlight.supported(dimension)) return native
        val profile = CelestialFlight.clientProfileSnapshot ?: return native
        val cache = local.get()
        if (cache.level !== level || cache.time != level.gameTime || cache.profile !== profile) {
            cache.level = level
            cache.time = level.gameTime
            cache.profile = profile
            cache.flight = CelestialFlight.sampleClient(0f)
            val frames = checkNotNull(cache.flight).frames
            cache.scene = SolarLightScene(profile, frames.tick, frames.seconds)
            cache.values.clear()
        }
        val p = Vector3(position.x + 0.5, position.y + 0.5, position.z + 0.5)
        // Preserve ordinary surface lighting; fade towards the same physical field before the handoff.
        val weight = if (dimension == Level.OVERWORLD) FlightAtmosphere.vacuum(p, profile.geometry).toDouble() else 1.0
        if (weight <= 0.0) return native
        // Packed BlockPos longs truncate Y; orbital sectors deliberately use heights near 1M.
        val key = position.immutable()
        val visibility =
            cache.values[key] ?: run {
                val root = checkNotNull(cache.flight).rootPoint(p, dimension)
                val value = checkNotNull(cache.scene).visibility(root)
                if (cache.values.size >= 4096) cache.values.clear()
                cache.values[key] = value
                value
            }
        return (native * (1.0 - weight + visibility * weight)).roundToInt().coerceIn(0, native)
    }

    fun clear() = local.remove()
}
