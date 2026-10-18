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

package org.cbi.orbitalis.neoforge.client

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.flight.FlightMotionPayload
import org.cbi.orbitalis.minecraft.flight.FlightMotionReceiver
import java.util.UUID

@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object FlightMotionClientSetup {
    @JvmStatic
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork(
            Runnable {
                FlightMotionReceiver.accept = FlightMotionClientState::accept
            },
        )
    }
}

@EventBusSubscriber(modid = Orbitalis.MOD_ID, value = [Dist.CLIENT])
object FlightMotionClientState {
    private var token: UUID? = null
    private var velocity: Vec3? = null
    private var capturedAt = 0L

    fun accept(packet: FlightMotionPayload) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (client.level?.dimension()?.location() != packet.dimension) return
        if (packet.capture) {
            token = packet.token
            velocity =
                player.deltaMovement.takeIf {
                    it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.length() <= 500.0
                }
            capturedAt = System.nanoTime()
        } else {
            val local = velocity.takeIf { token == packet.token && System.nanoTime() - capturedAt <= 10_000_000_000L }
            val result =
                local?.let {
                    packet.transformCaptured(
                        org.cbi.orbitalis.core.planet
                            .Vector3(it.x, it.y, it.z),
                    )
                } ?: packet.velocity
            val safe = result.takeIf { it.length().isFinite() && it.length() <= 500.0 } ?: packet.velocity
            player.deltaMovement = Vec3(safe.x, safe.y, safe.z)
            token = null
            velocity = null
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun tick(event: net.neoforged.neoforge.client.event.ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        if (mc.level != null && !mc.isPaused) {
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .advanceClient()
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun logout(event: ClientPlayerNetworkEvent.LoggingOut) {
        org.cbi.orbitalis.minecraft.flight.PlayerGravity.clientAcceleration = 11.0
        org.cbi.orbitalis.minecraft.flight.CelestialFlight
            .accept(null, 0L)
        token = null
        velocity = null
    }
}
