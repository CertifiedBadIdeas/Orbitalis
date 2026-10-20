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

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.FlatPlanetFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.map.MapObject
import org.cbi.orbitalis.minecraft.map.OrbitalMapHooks
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData
import org.joml.Vector3d

object SableMapObjects {
    fun install() {
        OrbitalMapHooks.objects = ::snapshot
    }

    fun snapshot(server: MinecraftServer): List<MapObject> {
        val indices =
            server.allLevels
                .filter { FlightSpaceCoordinates.isOrbital(it.dimension()) }
                .mapNotNull { it.dataStorage.get(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME) }
        val sample = CelestialFlight.trySample(server)
        val live = SableShipDirectory.loaded(server)
        val liveIds = live.map { it.uniqueId }.toSet()
        val result = mutableListOf<MapObject>()
        // Read the existing motion index only. Do not open a holding chunk or create an orbital service.
        for (data in indices.filter { it.readable }) {
            for (record in data.records.values) {
                if (record.id !in
                    liveIds
                ) {
                    result.add(MapObject(record.id, "", record.state.position, record.state.velocity, false, record.tick))
                }
            }
        }
        for (body in live) {
            val centre = body.massTracker.centerOfMass ?: continue
            if (body.massTracker.mass <= 0.0) continue
            val native = SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body)
            val worldPosition = body.logicalPose().transformPosition(centre, Vector3d())
            if (!worldPosition.isFinite) continue
            val nativePosition = Vector3(worldPosition.x, worldPosition.y, worldPosition.z)
            val v = native.getLinearVelocity(Vector3d())
            if (!v.isFinite) continue
            val state =
                sample?.rootState(CartesianState(nativePosition, Vector3(v.x, v.y, v.z)), body.level.dimension())
                    ?: CartesianState(FlightSpaceCoordinates.globalPosition(body.level, nativePosition), Vector3(v.x, v.y, v.z))
            val pos = state.position
            if (pos.length() > 1e8 || v.length() > 10000.0) continue
            val name =
                body.name
                    .orEmpty()
                    .filterNot { it.isISOControl() }
                    .take(32)
            result.add(MapObject(body.uniqueId, name, pos, state.velocity, true, sample?.frames?.tick ?: 0L))
        }
        return result
    }
}
