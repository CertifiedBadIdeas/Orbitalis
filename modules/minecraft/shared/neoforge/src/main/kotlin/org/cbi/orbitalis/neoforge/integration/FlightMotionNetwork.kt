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

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.flight.FlightMotionPayload
import org.cbi.orbitalis.minecraft.flight.FlightMotionReceiver
import org.cbi.orbitalis.minecraft.flight.PlayerGravity
import org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload

@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object FlightMotionNetwork {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterPayloadHandlersEvent) {
        event.registrar("2").playToClient(PlayerGravityPayload.TYPE, PlayerGravityPayload.CODEC) { payload, _ ->
            PlayerGravity.clientAcceleration = payload.acceleration
            org.cbi.orbitalis.minecraft.flight.CelestialFlight.accept(
                payload.profile,
                payload.simulationTick,
                payload.lunarSeed,
                payload.advancing,
            )
        }
        event.registrar("3").playToClient(FlightMotionPayload.TYPE, FlightMotionPayload.CODEC) { payload, _ ->
            FlightMotionReceiver.accept(payload)
        }
    }
}
