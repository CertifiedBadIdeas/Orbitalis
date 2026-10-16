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

package org.cbi.orbitalis.minecraft.client

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.CubePlayerVisualFrame
import org.joml.Quaternionf
import java.util.UUID

/** Visual frame only. Physics never reads interpolated camera state. */
object CubePlayerView {
    /** Installed only on the physical client when an optional moving-world provider is present. */
    var movingCentre: ((Entity, Float) -> Vec3?)? = null

    @JvmStatic
    fun centre(
        entity: Entity,
        partialTick: Float,
    ): Vec3 =
        movingCentre?.invoke(entity, partialTick)
            ?: entity.getPosition(partialTick).add(0.0, CubePlayerPhysics.HALF_HEIGHT, 0.0)

    private data class Frame(
        val visual: CubePlayerVisualFrame,
        var yawRebase: Double,
    )

    private val frames = mutableMapOf<UUID, Frame>()
    private var world: Level? = null

    fun clear() {
        frames.clear()
        world = null
    }

    fun forget(uuid: UUID) {
        frames.remove(uuid)
    }

    private fun frame(entity: Entity): Frame {
        if (world !== entity.level()) {
            frames.clear()
            world = entity.level()
        }
        return frames.getOrPut(entity.uuid) {
            Frame(CubePlayerVisualFrame(CubePlayerPhysics.rotation(entity)), (entity as CubePlayerFrame).orbitalisGetYawRebase())
        }
    }

    fun tick(entity: Entity) {
        val frame = frame(entity)
        val yawRebase = (entity as CubePlayerFrame).orbitalisGetYawRebase()
        frame.visual.tick(CubePlayerPhysics.rotation(entity), yawRebase - frame.yawRebase)
        frame.yawRebase = yawRebase
    }

    @JvmStatic
    fun rotation(
        entity: Entity,
        partialTick: Float,
    ): Quaternionf = frame(entity).visual.sample(partialTick)
}
