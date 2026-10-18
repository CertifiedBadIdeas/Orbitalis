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

import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.joml.Matrix3d
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Apply a single frozen frame sample to every physical part of a durable native snapshot. */
class ShipFrameTransform(
    val sample: CelestialFlight,
    val from: ResourceKey<Level>,
    val to: ResourceKey<Level>,
) {
    private fun vec(value: org.joml.Vector3dc) = Vector3(value.x(), value.y(), value.z())

    private fun native(value: Vector3) = Vector3d(value.x, value.y, value.z)

    private val rotation: Quaterniond
        get() {
            val x = sample.direction(Vector3(1.0, 0.0, 0.0), from, to)
            val y = sample.direction(Vector3(0.0, 1.0, 0.0), from, to)
            val z = sample.direction(Vector3(0.0, 0.0, 1.0), from, to)
            return Quaterniond().setFromNormalized(Matrix3d(native(x), native(y), native(z)))
        }

    fun ship(
        snapshot: CompoundTag,
        sourceCentre: Vector3,
    ): CompoundTag =
        snapshot.copy().also { tag ->
            val pose = SableNBTUtils.readPose3d(tag.getCompound("pose"))
            val p = sample.point(vec(pose.position()), from, to)
            pose.position().set(p.x, p.y, p.z)
            pose.orientation().premul(rotation)
            tag.put("pose", SableNBTUtils.writePose3d(pose))
            val velocity = vec(SableNBTUtils.readVector3d(tag.getCompound("exact_linear")))
            val angular = vec(SableNBTUtils.readVector3d(tag.getCompound("exact_angular")))
            val state = sample.state(CartesianState(sourceCentre, velocity), from, to)
            tag.put("exact_linear", SableNBTUtils.writeVector3d(native(state.velocity)))
            tag.put("exact_angular", SableNBTUtils.writeVector3d(native(sample.angular(angular, from, to))))
            val b = SableNBTUtils.readBoundingBox(tag.getCompound("world_bounds"))
            val corners =
                listOf(b.minX(), b.maxX()).flatMap { x ->
                    listOf(b.minY(), b.maxY()).flatMap { y ->
                        listOf(b.minZ(), b.maxZ()).map { z -> sample.point(Vector3(x, y, z), from, to) }
                    }
                }
            tag.put(
                "world_bounds",
                SableNBTUtils.writeBoundingBox(
                    dev.ryanhcode.sable.companion.math.BoundingBox3d(
                        corners.minOf { it.x },
                        corners.minOf { it.y },
                        corners.minOf { it.z },
                        corners.maxOf { it.x },
                        corners.maxOf { it.y },
                        corners.maxOf { it.z },
                    ),
                ),
            )
        }

    fun player(state: CompoundTag): CompoundTag =
        state.copy().also { tag ->
            if (tag.isEmpty) return@also
            val source =
                CartesianState(
                    Vector3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                    Vector3(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz")) * 20.0,
                )
            val result = sample.state(source, from, to)
            for ((key, value) in listOf(
                "x" to result.position.x,
                "y" to result.position.y,
                "z" to result.position.z,
                "vx" to result.velocity.x / 20.0,
                "vy" to result.velocity.y / 20.0,
                "vz" to result.velocity.z / 20.0,
            )) {
                tag.putDouble(key, value)
            }
            val (yaw, pitch) = look(tag.getDouble("yaw"), tag.getDouble("pitch"))
            tag.putDouble("yaw", yaw)
            tag.putDouble("pitch", pitch)
        }

    fun passengers(
        list: ListTag,
        source: CompoundTag,
        destination: CompoundTag,
    ): ListTag {
        val rebased = ShipTransferPassengers.translated(list, source, destination)
        val sourcePose = SableNBTUtils.readPose3d(source.getCompound("pose"))
        val destinationPose = SableNBTUtils.readPose3d(destination.getCompound("pose"))
        val plotDelta = Vector3d(destinationPose.rotationPoint()).sub(sourcePose.rotationPoint())
        for (index in list.indices) {
            val old = list[index] as CompoundTag
            val entry = rebased[index] as CompoundTag
            if (old.getBoolean("player")) {
                val original = old.getCompound("state")
                val moved =
                    if (old.getBoolean("plot")) {
                        original.copy().also { state ->
                            state.putDouble("x", state.getDouble("x") + plotDelta.x)
                            state.putDouble("z", state.getDouble("z") + plotDelta.z)
                        }
                    } else {
                        player(original)
                    }
                entry.put("state", moved)
            } else if (!old.getBoolean("plot")) {
                val data = entry.getCompound("data")
                val original = old.getCompound("data")
                val pos = original.getList("Pos", 6)
                val motion = original.getList("Motion", 6)
                val result =
                    sample.state(
                        CartesianState(
                            Vector3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)),
                            Vector3(motion.getDouble(0), motion.getDouble(1), motion.getDouble(2)) * 20.0,
                        ),
                        from,
                        to,
                    )
                data.put(
                    "Pos",
                    ListTag().also {
                        it.add(DoubleTag.valueOf(result.position.x))
                        it.add(DoubleTag.valueOf(result.position.y))
                        it.add(DoubleTag.valueOf(result.position.z))
                    },
                )
                data.put(
                    "Motion",
                    ListTag().also {
                        it.add(DoubleTag.valueOf(result.velocity.x / 20.0))
                        it.add(
                            DoubleTag.valueOf(result.velocity.y / 20.0),
                        )
                        it.add(DoubleTag.valueOf(result.velocity.z / 20.0))
                    },
                )
                val angles = original.getList("Rotation", 5)
                val (yaw, pitch) = look(angles.getFloat(0).toDouble(), angles.getFloat(1).toDouble())
                data.put(
                    "Rotation",
                    ListTag().also {
                        it.add(FloatTag.valueOf(yaw.toFloat()))
                        it.add(FloatTag.valueOf(pitch.toFloat()))
                    },
                )
            }
        }
        return rebased
    }

    private fun look(
        yaw: Double,
        pitch: Double,
    ): Pair<Double, Double> {
        val y = Math.toRadians(yaw)
        val p = Math.toRadians(pitch)
        val direction = sample.direction(Vector3(-sin(y) * cos(p), -sin(p), cos(y) * cos(p)), from, to)
        return Math.toDegrees(atan2(-direction.x, direction.z)) to Math.toDegrees(asin((-direction.y).coerceIn(-1.0, 1.0)))
    }
}
