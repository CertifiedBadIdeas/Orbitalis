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

package org.cbi.orbitalis.minecraft.navigation

import net.minecraft.resources.ResourceLocation
import org.cbi.orbitalis.core.planet.CoastPrediction
import java.util.UUID

/** Physical inertial coordinates in metres; never native plot or translated space coordinates. */
data class TrajectorySnapshot(
    val ship: UUID,
    val dimension: ResourceLocation,
    val sampledTick: Long,
    val prediction: CoastPrediction.Result,
) {
    init {
        require(dimension.toString() in setOf("minecraft:overworld", "orbitalis:space"))
        require(sampledTick >= 0 && prediction.points.size in 1..602)
        var previous = -1.0
        for (point in prediction.points) {
            require(point.seconds.isFinite() && point.seconds in 0.0..300.0 && point.seconds > previous)
            require(point.state.position.length() <= 100000.0 && point.state.velocity.length() <= 10000.0)
            previous = point.seconds
        }
        require(prediction.points.first().seconds == 0.0)
    }
}
