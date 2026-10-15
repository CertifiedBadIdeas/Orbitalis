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

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** One boarding teleport; subsequent support and motion belong entirely to Sable/Minecraft. */
class SableRiderSession(
    private val level: ServerLevel,
    private val body: ServerSubLevel,
    val player: ServerPlayer,
) {
    data class Report(
        val supported: Boolean,
        val localFeet: Vector3d,
        val supportedTicks: Int,
        val unsupportedTicks: Int,
        val maximumHorizontalDrift: Double,
        val maximumHeightError: Double,
    )

    private val returnPosition = player.position()
    private val returnYaw = player.yRot
    private val returnPitch = player.xRot
    private var supportedTicks = 0
    private var unsupportedTicks = 0
    private var maximumHorizontalDrift = 0.0
    private var maximumHeightError = 0.0
    var report = Report(false, Vector3d(), 0, 0, 0.0, 0.0)
        private set

    init {
        require(player.level() === level && player.isCreative && !player.abilities.flying && !player.isPassenger && !player.isRemoved) {
            "Board as an unmounted, non-flying creative player in the laboratory"
        }
        val centre = body.plot.centerBlock
        val feet = body.logicalPose().transformPosition(Vector3d(centre.x + 1.5, centre.y + 1.05, centre.z + 1.5), Vector3d())
        player.teleportTo(feet.x, feet.y, feet.z)
        player.deltaMovement = Vec3.ZERO
        player.fallDistance = 0f
    }

    /** Called once after the server tick, rather than once per physics substep. */
    fun sample() {
        if (player.level() !== level || player.isRemoved || body.isRemoved) return
        val local = body.logicalPose().transformPositionInverse(Vector3d(player.x, player.y, player.z), Vector3d())
        val centre = body.plot.centerBlock
        local.sub(centre.x.toDouble(), centre.y.toDouble(), centre.z.toDouble())
        val supported = Sable.HELPER.getTrackingSubLevel(player) === body && player.onGround()
        if (supported) supportedTicks++ else unsupportedTicks++
        maximumHorizontalDrift = max(maximumHorizontalDrift, hypot(local.x - 1.5, local.z - 1.5))
        maximumHeightError = max(maximumHeightError, abs(local.y - 1.0))
        report = Report(supported, local, supportedTicks, unsupportedTicks, maximumHorizontalDrift, maximumHeightError)
    }

    fun close() {
        if (player.level() !== level || player.isRemoved || !player.isCreative || player.isPassenger) return
        player.teleportTo(level, returnPosition.x, returnPosition.y, returnPosition.z, emptySet(), returnYaw, returnPitch)
        player.deltaMovement = Vec3.ZERO
        player.fallDistance = 0f
    }
}
