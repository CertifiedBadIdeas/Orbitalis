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

package org.cbi.orbitalis.neoforge.integration

import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.CommandEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.cbi.orbitalis.minecraft.flight.control.FlightControl

/** Registered once with Sable present, independently of engine backend availability. */
object FlightControlIntegration {
    fun install() {
        FlightControl.transferService = SableFlightIntegration::transferService
        FlightControl.install()
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent -> FlightControl.register(event.dispatcher) }
        NeoForge.EVENT_BUS.addListener { event: CommandEvent -> FlightControl.command(event.parseResults.context) }
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Pre -> FlightControl.tick(event.server) }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppingEvent -> FlightControl.stopping(event.server) }
    }
}
