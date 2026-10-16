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
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.cbi.orbitalis.core.planet.OrbitalTestFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
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
            check(comparablePayload(payload) == comparablePayload(restored)) {
                val changed = (payload.allKeys + restored.allKeys).sorted().filter { payload.get(it) != restored.get(it) }
                "Block entity payload changed during reconstruction: ${payload.getString("id")} at $position; fields: " +
                    changed.joinToString(", ")
            }
        }
    }

    private fun comparablePayload(payload: CompoundTag): CompoundTag {
        if (payload.getString("id") != "createpropulsion:creative_thruster_block_entity") return payload
        // Propulsion 1.1.5 read() re-arms this connectivity work flag for single engines.
        // Keep it armed on the live destination: its first resumed tick must rebuild links.
        // It is not cargo or engine configuration; all other fields remain exact.
        check(
            !payload.contains("UpdateConnectivity") ||
                (payload.contains("UpdateConnectivity", 1) && payload.getByte("UpdateConnectivity") == 1.toByte()),
        ) { "Invalid creative thruster connectivity work flag" }
        return payload.copy().also { it.remove("UpdateConnectivity") }
    }

    fun preflight(
        body: ServerSubLevel,
        pilot: ServerPlayer,
    ) {
        require(pilot.isCreative && !pilot.isPassenger && !pilot.isVehicle) { "Use an unmounted creative pilot" }
        require(body.massTracker.centerOfMass != null && body.massTracker.mass > 0.0) { "Wait for native ship initialization" }
        require(SubLevelHelper.getLoadingDependencyChain(body).size == 1 && SubLevelHelper.getConnectedChain(body).size == 1) {
            "Connected or overlapping constructions are not supported by this diagnostic"
        }
        require(body.plot.contraptions.isEmpty()) { "Kinematic contraptions are not supported" }
        require(body.plot.loadedChunks.size <= 4 && body.plot.boundingBox.volume() <= 4096) {
            "Diagnostic ship exceeds four plot chunks or 4096 blocks of bounds"
        }
        val bounds = body.boundingBox()
        val area = AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY() + 3.0, bounds.maxZ()).inflate(1.0)
        require(area.contains(pilot.position())) { "The pilot must stand on the selected ship" }
        // Sable's spatial query also projects plot-space entities into the ship's world bounds.
        // Its own glue is transferred with the plot; nearby world-space glue is still an obstruction.
        val obstruction = body.level.getEntities(pilot, area).firstOrNull { !isSupportedAttachment(body, it) }
        require(obstruction == null) {
            "Remove other players, mobs and loose entities from the ship: ${BuiltInRegistries.ENTITY_TYPE.getKey(obstruction!!.type)}"
        }
        require(
            body.level.getAllEntities().filter { it !== pilot && body.plot.contains(it.position()) }.all {
                isSupportedAttachment(body, it)
            },
        ) { "Only Create Super Glue and Simulated Honey Glue metadata entities are supported inside the plot" }
        require(entitiesReady(body)) { "Wait for native plot entities to finish loading" }
        val unsupported =
            body.plot.loadedChunks
                .flatMap { it.chunk.blockEntities.values }
                .map { BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(it.type).toString() }
                .filter { it !in SUPPORTED_BLOCK_ENTITIES }
                .groupingBy { it }
                .eachCount()
                .toSortedMap()
        require(unsupported.isEmpty()) {
            "Unsupported block entities (${unsupported.values.sum()}): " +
                unsupported.entries.joinToString(", ") { (id, count) -> "$id × $count" }
        }
    }

    fun capture(body: ServerSubLevel): CompoundTag {
        val tag = SubLevelSerializer.toData(body, emptyList()).fullTag().copy()
        val handle = SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body)
        tag.put("exact_linear", SableNBTUtils.writeVector3d(handle.getLinearVelocity(Vector3d())))
        tag.put("exact_angular", SableNBTUtils.writeVector3d(handle.getAngularVelocity(Vector3d())))
        val attached = ListTag()
        val entities =
            body.level
                .getAllEntities()
                .filter { body.plot.contains(it.position()) }
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
            val dimension = target ?: if (delta < 0) FlightTestSpace.ORBIT else FlightTestSpace.SURFACE
            val physical = FlightTestSpace.globalPosition(dimension, Vector3(pose.position().x, pose.position().y, pose.position().z))
            OrbitalTestFrame.requirePatch(physical)
            require(
                pose.position().y >= 64.0 &&
                    (if (dimension == FlightTestSpace.SPACE) physical.y in 64.0..16000.0 else pose.position().y <= 16000.0),
            ) {
                "Transfer outside the diagnostic top-face altitude range; legacy orbit requires surface y>=320"
            }
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
        check(handle.getLinearVelocity(Vector3d()).distance(linear) < 1e-8) { "Linear velocity was not restored" }
        check(handle.getAngularVelocity(Vector3d()).distance(angular) < 1e-8) { "Angular velocity was not restored" }
        return body
    }

    fun entitiesReady(body: ServerSubLevel): Boolean = body.plot.loadedChunks.all { body.level.areEntitiesLoaded(it.chunk.pos.toLong()) }

    private fun isSupportedAttachment(
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

    private val SUPPORTED_BLOCK_ENTITIES =
        setOf(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:sign",
            "minecraft:hanging_sign",
            "create:redstone_link",
            "createpropulsion:creative_thruster_block_entity",
            "simulated:physics_assembler",
        )
}
