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

package org.cbi.orbitalis.minecraft.lab

import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.GliderFlight
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Opt-in cube player locomotion and flight. Vanilla movement remains outside this scene. */
object CubePlayerPhysics {
    @JvmField
    val CENTRE = PlanetLabCoordinates.CENTRE

    @JvmField
    val GRAVITY = PlanetLabCoordinates.GRAVITY

    const val HALF_HEIGHT = 0.9
    private const val HALF_WIDTH = 0.3

    @JvmStatic
    fun active(entity: Entity): Boolean =
        entity is Player && entity.level().dimension() == PlanetLabCoordinates.DIMENSION &&
            (entity as CubePlayerMode).orbitalisCubeEnrolled() && !entity.isSpectator

    @JvmStatic
    fun centre(entity: Entity): Vec3 = entity.position().add(0.0, HALF_HEIGHT, 0.0)

    private fun state(entity: Entity): CubePlayerFrame = entity as CubePlayerFrame

    @JvmStatic
    fun desiredUp(entity: Entity): Direction {
        val p = centre(entity).subtract(CENTRE)
        val largest = maxOf(abs(p.x), abs(p.y), abs(p.z))
        val old = state(entity).orbitalisGetUp()
        if (old != null && p.dot(Vec3.atLowerCornerOf(old.normal)) >= largest - 1e-7) return old
        return when {
            abs(p.y) == largest -> if (p.y >= 0) Direction.UP else Direction.DOWN
            abs(p.x) == largest -> if (p.x >= 0) Direction.EAST else Direction.WEST
            else -> if (p.z >= 0) Direction.SOUTH else Direction.NORTH
        }
    }

    @JvmStatic
    fun up(entity: Entity): Direction = state(entity).orbitalisGetUp() ?: desiredUp(entity)

    @JvmStatic
    fun rotation(up: Direction): Quaternionf =
        if (up == Direction.DOWN) {
            Quaternionf().rotationX(Math.PI.toFloat())
        } else {
            Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(up.stepX.toFloat(), up.stepY.toFloat(), up.stepZ.toFloat()))
        }

    @JvmStatic
    fun rotate(
        vector: Vec3,
        up: Direction,
    ): Vec3 {
        val v = rotation(up).transform(Vector3f(vector.x.toFloat(), vector.y.toFloat(), vector.z.toFloat()))
        return Vec3(v.x.toDouble(), v.y.toDouble(), v.z.toDouble())
    }

    @JvmStatic
    fun rotation(entity: Entity): Quaternionf = state(entity).orbitalisGetOrientation() ?: desiredRotation(entity)

    @JvmStatic
    fun rotate(
        vector: Vec3,
        entity: Entity,
    ): Vec3 = transform(vector, rotation(entity))

    @JvmStatic
    fun local(
        vector: Vec3,
        entity: Entity,
    ): Vec3 = transform(vector, rotation(entity).conjugate())

    private fun transform(
        vector: Vec3,
        orientation: Quaternionf,
    ): Vec3 {
        val v = Quaterniond(orientation).transform(Vector3d(vector.x, vector.y, vector.z))
        return Vec3(v.x, v.y, v.z)
    }

    @JvmStatic
    fun normal(entity: Entity): Vec3 = rotate(Vec3(0.0, 1.0, 0.0), entity).normalize()

    @JvmStatic
    fun halfHeight(entity: Entity): Double = state(entity).orbitalisGetBodyHalfHeight().takeIf { it > 0.0 } ?: desiredHalfHeight(entity)

    private fun desiredHalfHeight(entity: Entity): Double =
        if (entity is Player && entity.isFallFlying &&
            !entity.abilities.flying
        ) {
            HALF_WIDTH
        } else {
            HALF_HEIGHT
        }

    @JvmStatic
    fun eyeOffset(entity: Entity): Double =
        entity.getEyeHeight(if (halfHeight(entity) < HALF_HEIGHT) Pose.FALL_FLYING else Pose.STANDING) - halfHeight(entity)

    @JvmStatic
    fun gravity(entity: Entity): Vec3 {
        val p = centre(entity).subtract(CENTRE)
        val g = GRAVITY.acceleration(Vector3(p.x, p.y, p.z))
        return Vec3(g.x, g.y, g.z)
    }

    private fun desiredRotation(entity: Entity): Quaternionf {
        val p = centre(entity).subtract(CENTRE)
        if (maxOf(abs(p.x), abs(p.y), abs(p.z)) <= GRAVITY.halfExtent + GRAVITY.transitionStartAltitude) {
            return rotation(desiredUp(entity))
        }
        val g = gravity(entity)
        if (g.lengthSqr() < 1e-18) return state(entity).orbitalisGetOrientation() ?: rotation(desiredUp(entity))
        val n = g.normalize().scale(-1.0)
        val horizontal = hypot(n.x, n.z)
        if (horizontal < 1e-12) return if (n.y < 0.0) rotation(Direction.DOWN) else Quaternionf()
        // Axis/angle in double precision avoids rotationTo's loss of accuracy near world -Y.
        return Quaternionf(Quaterniond().rotationAxis(atan2(horizontal, n.y), n.z / horizontal, 0.0, -n.x / horizontal)).normalize()
    }

    /** Conservative world-axis envelope of the gravity-aligned body, not an exact OBB collider. */
    @JvmStatic
    fun box(entity: Entity): AABB = box(entity, rotation(entity))

    private fun box(
        entity: Entity,
        orientation: Quaternionf,
        height: Double = halfHeight(entity),
    ): AABB {
        val right = transform(Vec3(HALF_WIDTH, 0.0, 0.0), orientation)
        val top = transform(Vec3(0.0, height, 0.0), orientation)
        val forward = transform(Vec3(0.0, 0.0, HALF_WIDTH), orientation)

        fun extent(
            a: Double,
            b: Double,
            c: Double,
        ): Double = listOf(a, b, c).sumOf { if (abs(it) < 1e-7) 0.0 else abs(it) }
        val x = extent(right.x, top.x, forward.x)
        val y = extent(right.y, top.y, forward.y)
        val z = extent(right.z, top.z, forward.z)
        val c = centre(entity)
        return AABB(c.x - x, c.y - y, c.z - z, c.x + x, c.y + y, c.z + z)
    }

    /** Clear turns keep centre and inertial velocity. Contact may resolve growing bounds outward. */
    @JvmStatic
    fun refresh(entity: Entity) {
        val previous = rotation(entity)
        val next = desiredRotation(entity)
        val oldBounds = box(entity, previous)
        val height = desiredHalfHeight(entity)
        val clearance = box(entity, next, height).minmax(oldBounds)
        if (clear(entity, clearance.deflate(1e-6))) {
            acceptTurn(entity, previous, next, height)
        } else {
            val offset = turnClearance(entity, next, clearance, oldBounds)
            if (offset != null) {
                acceptTurn(entity, previous, next, height)
                entity.setPos(entity.position().add(offset))
            }
        }
        entity.boundingBox = box(entity)
    }

    private fun acceptTurn(
        entity: Entity,
        previous: Quaternionf,
        next: Quaternionf,
        height: Double,
    ) {
        if (entity is Player && entity.isLocalPlayer) {
            val radians = CubePlayerOrientation.yawShift(previous, next)
            val yaw = Math.toDegrees(radians).toFloat()
            entity.yRot += yaw
            entity.yRotO += yaw
            entity.yHeadRot += yaw
            entity.yHeadRotO += yaw
            entity.yBodyRot += yaw
            entity.yBodyRotO += yaw
            state(entity).orbitalisSetYawRebase(state(entity).orbitalisGetYawRebase() + radians)
        }
        state(entity).orbitalisSetUp(desiredUp(entity))
        state(entity).orbitalisSetOrientation(next)
        state(entity).orbitalisSetBodyHalfHeight(height)
    }

    /** Resolve only collider growth; the old body must reach the new centre without crossing terrain. */
    private fun turnClearance(
        entity: Entity,
        next: Quaternionf,
        candidate: AABB,
        oldBounds: AABB,
    ): Vec3? {
        val nextUp = transform(Vec3(0.0, 1.0, 0.0), next).normalize()
        val growth =
            (
                (candidate.xsize - oldBounds.xsize) * abs(nextUp.x) +
                    (candidate.ysize - oldBounds.ysize) * abs(nextUp.y) +
                    (candidate.zsize - oldBounds.zsize) * abs(nextUp.z)
            ) / 2.0
        if (growth < 1e-9) return null
        val lift = nextUp.scale(growth + 1e-6)
        val outside = candidate.move(lift)
        if (!clear(entity, outside.deflate(1e-6))) return null
        var offset = lift.add(clip(entity, outside, lift.scale(-1.0)))
        if (!clear(entity, candidate.move(offset).deflate(1e-6))) {
            // Native moving floors are absent from the terrain clip. Find the smallest safe
            // outward reservation in the same bounded lift, rather than accepting a ship overlap.
            var low = 0.0
            var high = 1.0
            repeat(24) {
                val middle = (low + high) / 2.0
                if (clear(entity, candidate.move(lift.scale(middle)).deflate(1e-6))) high = middle else low = middle
            }
            offset = lift.scale(high)
        }
        if (offset.lengthSqr() < 1e-12) return null
        if (!clear(entity, candidate.move(offset).deflate(1e-6))) return null
        if (clip(entity, oldBounds, offset).subtract(offset).lengthSqr() > 1e-12) return null
        if (CubePlayerContacts.adapter?.clear(entity, oldBounds.minmax(oldBounds.move(offset)).deflate(1e-6)) == false) return null
        return offset
    }

    private fun clear(
        entity: Entity,
        bounds: AABB,
    ): Boolean = entity.level().noCollision(entity, bounds) && CubePlayerContacts.adapter?.clear(entity, bounds) != false

    private fun clip(
        entity: Entity,
        bounds: AABB,
        movement: Vec3,
    ): Vec3 {
        val shapes = entity.level().getEntityCollisions(entity, bounds.expandTowards(movement))
        // Vanilla clips global Y first. At a rotated edge that can consume the clearance before
        // the body reaches the new support and clip its remaining fall against the old face.
        val normal = normal(entity)
        val normalMotion = normal.scale(movement.dot(normal))
        val tangentMotion = movement.subtract(normalMotion)
        val tangent = Entity.collideBoundingBox(entity, tangentMotion, bounds, entity.level(), shapes)
        val inward = Entity.collideBoundingBox(entity, normalMotion, bounds.move(tangent), entity.level(), shapes)
        return tangent.add(inward)
    }

    @JvmStatic
    fun eye(
        entity: Entity,
        partialTick: Float,
    ): Vec3 {
        val position = entity.getPosition(partialTick).add(0.0, HALF_HEIGHT, 0.0)
        return position.add(normal(entity).scale(eyeOffset(entity)))
    }

    @JvmStatic
    fun view(
        entity: Entity,
        partialTick: Float,
    ): Vec3 = view(entity, partialTick, rotation(entity))

    fun view(
        entity: Entity,
        partialTick: Float,
        orientation: Quaternionf,
    ): Vec3 {
        val yaw = Math.toRadians(entity.getViewYRot(partialTick).toDouble())
        val pitch = Math.toRadians(entity.getViewXRot(partialTick).toDouble())
        return transform(Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch)), orientation)
    }

    @JvmStatic
    fun travel(
        player: Player,
        input: Vec3,
    ) {
        if (player.abilities.flying && player.isFallFlying) player.stopFallFlying()
        refresh(player)
        val normal = normal(player)
        val yaw = Math.toRadians(player.yRot.toDouble())
        var control = Vec3(input.x * cos(yaw) - input.z * sin(yaw), 0.0, input.z * cos(yaw) + input.x * sin(yaw))
        if (control.lengthSqr() > 1) control = control.normalize()
        if (player.abilities.flying) {
            player.deltaMovement =
                player.deltaMovement.scale(0.6).add(rotate(control, player).scale(player.abilities.flyingSpeed.toDouble()))
            player.move(MoverType.SELF, player.deltaMovement)
            player.fallDistance = 0f
            return
        }
        if (player.isFallFlying) {
            val velocity = local(player.deltaMovement, player)
            val force = local(gravity(player).scale(1.0 / 400.0), player)
            val next =
                GliderFlight.step(
                    Vector3(velocity.x, velocity.y, velocity.z),
                    player.yRot.toDouble(),
                    player.xRot.toDouble(),
                    Vector3(force.x, force.y, force.z),
                )
            player.deltaMovement = rotate(Vec3(next.x, next.y, next.z), player)
            player.move(MoverType.SELF, player.deltaMovement)
            if (player.onGround()) player.stopFallFlying()
            player.fallDistance = 0f
            return
        }
        val old = player.deltaMovement
        val normalMotion = normal.scale(old.dot(normal))
        val tangentMotion = old.subtract(normalMotion)
        val acceleration = if (player.onGround()) 0.075 else 0.009
        val drag = if (player.onGround()) 0.65 else 0.999
        val gravity = gravity(player)
        player.deltaMovement =
            normalMotion
                .add(tangentMotion.scale(drag))
                .add(rotate(control, player).scale(acceleration))
                .add(gravity.x / 400.0, gravity.y / 400.0, gravity.z / 400.0)
        player.move(MoverType.SELF, player.deltaMovement)
        player.fallDistance = 0f
    }

    @JvmStatic
    fun jump(player: Player) {
        val normal = normal(player)
        player.deltaMovement = player.deltaMovement.subtract(normal.scale(player.deltaMovement.dot(normal))).add(normal.scale(0.23))
        player.setOnGround(false)
        player.hasImpulse = true
    }

    /** Collision envelopes follow accepted gravity orientation; inertial velocity never rotates with it. */
    @JvmStatic
    fun move(
        entity: Entity,
        requested: Vec3,
    ) {
        refresh(entity)
        val before = entity.boundingBox
        val contact = CubePlayerContacts.adapter?.collide(entity, requested) ?: CubePlayerContacts.Contact(requested)
        val actual = clip(entity, before, contact.motion)
        val normal = normal(entity)
        // Native ship collision can change velocity along its contact normal. Only terrain clipping
        // below uses axis rejection; do not erase a ship's tangential collision response a second time.
        val blocked = contact.motion.subtract(actual)
        entity.setPos(entity.position().add(actual))
        val carry = clip(entity, entity.boundingBox, contact.carry)
        entity.setPos(entity.position().add(carry))
        entity.setOnGround(contact.grounded || (requested.dot(normal) < 0 && blocked.dot(normal) < -1e-7))
        entity.verticalCollision = contact.verticalCollision || abs(blocked.dot(normal)) > 1e-7
        entity.verticalCollisionBelow = entity.onGround()
        entity.horizontalCollision = contact.horizontalCollision || blocked.subtract(normal.scale(blocked.dot(normal))).lengthSqr() > 1e-12
        var velocity = entity.deltaMovement
        val normalSpeed = velocity.dot(normal)
        val shipBlockedNormal = requested.subtract(contact.motion).dot(normal)
        if ((contact.grounded && normalSpeed < 0.0) ||
            (contact.verticalCollision && shipBlockedNormal * normalSpeed > 1e-12)
        ) {
            velocity = velocity.subtract(normal.scale(normalSpeed))
        }
        entity.deltaMovement =
            Vec3(
                if (abs(blocked.x) > 1e-7) 0.0 else velocity.x,
                if (abs(blocked.y) > 1e-7) 0.0 else velocity.y,
                if (abs(blocked.z) > 1e-7) 0.0 else velocity.z,
            )
        entity.fallDistance = 0f
    }
}
