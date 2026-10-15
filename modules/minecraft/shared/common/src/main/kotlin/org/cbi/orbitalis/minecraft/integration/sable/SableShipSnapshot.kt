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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import org.cbi.orbitalis.core.planet.OrbitalTestFrame
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Vector3d

/** Pinned native serialization stays behind this integration boundary; no upstream code is copied. */
object SableShipSnapshot {
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
        require(body.level.getEntities(pilot, area).isEmpty()) { "Remove other players, mobs and loose entities from the ship" }
        require(
            body.level.getAllEntities().filter { it !== pilot && body.plot.contains(it.position()) }.all {
                BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() ==
                    "create:super_glue"
            },
        ) { "Only Create Super Glue metadata entities are supported inside the plot" }
        require(entitiesReady(body)) { "Wait for native plot entities to finish loading" }
        for (holder in body.plot.loadedChunks) {
            for (blockEntity in holder.chunk.blockEntities.values) {
                val id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type).toString()
                require(id in SUPPORTED_BLOCK_ENTITIES) { "Unsupported block entity: $id" }
            }
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
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() == "create:super_glue" && !entity.isVehicle &&
                    !entity.isPassenger,
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

    fun translated(
        snapshot: CompoundTag,
        delta: Double,
    ): CompoundTag =
        snapshot.copy().also { tag ->
            val pose = SableNBTUtils.readPose3d(tag.getCompound("pose"))
            val surface = Vector3(pose.position().x, pose.position().y + if (delta > 0.0) delta else 0.0, pose.position().z)
            OrbitalTestFrame.requirePatch(surface)
            pose.position().add(0.0, delta, 0.0)
            require(pose.position().y >= 64.0 && pose.position().y <= 16000.0) {
                "Climb above y=320 before ascent; remain inside the diagnostic altitude range"
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

    fun discardAttachments(
        level: ServerLevel,
        snapshot: CompoundTag,
    ) {
        for (tag in snapshot.getList("orbitalis_attached", 10)) {
            val data = tag as CompoundTag
            require(data.getString("id") == "create:super_glue") { "Unsupported attached entity" }
            level.getEntity(data.getUUID("UUID"))?.let { entity ->
                require(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() == "create:super_glue",
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
            require(!data.contains("Passengers")) { "Attached passengers are unsupported" }
            val entity =
                checkNotNull(EntityType.loadEntityRecursive(data, body.level) { it }) { "Could not reconstruct Create glue metadata" }
            check(body.plot.contains(entity.position()) && body.level.addFreshEntity(entity)) { "Could not attach Create glue metadata" }
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
            "createpropulsion:creative_thruster",
            "simulated:physics_assembler",
        )
}
