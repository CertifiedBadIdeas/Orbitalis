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

import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightAtmosphere
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates

object FlightSky {
    fun weight(
        level: ClientLevel,
        camera: Camera,
    ): Float {
        if (level.dimension() == FlatPlanetSpace.SPACE || level.dimension() == org.cbi.orbitalis.minecraft.moon.MoonWorld.KEY) return 1f
        if (level.dimension() != Level.OVERWORLD) return 0f
        val p = camera.position
        return FlightAtmosphere.vacuum(FlightSpaceCoordinates.globalPosition(level, Vector3(p.x, p.y, p.z)))
    }
}
