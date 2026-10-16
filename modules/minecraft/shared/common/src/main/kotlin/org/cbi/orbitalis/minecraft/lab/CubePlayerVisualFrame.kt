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

import org.joml.Quaternionf

/** Tick-owned visual endpoints, sampled without mutation by every render consumer. */
class CubePlayerVisualFrame(
    initial: Quaternionf,
) {
    private val previous = Quaternionf(initial)
    private val current = Quaternionf(initial)

    fun tick(
        target: Quaternionf,
        rebaseRadians: Double,
    ) {
        // Vanilla rebases both current and previous yaw into the destination chart.
        previous.set(current).rotateY(rebaseRadians.toFloat())
        CubePlayerOrientation.ease(current, target, rebaseRadians, 0.05)
    }

    fun sample(partialTick: Float): Quaternionf = Quaternionf(previous).slerp(current, partialTick.coerceIn(0f, 1f)).normalize()
}
