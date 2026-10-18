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

package org.cbi.orbitalis.minecraft.client.space

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import org.cbi.orbitalis.core.orbit.LunarApproachView
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.client.ship.ShipCameraAccess
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace

/** Free-player horizon follows the lunar surface before its native frame handoff. Aim stays native. */
object PlayerApproachView {
    private val reference = LunarApproachView()
    private var connection: Any? = null
    private var roll = 0.0
    private var dimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>? = null

    fun turn(
        x: Double,
        y: Double,
    ): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (mc.screen != null || mc.level?.dimension() != dimension || kotlin.math.abs(roll) < 0.001) return false
        val look = player.lookAngle
        val nativeUp =
            net.minecraft.world.phys.Vec3
                .directionFromRotation(player.xRot - 90f, player.yRot)
        val yawAngle = Math.toRadians(player.yRot.toDouble())
        val baseUp = Vector3(nativeUp.x, nativeUp.y, nativeUp.z)
        val baseLeft = Vector3(kotlin.math.cos(yawAngle), 0.0, kotlin.math.sin(yawAngle))
        val rollAngle = Math.toRadians(roll)
        val up = baseUp * kotlin.math.cos(rollAngle) - baseLeft * kotlin.math.sin(rollAngle)
        val left = baseLeft * kotlin.math.cos(rollAngle) + baseUp * kotlin.math.sin(rollAngle)
        val ray = LunarApproachView.turnRay(Vector3(look.x, look.y, look.z), up, left, x, y)
        val yaw = Math.toDegrees(kotlin.math.atan2(-ray.x, ray.z))
        val pitch = Math.toDegrees(kotlin.math.asin((-ray.y).coerceIn(-1.0, 1.0)))
        val delta =
            net.minecraft.util.Mth
                .wrapDegrees(yaw - player.yRot)
        player.turn(delta / 0.15, (pitch - player.xRot) / 0.15)
        return true
    }

    fun setup(camera: Camera) {
        val mc = Minecraft.getInstance()
        roll = 0.0
        dimension = null
        val player = mc.player
        val level = mc.level
        if (connection !== mc.connection) {
            reference.clear()
            connection = mc.connection
        }
        if (player == null || level == null || camera.entity !== player || player.isPassenger ||
            FlightTransitionContact.ship() != null || !FlatPlanetSpace.contains(level) || !CelestialFlight.available(level)
        ) {
            return
        }
        val p = camera.position
        val up = reference.up(CelestialView.sample().frames, Vector3(p.x, p.y, p.z), CelestialFlight.world(level.dimension()))
        val nativeUp = camera.upVector
        val nativeLeft = camera.leftVector
        roll =
            LunarApproachView.roll(
                up,
                Vector3(nativeUp.x.toDouble(), nativeUp.y.toDouble(), nativeUp.z.toDouble()),
                Vector3(nativeLeft.x.toDouble(), nativeLeft.y.toDouble(), nativeLeft.z.toDouble()),
            )
        dimension = level.dimension()
        (camera as ShipCameraAccess).orbitalisRollView(roll.toFloat())
    }
}
