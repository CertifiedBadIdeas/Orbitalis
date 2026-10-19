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

package org.cbi.orbitalis.minecraft.flight.control

import net.minecraft.server.level.ServerPlayer

/** Server-owned guidance lifecycle shared by native flight missions. */
interface FlightMission : AutoCloseable {
    val completionReason: String get() = "landed at the saved launch site"
    val stage: String
    val body: dev.ryanhcode.sable.sublevel.ServerSubLevel

    fun requestLanding()

    fun tick(pilot: ServerPlayer?): Boolean

    fun status(): String
}
