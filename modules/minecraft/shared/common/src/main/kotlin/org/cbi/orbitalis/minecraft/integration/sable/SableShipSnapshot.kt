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

import dev.ryanhcode.sable.api.SubLevelHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.joml.Vector3d

/** Pinned native serialization stays behind this integration boundary; no upstream code is copied. */
object SableShipSnapshot {
    /** Native serialization iterates an unordered set of block-entity positions. */
    fun verifyBlockEntities(
        source: ListTag,
        destination: ListTag,
    ) {
        fun index(entries: ListTag): Map<BlockPos, CompoundTag> =
            buildMap {
                for (entry in entries) {
                    check(entry is CompoundTag && listOf("x", "y", "z").all { entry.contains(it, 3) }) {
                        "Invalid block entity coordinates during reconstruction"
                    }
                    val position = BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"))
                    check(put(position, entry) == null) { "Duplicate block entity at $position during reconstruction" }
                }
            }
        val expected = index(source)
        val actual = index(destination)
        check(expected.keys == actual.keys) { "Block entity positions changed during reconstruction" }
        for ((position, payload) in expected) {
            val restored = actual.getValue(position)
            check(
                NativeBlockEntityMetadata.forComparison(payloadForComparison(payload)) ==
                    NativeBlockEntityMetadata.forComparison(payloadForComparison(restored)),
            ) {
                val changed = (payload.allKeys + restored.allKeys).sorted().filter { payload.get(it) != restored.get(it) }
                "Block entity payload changed during reconstruction: ${payload.getString("id")} at $position; fields: " +
                    changed.joinToString(", ") +
                    if ("Tanks" in changed) {
                        "; source Tanks=${payload.get("Tanks")}; restored Tanks=${restored.get("Tanks")}"
                    } else {
                        ""
                    }
            }
        }
    }

    /** Internal optional adapter seam; identity preserves strict verification without engines. */
    var payloadForComparison: (CompoundTag) -> CompoundTag = { it }

    fun preflight(
        body: ServerSubLevel,
        pilot: ServerPlayer?,
        travellers: Set<java.util.UUID> = emptySet(),
    ) {
        require(body.massTracker.centerOfMass != null && body.massTracker.mass > 0.0) { "Wait for native ship initialization" }
        require(SubLevelHelper.getLoadingDependencyChain(body).size == 1 && SubLevelHelper.getConnectedChain(body).size == 1) {
            "Connected or overlapping constructions are not supported by this diagnostic"
        }
        require(body.plot.contraptions.isEmpty()) { "Kinematic contraptions are not supported" }
        require(SableShipChunks.occupied(body).size <= SableShipChunks.MAX_OCCUPIED_CHUNKS && body.plot.boundingBox.volume() <= 4096) {
            "Diagnostic ship exceeds nine occupied plot chunks or 4096 blocks of bounds"
        }
        val bounds = body.boundingBox()
        val area = AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY() + 3.0, bounds.maxZ()).inflate(1.0)
        require(
            pilot == null || area.contains(pilot.position()) || body.plot.contains(pilot.position()) || pilot.vehicle?.uuid in travellers,
        ) {
            "The pilot must be on the selected ship"
        }
        // Sable's spatial query also projects plot-space entities into the ship's world bounds.
        // Its own glue is transferred with the plot; nearby world-space glue is still an obstruction.
        val obstruction = body.level.getEntities(pilot, area).firstOrNull { it.uuid !in travellers && !isSupportedAttachment(body, it) }
        require(obstruction == null) {
            "Remove other players, mobs and loose entities from the ship: ${BuiltInRegistries.ENTITY_TYPE.getKey(obstruction!!.type)}"
        }
        require(
            body.level.getAllEntities().filter { it !== pilot && it.uuid !in travellers && body.plot.contains(it.position()) }.all {
                isSupportedAttachment(body, it)
            },
        ) { "Only Create Super Glue and Simulated Honey Glue metadata entities are supported inside the plot" }
        require(entitiesReady(body)) { "Wait for native plot entities to finish loading" }
        // Native serialization, registry identity and exact reconstruction are the compatibility
        // boundary. Block-entity type names are not a permission list.
        for (holder in body.plot.loadedChunks) {
            for (block in holder.chunk.blockEntities.values) {
                val data = block.saveWithFullMetadata(body.level.registryAccess())
                require(data.getString("id") == BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(block.type).toString()) {
                    "Block entity did not serialize its registry identity at ${block.blockPos}"
                }
            }
        }
    }

    fun capture(
        body: ServerSubLevel,
        travellers: Set<java.util.UUID> = emptySet(),
    ): CompoundTag {
        val tag = SubLevelSerializer.toData(body, emptyList()).fullTag().copy()
        val handle = SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body)
        tag.put("exact_linear", SableNBTUtils.writeVector3d(handle.getLinearVelocity(Vector3d())))
        tag.put("exact_angular", SableNBTUtils.writeVector3d(handle.getAngularVelocity(Vector3d())))
        val attached = ListTag()
        val entities =
            body.level
                .getAllEntities()
                .filter { it.uuid !in travellers && body.plot.contains(it.position()) }
                .sortedBy { it.uuid.toString() }
        require(entities.size <= 64) { "Too many attached metadata entities" }
        for (entity in entities) {
            require(
                isSupportedAttachment(body, entity),
            ) { "Unsupported plot entity" }
            val data = CompoundTag()
            require(entity.save(data)) { "Could not save attached glue" }
            attached.add(data)
        }
        tag.put("orbitalis_attached", attached)
        for (chunk in tag.getCompound("plot").getCompound("chunks").allKeys) {
            val data = tag.getCompound("plot").getCompound("chunks").getCompound(chunk)
            require(data.getList("block_ticks", 10).isEmpty() && data.getList("fluid_ticks", 10).isEmpty()) {
                "Pending block/fluid ticks are not supported by the first transfer diagnostic"
            }
        }
        SableShipChunks.compactCapture(body, tag)
        return tag
    }

    @JvmOverloads
    fun translated(
        snapshot: CompoundTag,
        delta: Double,
        target: ResourceKey<Level>? = null,
    ): CompoundTag =
        snapshot.copy().also { tag ->
            val pose = SableNBTUtils.readPose3d(tag.getCompound("pose"))
            pose.position().add(0.0, delta, 0.0)
            val dimension = target ?: if (delta > 0) FlatPlanetSpace.SPACE else net.minecraft.world.level.Level.OVERWORLD
            val physical =
                FlightSpaceCoordinates.legacyRootPosition(
                    dimension,
                    Vector3(pose.position().x, pose.position().y, pose.position().z),
                )
            require(FlatPlanetSpace.validTransferPosition(physical)) { "Transfer outside bounded physical flight coordinates" }
            tag.put("pose", SableNBTUtils.writePose3d(pose))
            val b = SableNBTUtils.readBoundingBox(tag.getCompound("world_bounds"))
            tag.put(
                "world_bounds",
                SableNBTUtils.writeBoundingBox(
                    BoundingBox3d(
                        b.minX(),
                        b.minY() + delta,
                        b.minZ(),
                        b.maxX(),
                        b.maxY() + delta,
                        b.maxZ(),
                    ),
                ),
            )
        }

    /** Native chunks are plot-relative; only the pivot and known absolute addresses move. */
    fun forDestination(
        snapshot: CompoundTag,
        source: ServerLevel,
        destination: ServerLevel,
    ): CompoundTag {
        val from = checkNotNull(SubLevelContainer.getContainer(source))
        val to = checkNotNull(SubLevelContainer.getContainer(destination))
        val plot = snapshot.getCompound("plot")
        require(plot.getInt("log_size") == from.logPlotSize && from.logPlotSize == to.logPlotSize) {
            "Incompatible native plot sizes"
        }
        val side = 1 shl to.logSideLength
        val oldX = plot.getInt("plot_x")
        val oldZ = plot.getInt("plot_z")
        require(oldX in 0 until (1 shl from.logSideLength) && oldZ in 0 until (1 shl from.logSideLength)) {
            "Invalid source native plot"
        }
        val index =
            if (oldX in 0 until side && oldZ in 0 until side && !to.occupancy[to.getIndex(oldX, oldZ)]) {
                to.getIndex(oldX, oldZ)
            } else {
                to.occupancy.nextClearBit(0)
            }
        require(index < side * side) { "No free destination plot; no source changes were made" }
        val x = index % side
        val z = index / side
        val shift = from.logPlotSize + 4
        val dx = (to.origin.x.toLong() + x - from.origin.x - oldX) shl shift
        val dz = (to.origin.y.toLong() + z - from.origin.y - oldZ) shl shift
        require(dx in Int.MIN_VALUE.toLong()..Int.MAX_VALUE && dz in Int.MIN_VALUE.toLong()..Int.MAX_VALUE) {
            "Native plot relocation exceeds block coordinates"
        }
        val copy = snapshot.copy()
        copy.getCompound("plot").putInt("plot_x", x)
        copy.getCompound("plot").putInt("plot_z", z)
        val pose = SableNBTUtils.readPose3d(copy.getCompound("pose"))
        // The world-space center stays fixed. Translation of both local blocks and rotation point
        // preserves the rigid transform even for a rotated craft and a nonzero angular velocity.
        pose.rotationPoint().add(dx.toDouble(), 0.0, dz.toDouble())
        copy.put("pose", SableNBTUtils.writePose3d(pose))
        for (key in copy.getCompound("plot").getCompound("chunks").allKeys) {
            val chunk = copy.getCompound("plot").getCompound("chunks").getCompound(key)
            for (tag in chunk.getList("block_entities", 10)) {
                val data = tag as CompoundTag
                // Create LinkBehaviour and Propulsion 1.1.5's four directional variants save
                // their owning block's absolute address. Frequencies and every other field stay exact.
                val linkPositions =
                    when (data.getString("id")) {
                        "create:redstone_link" -> listOf("LastKnownPosition")

                        "createpropulsion:creative_vector_thruster_block_entity",
                        "createpropulsion:vector_thruster_block_entity",
                        "createpropulsion:liquid_vector_thruster_block_entity",
                        -> listOf("DownLastKnownPosition", "UpLastKnownPosition", "WestLastKnownPosition", "EastLastKnownPosition")

                        else -> emptyList()
                    }
                for (field in linkPositions) {
                    require(data.contains(field, 4)) { "Missing native link position: $field" }
                    val previous = BlockPos.of(data.getLong(field))
                    data.putLong(field, previous.offset(dx.toInt(), 0, dz.toInt()).asLong())
                }
                data.putInt("x", Math.addExact(data.getInt("x"), dx.toInt()))
                data.putInt("z", Math.addExact(data.getInt("z"), dz.toInt()))
            }
        }
        for (tag in copy.getList("orbitalis_attached", 10)) {
            val data = tag as CompoundTag
            require(ShipTransferAttachments.supports(data.getString("id"))) { "Unsupported relocated entity" }
            val pos = data.getList("Pos", 6)
            require(pos.size == 3) { "Invalid attached entity position" }
            pos[0] = DoubleTag.valueOf(pos.getDouble(0) + dx)
            pos[2] = DoubleTag.valueOf(pos.getDouble(2) + dz)
            // Pinned Super Glue/Honey Glue From/To bounds are relative to Pos. Propulsion
            // ControllerOffX/Y/Z are relative to the BE. Their payloads must stay exact.
        }
        return copy
    }

    fun load(
        level: ServerLevel,
        snapshot: CompoundTag,
    ): ServerSubLevel {
        val tag = snapshot.copy()
        val linear = vector(tag, "exact_linear")
        val angular = vector(tag, "exact_angular")
        val pose = SableNBTUtils.readPose3d(tag.getCompound("pose"))
        require(pose.position().isFinite && pose.orientation().isFinite) { "Nonfinite ship pose" }
        tag.remove("linear_velocity")
        tag.remove("angular_velocity")
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        require(container.getSubLevel(tag.getUUID("uuid")) == null) { "Ship UUID already exists at destination" }
        val body =
            checkNotNull(
                SubLevelSerializer.fullyLoad(level, checkNotNull(SubLevelSerializer.fromData(tag))),
            ) { "Native ship reconstruction failed" }
        val physics = SubLevelPhysicsSystem.require(level)
        body.updateMergedMassData(1.0f)
        physics.pipeline.onStatsChanged(body)
        physics.pipeline.prePhysicsTicks()
        val handle = physics.getPhysicsHandle(body)
        handle.teleport(pose.position(), pose.orientation())
        physics.pipeline.resetVelocity(body)
        handle.addLinearAndAngularVelocity(linear, angular)
        physics.pipeline.prePhysicsTicks()
        body.updateLastPose()
        check(matchesNativeVelocity(handle.getLinearVelocity(Vector3d()), linear)) { "Linear velocity was not restored" }
        check(matchesNativeVelocity(handle.getAngularVelocity(Vector3d()), angular)) { "Angular velocity was not restored" }
        return body
    }

    /** Rapier's observed velocity is float precision. Accept only exact double or exact float rounding. */
    fun matchesNativeVelocity(
        actual: org.joml.Vector3dc,
        expected: org.joml.Vector3dc,
    ): Boolean =
        actual.distance(expected) < 1e-8 ||
            (
                actual.x() == expected.x().toFloat().toDouble() && actual.y() == expected.y().toFloat().toDouble() &&
                    actual.z() == expected.z().toFloat().toDouble()
            )

    fun entitiesReady(body: ServerSubLevel): Boolean = body.plot.loadedChunks.all { body.level.areEntitiesLoaded(it.chunk.pos.toLong()) }

    fun isSupportedAttachment(
        body: ServerSubLevel,
        entity: Entity,
    ): Boolean =
        body.plot.contains(entity.position()) &&
            ShipTransferAttachments.supports(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()) &&
            !entity.isVehicle && !entity.isPassenger

    fun discardAttachments(
        level: ServerLevel,
        snapshot: CompoundTag,
    ) {
        for (tag in snapshot.getList("orbitalis_attached", 10)) {
            val data = tag as CompoundTag
            require(ShipTransferAttachments.supports(data.getString("id"))) { "Unsupported attached entity" }
            level.getEntity(data.getUUID("UUID"))?.let { entity ->
                require(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() == data.getString("id"),
                ) { "Attached entity UUID conflict" }
                entity.remove(Entity.RemovalReason.DISCARDED)
            }
        }
    }

    fun restoreAttachments(
        body: ServerSubLevel,
        snapshot: CompoundTag,
    ) {
        discardAttachments(body.level, snapshot)
        for (tag in snapshot.getList("orbitalis_attached", 10)) {
            val data = (tag as CompoundTag).copy()
            require(ShipTransferAttachments.supports(data.getString("id"))) { "Unsupported attached entity" }
            require(!data.contains("Passengers")) { "Attached passengers are unsupported" }
            val entity =
                checkNotNull(EntityType.loadEntityRecursive(data, body.level) { it }) { "Could not reconstruct glue metadata" }
            check(body.plot.contains(entity.position()) && body.level.addFreshEntity(entity)) { "Could not attach glue metadata" }
        }
    }

    fun vector(
        tag: CompoundTag,
        key: String,
    ): Vector3d =
        SableNBTUtils.readVector3d(tag.getCompound(key)).also {
            require(it.isFinite) { "Nonfinite ship velocity" }
        }
}
