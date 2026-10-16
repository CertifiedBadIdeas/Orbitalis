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
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.joml.Quaternionf
import java.util.UUID
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min

/** Visual frame only. Physics never reads interpolated camera state. */
object CubePlayerView {
    private data class Frame(
        val rotation: Quaternionf,
        var nanos: Long,
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

    @JvmStatic
    fun rotation(entity: Entity): Quaternionf {
        if (world !== entity.level()) {
            frames.clear()
            world = entity.level()
        }
        val target = CubePlayerPhysics.rotation(CubePlayerPhysics.up(entity))
        val now = System.nanoTime()
        val frame = frames.getOrPut(entity.uuid) { Frame(Quaternionf(), now) }
        val elapsed = ((now - frame.nanos) / 1e9).coerceIn(0.0, 0.1)
        frame.nanos = now
        val angle = 2.0 * acos(abs(frame.rotation.dot(target)).coerceIn(0f, 1f).toDouble())
        if (angle > 1e-6) frame.rotation.slerp(target, min(1.0, Math.PI * elapsed / angle).toFloat())
        return Quaternionf(frame.rotation)
    }
}
