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

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.CoastPrediction
import org.cbi.orbitalis.core.planet.Vector3

/** Version-two clientbound, bounded and validated snapshot; null explicitly clears the path. */
data class TrajectoryPayload(
    val snapshot: TrajectorySnapshot?,
    val marker: Vector3? = null,
) : CustomPacketPayload {
    init {
        marker?.let(NavigationBookmarks::validate)
    }

    override fun type(): CustomPacketPayload.Type<TrajectoryPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TrajectoryPayload>(ResourceLocation.fromNamespaceAndPath("orbitalis", "coast_trajectory"))
        val CODEC: StreamCodec<FriendlyByteBuf, TrajectoryPayload> =
            object : StreamCodec<FriendlyByteBuf, TrajectoryPayload> {
                override fun encode(
                    buffer: FriendlyByteBuf,
                    value: TrajectoryPayload,
                ) {
                    buffer.writeBoolean(value.marker != null)
                    value.marker?.let { writeVector(buffer, it) }
                    val snapshot = value.snapshot
                    buffer.writeBoolean(snapshot != null)
                    if (snapshot == null) return
                    buffer.writeUUID(snapshot.ship)
                    buffer.writeResourceLocation(snapshot.dimension)
                    buffer.writeLong(snapshot.sampledTick)
                    buffer.writeVarInt(snapshot.prediction.end.ordinal)
                    buffer.writeVarInt(snapshot.prediction.points.size)
                    for (point in snapshot.prediction.points) {
                        buffer.writeDouble(point.seconds)
                        writeVector(buffer, point.state.position)
                        writeVector(buffer, point.state.velocity)
                    }
                }

                override fun decode(buffer: FriendlyByteBuf): TrajectoryPayload {
                    val marker = if (buffer.readBoolean()) readVector(buffer) else null
                    if (!buffer.readBoolean()) return TrajectoryPayload(null, marker)
                    val ship = buffer.readUUID()
                    val dimension = buffer.readResourceLocation()
                    val tick = buffer.readLong()
                    val end = buffer.readVarInt()
                    require(end in CoastPrediction.End.entries.indices) { "Invalid coast terminal event" }
                    val count = buffer.readVarInt()
                    require(count in 1..602) { "Invalid coast point count" }
                    val points =
                        List(count) {
                            CoastPrediction.Point(buffer.readDouble(), CartesianState(readVector(buffer), readVector(buffer)))
                        }
                    return TrajectoryPayload(
                        TrajectorySnapshot(ship, dimension, tick, CoastPrediction.Result(points, CoastPrediction.End.entries[end])),
                        marker,
                    )
                }
            }

        private fun writeVector(
            buffer: FriendlyByteBuf,
            vector: Vector3,
        ) {
            buffer.writeDouble(vector.x)
            buffer.writeDouble(vector.y)
            buffer.writeDouble(vector.z)
        }

        private fun readVector(buffer: FriendlyByteBuf) = Vector3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
    }
}

/** Installed on the physical client only; dedicated-server registration never resolves client classes. */
object TrajectoryReceiver {
    var accept: (TrajectoryPayload) -> Unit = {}
}
