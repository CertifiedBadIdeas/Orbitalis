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

package org.cbi.orbitalis.minecraft.flight

/** Bounded client-tick interpolation; native dimension gameTime resets cannot move this epoch. */
class ClientEphemerisClock {
    private var epoch = 0L
    private var age = 0L
    private var advancing = false
    private var initialized = false
    private var previousOffset = 0.0
    private var currentOffset = 0.0
    val tick get() = epoch + age.coerceAtMost(Long.MAX_VALUE - epoch)

    fun accept(
        tick: Long,
        advancing: Boolean,
    ) {
        require(tick >= 0)
        val shift = this.tick - tick
        if (initialized && this.advancing && advancing && shift in -10L..10L &&
            previousOffset + shift in -10.0..10.0 && currentOffset + shift in -10.0..10.0
        ) {
            // Preserve small network corrections, but bound their cumulative visual delay.
            // A native tick sprint may deliver many snapshots between client frames.
            previousOffset += shift.toDouble()
            currentOffset += shift.toDouble()
        } else {
            previousOffset = 0.0
            currentOffset = 0.0
        }
        epoch = tick
        age = 0L
        this.advancing = advancing
        initialized = true
    }

    /** Offsets avoid losing tick precision at large saved epochs. */
    fun renderSeconds(partialTick: Float): Double =
        (previousOffset + (currentOffset - previousOffset) * partialTick.coerceIn(0f, 1f)) / 20.0

    fun advance() {
        val before = tick
        if (advancing) age = (age + 1).coerceAtMost(10L)
        previousOffset = currentOffset + (before - tick).toDouble()
        // Catch up gently after a snapshot correction; never run the visible orbit backwards.
        currentOffset = previousOffset + (-previousOffset).coerceIn(0.0, 1.25)
    }
}
