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

package org.cbi.orbitalis.minecraft.flight.control

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Vector3d
import org.joml.Vector3dc

object FlightBody {
    fun vector(v: Vector3dc) = Vector3(v.x(), v.y(), v.z())

    fun position(body: ServerSubLevel): Vector3 = vector(body.logicalPose().transformPosition(body.massTracker.centerOfMass, Vector3d()))
}
