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

/** Monotonic seconds. Native readiness must remain stable before revealing the destination. */
class HeldFrameTiming {
    private var started: Double? = null
    private var readySince: Double? = null
    val active get() = started != null

    fun begin(now: Double) {
        started = now
        readySince = null
    }

    fun clear() {
        started = null
        readySince = null
    }

    fun opacity(
        now: Double,
        ready: Boolean,
    ): Float {
        val start = started ?: return 0f
        if (now - start >= 10.0 || now < start) {
            clear()
            return 0f
        }
        if (!ready) {
            readySince = null
            return 1f
        }
        val since = readySince ?: now.also { readySince = it }
        val t = ((now - since - 0.2) / 0.25).coerceIn(0.0, 1.0)
        if (t == 1.0) clear()
        return (1.0 - t * t * (3.0 - 2.0 * t)).toFloat()
    }
}
