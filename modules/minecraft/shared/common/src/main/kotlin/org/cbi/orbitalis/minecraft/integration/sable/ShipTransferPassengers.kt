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

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NumericTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import java.util.UUID

/** Bounded native entity snapshots with an explicit mount graph; players retain native player data. */
object ShipTransferPassengers {
    data class Manifest(
        val entities: Set<Entity>,
        val data: ListTag,
    )

    /** The same read-only participant validation is used by guidance and the actual handoff. */
    fun preflight(
        body: ServerSubLevel,
        pilot: ServerPlayer?,
    ): Manifest {
        val travelling = nearby(body).toMutableSet()
        pilot?.let { travelling.add(it) }
        require(travelling.filterIsInstance<ServerPlayer>().all { it === pilot }) { "Select the travelling player" }
        val passengers = capture(body, travelling)
        SableShipSnapshot.preflight(body, pilot, ids(passengers))
        return Manifest(travelling.toSet(), passengers)
    }

    fun nearby(body: ServerSubLevel): Set<Entity> {
        val b = body.boundingBox()
        val area = AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY() + 3.0, b.maxZ()).inflate(1.0)
        val result =
            body.level
                .getEntities(null, area)
                .filter { !SableShipSnapshot.isSupportedAttachment(body, it) }
                .toMutableSet()
        result.addAll(
            body.level.getAllEntities().filter {
                body.plot.contains(it.position()) &&
                    !SableShipSnapshot.isSupportedAttachment(body, it)
            },
        )
        for (entity in result.toList()) {
            val root = entity.rootVehicle
            result.add(root)
            result.addAll(root.indirectPassengers.toList())
        }
        require(result.size <= 32) { "At most 32 travelling entities are supported" }
        require(result.filterIsInstance<ServerPlayer>().size <= 1) { "Only one player per transfer is currently supported" }
        return result
    }

    fun capture(
        body: ServerSubLevel,
        entities: Set<Entity>,
    ): ListTag =
        ListTag().also { list ->
            for (entity in entities.sortedBy { it.uuid.toString() }) {
                require(entity.isAlive && !entity.isRemoved) { "Travelling entity is unavailable" }
                val containing =
                    dev.ryanhcode.sable.Sable.HELPER
                        .getContaining(entity)
                val tracked =
                    dev.ryanhcode.sable.Sable.HELPER
                        .getTrackingSubLevel(entity.rootVehicle)
                require((containing == null || containing === body) && (tracked == null || tracked === body)) {
                    "Travelling entity belongs to another construction"
                }
                val entry = CompoundTag()
                entry.putUUID("uuid", entity.uuid)
                entry.putBoolean("player", entity is ServerPlayer)
                entry.putBoolean("plot", body.plot.contains(entity.position()))
                if (entity.vehicle != null) entry.putUUID("vehicle", entity.vehicle!!.uuid)
                if (entity is ServerPlayer) {
                    entry.put("state", SableShipTransfer.playerState(entity))
                } else {
                    val data = CompoundTag()
                    require(entity.saveAsPassenger(data)) { "Entity cannot be serialized for travel" }
                    data.remove("Passengers")
                    entry.put("data", data)
                }
                list.add(entry)
            }
            validate(list)
        }

    fun ids(list: ListTag): Set<UUID> = list.map { (it as CompoundTag).getUUID("uuid") }.toSet()

    fun validate(
        list: ListTag,
        receipt: Boolean = false,
    ) {
        require(list.size <= 32 && (list.isEmpty() || list.elementType.toInt() == 10)) { "Invalid travelling entity list" }
        val ids = ids(list)
        require(ids.size == list.size) { "Duplicate travelling UUID" }
        require(list.count { (it as CompoundTag).getBoolean("player") } <= 1) { "Too many travelling players" }
        val parents = mutableMapOf<UUID, UUID>()
        for (item in list) {
            val e = item as CompoundTag
            require(e.contains("player", 1) && e.contains("plot", 1)) { "Missing traveller coordinate/type ownership" }
            require(!e.contains("vehicle") || e.hasUUID("vehicle")) { "Invalid vehicle identity" }
            if (e.hasUUID("vehicle")) {
                val parent = e.getUUID("vehicle")
                require(parent in ids && parent != e.getUUID("uuid")) { "Invalid mount reference" }
                parents[e.getUUID("uuid")] = parent
            }
            if (e.getBoolean("player")) {
                val s = e.getCompound("state")
                for (k in listOf("x", "y", "z", "vx", "vy", "vz", "yaw", "pitch")) {
                    require(s.contains(k, 99) && s.getDouble(k).isFinite()) {
                        "Invalid player state"
                    }
                }
            } else if (!receipt) {
                val d = e.getCompound("data")
                require(
                    d.hasUUID("UUID") && d.getUUID("UUID") == e.getUUID("uuid") && d.getString("id").isNotEmpty() &&
                        !d.contains("Passengers"),
                ) { "Invalid entity payload" }
                for ((key, size) in listOf("Pos" to 3, "Motion" to 3, "Rotation" to 2)) {
                    val values = d.getList(key, if (key == "Rotation") 5 else 6)
                    require(
                        values.size == size && values.all { (it as NumericTag).asDouble.isFinite() },
                    ) { "Invalid entity vectors" }
                }
            }
        }
        for (id in ids) {
            val seen = mutableSetOf<UUID>()
            var current: UUID? = id
            while (current != null) {
                require(seen.add(current)) { "Cyclic mount graph" }
                current = parents[current]
            }
        }
    }

    fun translated(
        list: ListTag,
        source: CompoundTag,
        destination: CompoundTag,
    ): ListTag {
        val from =
            SableNBTUtils
                .readPose3d(source.getCompound("pose"))
        val to =
            SableNBTUtils
                .readPose3d(destination.getCompound("pose"))
        val local = Vector3d(to.rotationPoint()).sub(from.rotationPoint())
        val dy = to.position().y() - from.position().y()
        return list.copy().also { copy ->
            for (item in copy) {
                val entry = item as CompoundTag
                if (entry.getBoolean("player")) {
                    val state = entry.getCompound("state")
                    state.putDouble("y", state.getDouble("y") + dy)
                } else {
                    val pos = entry.getCompound("data").getList("Pos", 6)
                    // Plot entities have their own absolute local coordinates; world entities follow the frame.
                    val inPlot = entry.getBoolean("plot")
                    pos[0] = DoubleTag.valueOf(pos.getDouble(0) + if (inPlot) local.x else 0.0)
                    pos[1] = DoubleTag.valueOf(pos.getDouble(1) + if (inPlot) 0.0 else dy)
                    pos[2] = DoubleTag.valueOf(pos.getDouble(2) + if (inPlot) local.z else 0.0)
                }
            }
        }
    }

    fun unchanged(
        level: ServerLevel,
        list: ListTag,
    ): Boolean =
        list.all { item ->
            val entry = item as CompoundTag
            if (entry.getBoolean("player")) {
                true
            } else {
                val entity = level.getEntity(entry.getUUID("uuid"))
                if (entity == null || entity.isRemoved) {
                    false
                } else {
                    val current = CompoundTag()
                    entity.saveAsPassenger(current) && current.also { it.remove("Passengers") } == entry.getCompound("data")
                }
            }
        }

    fun discard(
        level: ServerLevel,
        list: ListTag,
    ) {
        for (entry in list) {
            val e = entry as CompoundTag
            if (!e.getBoolean("player")) level.getEntity(e.getUUID("uuid"))?.remove(Entity.RemovalReason.CHANGED_DIMENSION)
        }
    }

    fun restore(
        level: ServerLevel,
        list: ListTag,
    ) {
        for (entry in list) {
            val e = entry as CompoundTag
            if (e.getBoolean("player")) continue
            require(level.getEntity(e.getUUID("uuid")) == null) { "Travelling UUID already exists" }
            val entity =
                checkNotNull(
                    EntityType.loadEntityRecursive(e.getCompound("data").copy(), level) { it },
                ) { "Unknown travelling entity type" }
            check(level.addFreshEntity(entity)) { "Travelling entity could not be added" }
        }
    }

    fun ready(
        level: ServerLevel,
        list: ListTag,
    ): Boolean =
        list.all { item ->
            val entry = item as CompoundTag
            entry.getBoolean("player") || level.getEntity(entry.getUUID("uuid")) != null
        }

    fun mount(
        level: ServerLevel,
        list: ListTag,
        pilot: ServerPlayer?,
    ) {
        for (entry in list) {
            val e = entry as CompoundTag
            if (!e.hasUUID("vehicle")) continue
            val rider = if (e.getBoolean("player")) pilot else level.getEntity(e.getUUID("uuid"))
            val vehicle = checkNotNull(level.getEntity(e.getUUID("vehicle"))) { "Travelling vehicle missing" }
            if (rider != null) check(rider.vehicle === vehicle || rider.startRiding(vehicle, true)) { "Could not restore passenger seat" }
        }
    }
}
