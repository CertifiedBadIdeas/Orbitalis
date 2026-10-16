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

import dev.ryanhcode.sable.api.math.LevelReusedVectors
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.minecraft.lab.CubePlayerContacts
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.ceil
import kotlin.math.floor

/** The native solver owns ship contact and tracking; planetary locomotion owns terrain and gravity. */
object SableCubePlayerContacts : CubePlayerContacts {
    override fun collide(
        entity: Entity,
        requested: Vec3,
    ): CubePlayerContacts.Contact {
        val movement = entity as EntityMovementExtension
        // Sable's server path trusts client tracking packets and never solves player contacts.
        // Avoid its global-Y velocity clamp; the cube movement code applies local support instead.
        if (entity is ServerPlayer) {
            val grounded = movement.`sable$getTrackingSubLevel`() != null && requested.dot(CubePlayerPhysics.normal(entity)) <= 0.0
            return CubePlayerContacts.Contact(requested, grounded = grounded, verticalCollision = grounded)
        }
        val inherited = (entity as LivingEntityMovementExtension).`sable$getInheritedVelocity`()
        val info =
            SubLevelEntityCollision.collide(
                entity,
                requested,
                Vec3(inherited.x, inherited.y, inherited.z),
                (entity.level() as LevelExtension).`sable$getJOMLSink`(),
            )
        val candidate = info.trackingSubLevel?.takeUnless { it.isRemoved }
        if (candidate == null || info.verticalCollisionBelow) movement.`sable$setTrackingSubLevel`(candidate)
        val tracking = movement.`sable$getTrackingSubLevel`()
        val carry = info.inheritedMotion ?: Vec3.ZERO
        if (tracking == null) {
            // Departure keeps the deck's last inertial velocity, once, in the ordinary flight state.
            entity.deltaMovement = entity.deltaMovement.add(inherited.x, inherited.y, inherited.z)
            inherited.zero()
        } else {
            inherited.set(carry.x, carry.y, carry.z)
        }
        return CubePlayerContacts.Contact(info.motion, carry, info.verticalCollisionBelow, info.verticalCollision, info.horizontalCollision)
    }

    /** Check a conservative world envelope against native ship block shapes before accepting a turn. */
    override fun clear(
        entity: Entity,
        bounds: AABB,
    ): Boolean = clear(entity.level(), bounds)

    fun clear(
        level: Level,
        bounds: AABB,
    ): Boolean {
        val container = SubLevelContainer.getContainer(level) ?: return true
        val sink = LevelReusedVectors()
        val c = bounds.center
        val candidate =
            OrientedBoundingBox3d(
                c.x,
                c.y,
                c.z,
                bounds.xsize,
                bounds.ysize,
                bounds.zsize,
                Quaterniond(),
                sink,
            )
        for (body in container.queryIntersecting(BoundingBox3d().set(bounds))) {
            val pose = body.logicalPose()
            val local = boundsCorners(bounds).map { pose.transformPositionInverse(it) }
            val min = Vector3d(Double.POSITIVE_INFINITY)
            val max = Vector3d(Double.NEGATIVE_INFINITY)
            local.forEach {
                min.min(it)
                max.max(it)
            }
            for (pos in BlockPos.betweenClosed(
                floor(min.x).toInt(),
                floor(min.y).toInt(),
                floor(min.z).toInt(),
                ceil(max.x).toInt(),
                ceil(max.y).toInt(),
                ceil(max.z).toInt(),
            )) {
                val shape = level.getBlockState(pos).getCollisionShape(level, pos)
                for (box in shape.toAabbs()) {
                    val centre =
                        pose.transformPosition(
                            Vector3d(
                                pos.x + (box.minX + box.maxX) / 2,
                                pos.y + (box.minY + box.maxY) / 2,
                                pos.z + (box.minZ + box.maxZ) / 2,
                            ),
                        )
                    val block =
                        OrientedBoundingBox3d(centre.x, centre.y, centre.z, box.xsize, box.ysize, box.zsize, pose.orientation(), sink)
                    // Native SAT returns a zero displacement for separated/touching boxes.
                    if (OrientedBoundingBox3d.sat(candidate, block).lengthSquared() > 1e-12) return false
                }
            }
        }
        return true
    }

    private fun boundsCorners(box: AABB): List<Vector3d> =
        listOf(box.minX, box.maxX).flatMap { x ->
            listOf(box.minY, box.maxY).flatMap { y -> listOf(box.minZ, box.maxZ).map { z -> Vector3d(x, y, z) } }
        }
}
