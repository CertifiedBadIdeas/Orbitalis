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

package org.cbi.orbitalis.minecraft.client.ship

import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.integration.sable.SableShipContact
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Local observation only. Native ship, passenger and player look remain authoritative. */
object SableShipView : ShipViewHooks.Controller {
    private var shipId: UUID? = null
    private var perspective: CameraType? = null
    private var missingTicks = 0
    private var yaw = 0f
    private var pitch = 20f
    private var zoom = 1.0
    private var distance = 0.0
    private var frameTime = 0L
    private var lastPosition: Vec3? = null
    private var lastDimension: ResourceKey<Level>? = null
    private val minecraft get() = Minecraft.getInstance()
    val active get() = shipId != null

    fun contact(): ClientSubLevel? = minecraft.player?.let { SableShipContact.resolve(it) as? ClientSubLevel }

    fun toggle() {
        if (active) {
            exit()
            return
        }
        val body = contact() ?: return
        shipId = body.uniqueId
        perspective = minecraft.options.cameraType
        minecraft.options.cameraType = CameraType.THIRD_PERSON_BACK
        yaw = minecraft.gameRenderer.mainCamera.yRot
        pitch = 20f
        zoom = 1.0
        distance = 0.0
        lastPosition = null
        lastDimension = null
        frameTime = 0L
        missingTicks = 0
    }

    fun tick() {
        if (!active) return
        if (minecraft.player?.isAlive != true || minecraft.level == null) {
            exit()
            return
        }
        if (minecraft.options.cameraType != CameraType.THIRD_PERSON_BACK) {
            exit(false)
            return
        }
        val body = contact()
        if (body?.uniqueId == shipId) {
            missingTicks = 0
        } else if (body != null || ++missingTicks > 100) {
            exit()
        }
    }

    private fun exit(restore: Boolean = true) {
        if (restore) perspective?.let { minecraft.options.cameraType = it }
        perspective = null
        shipId = null
        distance = 0.0
        lastPosition = null
        lastDimension = null
    }

    override fun turn(
        x: Double,
        y: Double,
    ): Boolean {
        if (!active || minecraft.screen != null || contact()?.uniqueId != shipId) return false
        yaw = (yaw + x.toFloat() * 0.15f) % 360f
        pitch = (pitch + y.toFloat() * 0.15f).coerceIn(-85f, 85f)
        return true
    }

    fun scroll(delta: Double): Boolean {
        if (!active || minecraft.screen != null) return false
        zoom = ShipViewMath.zoom(zoom, delta)
        return true
    }

    override fun setup(
        camera: Camera,
        partialTick: Float,
        fieldOfView: Double,
    ) {
        if (!active) return
        val contact = contact()
        val body = contact?.takeIf { it.uniqueId == shipId }
        if (body == null) {
            // Native dimension travel briefly drops contact before the destination ship bundle arrives.
            // Keep the last observed view, rebased through the route's canonical coordinate transform.
            // Do not extrapolate physics or follow another ship while waiting for the same UUID.
            if (contact == null) holdView(camera)
            return
        }
        rebaseView(body.level.dimension())
        val bounds = body.plot.boundingBox
        val centre =
            Vector3d(
                (bounds.minX().toDouble() + bounds.maxX() + 1.0) * 0.5,
                (bounds.minY().toDouble() + bounds.maxY() + 1.0) * 0.5,
                (bounds.minZ().toDouble() + bounds.maxZ() + 1.0) * 0.5,
            )
        val pose = body.renderPose(partialTick)
        pose.transformPosition(centre, centre)
        val dx = (bounds.maxX() - bounds.minX() + 1).toDouble() * abs(pose.scale().x())
        val dy = (bounds.maxY() - bounds.minY() + 1).toDouble() * abs(pose.scale().y())
        val dz = (bounds.maxZ() - bounds.minZ() + 1).toDouble() * abs(pose.scale().z())
        val radius = sqrt(dx * dx + dy * dy + dz * dz) * 0.5
        val window = minecraft.window
        if (!centre.isFinite || radius <= 0.0 || window.height <= 0) return
        val minimum = ShipViewMath.fitDistance(radius, fieldOfView.coerceIn(1.0, 179.0), window.width.toDouble() / window.height)
        val now = System.nanoTime()
        val dt = if (frameTime == 0L) 1.0 else ((now - frameTime) / 1e9).coerceIn(0.0, 0.2)
        frameTime = now
        distance =
            if (distance == 0.0) minimum * zoom else (distance + (minimum * zoom - distance) * (1 - exp(-10 * dt))).coerceAtLeast(minimum)
        val centreVec = Vec3(centre.x, centre.y, centre.z)
        val direction = ShipViewMath.direction(yaw.toDouble(), pitch.toDouble())
        val desired = centreVec.subtract(direction.x * distance, direction.y * distance, direction.z * distance)
        val context = ClipContext(centreVec, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player!!)
        (context as ClipContextExtension).`sable$setIgnoredSubLevel`(body)
        val hit = body.level.clip(context)
        val position =
            if (hit.type == HitResult.Type.MISS) {
                desired
            } else {
                val delta = hit.location.subtract(centreVec)
                centreVec.add(delta.scale(((delta.length() - 0.3).coerceAtLeast(0.0)) / delta.length().coerceAtLeast(1e-6)))
            }
        (camera as ShipCameraAccess).orbitalisSetView(position, yaw, pitch)
        lastPosition = position
        lastDimension = body.level.dimension()
    }

    private fun rebaseView(target: ResourceKey<Level>) {
        val source = lastDimension ?: return
        val position = lastPosition ?: return
        if (source == target) return
        val level = minecraft.level ?: return
        if (!org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .available(level) ||
            source !in org.cbi.orbitalis.minecraft.flight.CelestialFlight.dimensions ||
            target !in org.cbi.orbitalis.minecraft.flight.CelestialFlight.dimensions
        ) {
            return
        }
        val sample =
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .sample(level)
        val local = sample.point(Vector3(position.x, position.y, position.z), source, target)
        val ray = Vec3.directionFromRotation(pitch, yaw)
        val direction = sample.direction(Vector3(ray.x, ray.y, ray.z), source, target)
        yaw = Math.toDegrees(kotlin.math.atan2(-direction.x, direction.z)).toFloat()
        pitch = Math.toDegrees(kotlin.math.asin((-direction.y).coerceIn(-1.0, 1.0))).toFloat()
        lastPosition = Vec3(local.x, local.y, local.z)
        lastDimension = target
    }

    private fun holdView(camera: Camera) {
        val target = minecraft.level?.dimension() ?: return
        rebaseView(target)
        if (lastDimension != target) return
        (camera as ShipCameraAccess).orbitalisSetView(lastPosition ?: return, yaw, pitch)
    }
}
