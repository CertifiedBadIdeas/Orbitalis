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

package org.cbi.orbitalis.minecraft.integration.sable

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.minecraft.space.SpaceWorlds

/** Use Sable's native object/object collision pipeline without terrain in new vacuum worlds. */
object SableVacuumWorld {
    private val originalPolicies = mutableMapOf<MinecraftServer, DimensionPhysics>()

    fun installPlotLookup() {
        SpaceWorlds.isShipPlot = { level, pos -> SubLevelContainer.getContainer(level)?.getPlot(ChunkPos(pos)) != null }
    }

    /** Native dimension configuration is static: never leak this world policy into the next integrated server. */
    fun close(server: MinecraftServer) {
        val previous = originalPolicies.remove(server) ?: return
        DimensionPhysicsData.putPhysics(org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE, previous)
    }

    fun configure(level: ServerLevel) {
        if (!SpaceWorlds.objects(level)) return
        val previous = DimensionPhysicsData.of(level)
        if (previous.ignoreChunks()) return
        originalPolicies[level.server] = previous
        DimensionPhysicsData.putPhysics(
            level.dimension(),
            DimensionPhysics(
                previous.dimension(),
                previous.priority(),
                previous.universalDrag(),
                previous.baseGravity(),
                previous.basePressure(),
                previous.pressureFunction(),
                previous.magneticNorth(),
                true,
            ),
        )
    }
}
