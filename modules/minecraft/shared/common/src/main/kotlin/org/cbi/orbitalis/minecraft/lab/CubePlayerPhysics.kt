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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.CubicGravity
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Bounded standing-player locomotion experiment. Vanilla movement remains outside this scene. */
object CubePlayerPhysics {
    @JvmField
    val CENTRE = Vec3(2048.0, 96.0, 2048.0)

    @JvmField
    val GRAVITY = CubicGravity(16.0, 9.81, 0.0, 4.0, 20.0)

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
    fun box(
        entity: Entity,
        up: Direction,
    ): AABB {
        val c = centre(entity)
        val x = if (up.axis == Direction.Axis.X) HALF_HEIGHT else HALF_WIDTH
        val y = if (up.axis == Direction.Axis.Y) HALF_HEIGHT else HALF_WIDTH
        val z = if (up.axis == Direction.Axis.Z) HALF_HEIGHT else HALF_WIDTH
        return AABB(c.x - x, c.y - y, c.z - z, c.x + x, c.y + y, c.z + z)
    }

    /** Clear turns keep the centre. A surface contact can displace a growing collider outward. */
    @JvmStatic
    fun refresh(entity: Entity) {
        val next = desiredUp(entity)
        val previous = up(entity)
        val candidate = box(entity, next)
        // Keep room for both standing frames. Checking only the new endpoint can catch the old
        // face sideways on the following tick and send a slow player back across the boundary.
        val clearance = if (previous.axis == next.axis) candidate else candidate.minmax(box(entity, previous))
        if (entity.level().noCollision(entity, clearance.deflate(1e-6))) {
            acceptTurn(entity, previous, next)
        } else {
            val offset = turnClearance(entity, previous, next, clearance)
            if (offset != null) {
                acceptTurn(entity, previous, next)
                entity.setPos(entity.position().add(offset))
            }
        }
        entity.boundingBox = box(entity, up(entity))
    }

    private fun acceptTurn(
        entity: Entity,
        previous: Direction,
        next: Direction,
    ) {
        // The controlling client owns vanilla yaw packets. Reapplying this on a server player
        // would rotate an already rebased incoming heading a second time.
        if (entity is Player && entity.isLocalPlayer && previous != next) {
            val turns = CubePlayerOrientation.yawQuarterTurns(previous, next)
            val yaw = turns * 90f
            entity.yRot += yaw
            entity.yRotO += yaw
            entity.yHeadRot += yaw
            entity.yHeadRotO += yaw
            entity.yBodyRot += yaw
            entity.yBodyRotO += yaw
            state(entity).orbitalisSetYawRebase(state(entity).orbitalisGetYawRebase() + turns)
        }
        state(entity).orbitalisSetUp(next)
    }

    /** Resolve only the extra half-length along new up; never move through a wall to turn. */
    private fun turnClearance(
        entity: Entity,
        previous: Direction,
        next: Direction,
        candidate: AABB,
    ): Vec3? {
        if (previous.axis == next.axis) return null
        val lift = Vec3.atLowerCornerOf(next.normal).scale(HALF_HEIGHT - HALF_WIDTH + 1e-6)
        val outside = candidate.move(lift)
        if (!entity.level().noCollision(entity, outside.deflate(1e-6))) return null
        // Sweep the new box inward from a clear position to find its nearest support contact.
        val offset = lift.add(clip(entity, outside, lift.scale(-1.0)))
        if (offset.lengthSqr() < 1e-12) return null
        if (!entity.level().noCollision(entity, candidate.move(offset).deflate(1e-6))) return null
        // The old standing body must also be able to reach that centre without crossing terrain.
        if (clip(entity, box(entity, previous), offset).subtract(offset).lengthSqr() > 1e-12) return null
        return offset
    }

    private fun clip(
        entity: Entity,
        bounds: AABB,
        movement: Vec3,
    ): Vec3 {
        val shapes = entity.level().getEntityCollisions(entity, bounds.expandTowards(movement))
        // Vanilla clips global Y first. At a rotated edge that can consume the clearance before
        // the body reaches the new support and clip its remaining fall against the old face.
        val normal = Vec3.atLowerCornerOf(up(entity).normal)
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
        return position.add(Vec3.atLowerCornerOf(up(entity).normal).scale(entity.eyeHeight - HALF_HEIGHT))
    }

    @JvmStatic
    fun view(
        entity: Entity,
        partialTick: Float,
    ): Vec3 {
        val yaw = Math.toRadians(entity.getViewYRot(partialTick).toDouble())
        val pitch = Math.toRadians(entity.getViewXRot(partialTick).toDouble())
        return rotate(Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch)), up(entity))
    }

    @JvmStatic
    fun travel(
        player: Player,
        input: Vec3,
    ) {
        refresh(player)
        val up = up(player)
        val normal = Vec3.atLowerCornerOf(up.normal)
        val yaw = Math.toRadians(player.yRot.toDouble())
        var control = Vec3(input.x * cos(yaw) - input.z * sin(yaw), 0.0, input.z * cos(yaw) + input.x * sin(yaw))
        if (control.lengthSqr() > 1) control = control.normalize()
        if (player.abilities.flying) {
            player.deltaMovement = player.deltaMovement.scale(0.6).add(rotate(control, up).scale(player.abilities.flyingSpeed.toDouble()))
            player.move(MoverType.SELF, player.deltaMovement)
            player.fallDistance = 0f
            return
        }
        val old = player.deltaMovement
        val normalMotion = normal.scale(old.dot(normal))
        val tangentMotion = old.subtract(normalMotion)
        val acceleration = if (player.onGround()) 0.075 else 0.009
        val drag = if (player.onGround()) 0.65 else 0.999
        val p = centre(player).subtract(CENTRE)
        val gravity = GRAVITY.acceleration(Vector3(p.x, p.y, p.z))
        player.deltaMovement =
            normalMotion
                .add(tangentMotion.scale(drag))
                .add(rotate(control, up).scale(acceleration))
                .add(gravity.x / 400.0, gravity.y / 400.0, gravity.z / 400.0)
        player.move(MoverType.SELF, player.deltaMovement)
        player.fallDistance = 0f
    }

    @JvmStatic
    fun jump(player: Player) {
        val normal = Vec3.atLowerCornerOf(up(player).normal)
        player.deltaMovement = player.deltaMovement.subtract(normal.scale(player.deltaMovement.dot(normal))).add(normal.scale(0.23))
        player.setOnGround(false)
        player.hasImpulse = true
    }

    /** Axis-aligned boxes rotate discretely, but centre/velocity never rotate with the face. */
    @JvmStatic
    fun move(
        entity: Entity,
        requested: Vec3,
    ) {
        refresh(entity)
        val before = entity.boundingBox
        val actual = clip(entity, before, requested)
        val normal = Vec3.atLowerCornerOf(up(entity).normal)
        val blocked = requested.subtract(actual)
        entity.setPos(entity.position().add(actual))
        entity.setOnGround(requested.dot(normal) < 0 && blocked.dot(normal) < -1e-7)
        entity.verticalCollision = abs(blocked.dot(normal)) > 1e-7
        entity.verticalCollisionBelow = entity.onGround()
        entity.horizontalCollision = blocked.subtract(normal.scale(blocked.dot(normal))).lengthSqr() > 1e-12
        val velocity = entity.deltaMovement
        entity.deltaMovement =
            Vec3(
                if (abs(blocked.x) > 1e-7) 0.0 else velocity.x,
                if (abs(blocked.y) > 1e-7) 0.0 else velocity.y,
                if (abs(blocked.z) > 1e-7) 0.0 else velocity.z,
            )
        entity.fallDistance = 0f
    }
}
