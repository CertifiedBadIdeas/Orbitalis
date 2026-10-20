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

package org.cbi.orbitalis.core.planet

import java.util.TreeMap

/** Coarse presentation data only; neither unknown cells nor lookups generate terrain. */
class SurfaceAtlas {
    data class Sample(
        val x: Int,
        val z: Int,
        val height: Int,
        val color: Int,
        val waterCoverage: Int = 0,
    ) {
        init {
            require(x in MIN_CHUNK..MAX_CHUNK && z in MIN_CHUNK..MAX_CHUNK)
            require(height in -4096..4096 && color in 0..0xffffff)
            require(waterCoverage in 0..255)
        }

        val key get() = (x - MIN_CHUNK) * WIDTH + z - MIN_CHUNK
    }

    private val samples = hashMapOf<Int, Sample>()
    private val revisions = hashMapOf<Int, Long>()
    private val stream = TreeMap<Long, Int>()
    var revision = 0L
        private set
    val size get() = samples.size

    fun put(sample: Sample): Boolean {
        if (samples[sample.key] == sample) return false
        revisions[sample.key]?.let { stream.remove(it) }
        samples[sample.key] = sample
        revision++
        revisions[sample.key] = revision
        stream[revision] = sample.key
        return true
    }

    fun contains(
        x: Int,
        z: Int,
    ): Boolean = samples.containsKey((x - MIN_CHUNK) * WIDTH + z - MIN_CHUNK)

    fun all(): Collection<Sample> = java.util.Collections.unmodifiableCollection(samples.values)

    /** Only current values are retained; cursors also work after an earlier value was superseded. */
    fun after(
        cursor: Long,
        limit: Int,
    ): Pair<Long, List<Sample>> {
        require(cursor >= 0 && limit in 1..256)
        val result = stream.tailMap(cursor, false).entries.take(limit)
        return (result.lastOrNull()?.key ?: cursor) to result.map { samples.getValue(it.value) }
    }

    data class Cell(
        val x: Int,
        val z: Int,
        val height: Double,
        val color: Int,
        val known: Int,
        val waterCoverage: Double = 0.0,
    )

    /** Native 16m cells by default; optional 32m aggregation combines four known chunk samples. */
    fun overview(chunksPerCell: Int = 1): Map<Pair<Int, Int>, Cell> {
        require(chunksPerCell in 1..2)
        val groups = hashMapOf<Pair<Int, Int>, LongArray>()
        for (sample in samples.values) {
            val key = Math.floorDiv(sample.x, chunksPerCell) to Math.floorDiv(sample.z, chunksPerCell)
            val sum = groups.getOrPut(key) { LongArray(6) }
            sum[0] += sample.height.toLong()
            sum[1] += (sample.color shr 16 and 255).toLong()
            sum[2] += (sample.color shr 8 and 255).toLong()
            sum[3] += (sample.color and 255).toLong()
            sum[4]++
            sum[5] += sample.waterCoverage.toLong()
        }
        return groups.mapValues { (key, sum) ->
            val n = sum[4]
            Cell(
                key.first,
                key.second,
                sum[0].toDouble() / n,
                ((sum[1] / n).toInt() shl 16) or ((sum[2] / n).toInt() shl 8) or (sum[3] / n).toInt(),
                n.toInt(),
                sum[5].toDouble() / (n * 255),
            )
        }
    }

    companion object {
        val MIN_CHUNK = Math.floorDiv(-10000, 16)
        val MAX_CHUNK = Math.floorDiv(10000 - 1, 16)
        val WIDTH = MAX_CHUNK - MIN_CHUNK + 1
        val MAX_SAMPLES = WIDTH * WIDTH
    }
}
