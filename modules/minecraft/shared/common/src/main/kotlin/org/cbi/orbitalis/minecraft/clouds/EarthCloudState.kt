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

package org.cbi.orbitalis.minecraft.clouds

/** One Earth weather snapshot for native ground, orbital and map rendering; no client classes. */
object EarthCloudState {
    const val BASE_HEIGHT = 192.0
    private const val WEATHER_BLEND_NANOS = 1_000_000_000.0

    data class Snapshot(
        val seed: Long,
        val rain: Float,
        val thunder: Float,
        val weatherTick: Long,
        val baseHeight: Double = BASE_HEIGHT,
    ) {
        init {
            require(rain.isFinite() && rain in 0f..1f && thunder.isFinite() && thunder in 0f..1f) { "Invalid Earth cloud weather" }
            require(weatherTick >= 0 && baseHeight.isFinite() && baseHeight in 64.0..1024.0) { "Invalid Earth cloud epoch or height" }
        }
    }

    data class Wind(
        val x: Double,
        val z: Double,
    )

    private data class Transition(
        val from: Snapshot,
        val target: Snapshot,
        val started: Long,
    )

    @Volatile private var transition: Transition? = null
    val client: Snapshot? get() = transition?.target

    /** Interpolate weather only; physical-clock wind never depends on recipient world ticks. */
    fun sampleClient(nowNanos: Long = System.nanoTime()): Snapshot? {
        val state = transition ?: return null
        val fraction = ((nowNanos - state.started).toDouble() / WEATHER_BLEND_NANOS).coerceIn(0.0, 1.0).toFloat()
        return state.target.copy(
            rain = state.from.rain + (state.target.rain - state.from.rain) * fraction,
            thunder = state.from.thunder + (state.target.thunder - state.from.thunder) * fraction,
        )
    }

    fun receive(
        snapshot: Snapshot,
        nowNanos: Long = System.nanoTime(),
    ) {
        val current = transition
        if (current != null && current.target.seed == snapshot.seed && snapshot.weatherTick <= current.target.weatherTick) return
        val from = if (current?.target?.seed == snapshot.seed) sampleClient(nowNanos) ?: snapshot else snapshot
        transition = Transition(from, snapshot, nowNanos)
    }

    fun clearClient() {
        transition = null
    }

    /** Stable derived cloud identity; the raw Overworld seed is not sent to clients. */
    fun derivedSeed(worldSeed: Long): Long {
        var mixed = worldSeed xor 0x434c4f5544534545L
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        return mixed xor (mixed ushr 31)
    }

    /** Exact whole-tick advection on a centimetre grid; caller supplies its genuinely periodic field size. */
    fun wind(
        tick: Long,
        seconds: Double = 0.0,
        period: Double,
    ): Wind {
        require(seconds.isFinite()) { "Cloud wind requires finite physical seconds" }
        require(period.isFinite() && period in 0.01..1.0e9) { "Invalid cloud field period" }
        val centimetres = kotlin.math.round(period * 100.0).toLong()
        require(kotlin.math.abs(centimetres / 100.0 - period) < 1e-8) { "Cloud period must fit the centimetre grid" }

        fun phase(
            centimetresPerTick: Long,
            metresPerSecond: Double,
        ): Double {
            val whole = (Math.floorMod(tick, centimetres) * centimetresPerTick % centimetres) / 100.0
            val partial = (seconds % (period / metresPerSecond)) * metresPerSecond
            return ((whole + partial) % period + period) % period
        }
        return Wind(phase(3L, 0.6), phase(1L, 0.2))
    }
}
