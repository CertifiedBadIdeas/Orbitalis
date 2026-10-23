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

package org.cbi.orbitalis.consumer.flight

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity

/** Exact command release through Propulsion's public API, including values inside its deadband. */
object NativeThrusterInput {
    fun clear(block: AbstractThrusterBlockEntity) {
        // Propulsion 1.1.5 ignores input changes <=1e-4, including a small positive value -> zero.
        // Both writes only dirty the cache; no physics or block tick runs between them.
        block.setDigitalInput(1.0f)
        block.setDigitalInput(0.0f)
    }

    fun set(
        block: AbstractThrusterBlockEntity,
        value: Float,
    ) {
        if (value == 0.0f && block.power != 0.0f) clear(block) else block.setDigitalInput(value)
    }
}
