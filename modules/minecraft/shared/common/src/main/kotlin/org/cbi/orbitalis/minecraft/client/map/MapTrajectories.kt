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

package org.cbi.orbitalis.minecraft.client.map

import net.minecraft.Util
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.FlatPlanetDefinition
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.map.MapObject
import org.cbi.orbitalis.minecraft.map.MapSnapshot
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Screen-local, immutable numerical previews. Never touches levels, native ships or loading APIs. */
class MapTrajectories : AutoCloseable {
    data class Path(
        val source: MapObject,
        val prediction: CoastPrediction.Result,
    )

    var paths: List<Path> = emptyList()
        private set
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Orbitalis map trajectories").apply { isDaemon = true } }
    private var pending: Future<List<Path>>? = null
    private var ids = emptyList<UUID>()
    private var gravity = Double.NaN
    private var requested = Long.MIN_VALUE

    fun update(
        data: MapSnapshot?,
        selected: UUID?,
        focus: Vector3,
    ) {
        if (data == null) return
        val inputs =
            data.objects
                .filter { it.position.length() <= 100000.0 }
                .sortedWith(compareBy<MapObject> { it.id != selected }.thenBy { (it.position - focus).length() })
                .take(8)
        val nextIds = inputs.map { it.id }
        val gravityChanged = gravity != data.gravity
        if (nextIds.toSet() != ids.toSet() || nextIds.firstOrNull() != ids.firstOrNull() || gravityChanged) {
            pending?.cancel(true)
            pending = null
            ids = nextIds
            gravity = data.gravity
            paths = if (gravityChanged) emptyList() else paths.filter { it.source.id in ids }
            requested = Long.MIN_VALUE
        }
        pending?.takeIf { it.isDone }?.let { result ->
            pending = null
            try {
                paths = result.get()
            } catch (_: java.util.concurrent.CancellationException) {
            } catch (failure: java.util.concurrent.ExecutionException) {
                com.mojang.logging.LogUtils
                    .getLogger()
                    .warn("Map trajectory preview failed", failure.cause)
            }
        }
        val now = Util.getMillis()
        if (pending != null || inputs.isEmpty() || (requested != Long.MIN_VALUE && now - requested < 1000)) return
        requested = now
        val field = FlatPlanetDefinition.gravity(data.gravity)
        pending =
            worker.submit<List<Path>> {
                inputs.mapNotNull { item ->
                    try {
                        Path(
                            item,
                            CoastPrediction.orbit(
                                field,
                                CartesianState(item.position, item.velocity),
                                CoastPrediction.Limits(range = 100000.0),
                            ),
                        )
                    } catch (_: IllegalArgumentException) {
                        null // An out-of-envelope object remains selectable without inventing its orbit.
                    }
                }
            }
    }

    override fun close() {
        pending?.cancel(true)
        worker.shutdownNow()
        paths = emptyList()
    }
}
