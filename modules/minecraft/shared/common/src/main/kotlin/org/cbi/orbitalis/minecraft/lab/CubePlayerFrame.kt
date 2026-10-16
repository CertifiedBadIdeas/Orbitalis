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

import net.minecraft.core.Direction

/** Transient collider frame; never persisted or sent as a second source of player state. */
interface CubePlayerFrame {
    fun orbitalisGetUp(): Direction?

    fun orbitalisSetUp(up: Direction?)

    /** Client-owned chart rebase counter, modulo four. Neither saved nor independently networked. */
    fun orbitalisGetYawRebase(): Int

    fun orbitalisSetYawRebase(quarterTurns: Int)
}
