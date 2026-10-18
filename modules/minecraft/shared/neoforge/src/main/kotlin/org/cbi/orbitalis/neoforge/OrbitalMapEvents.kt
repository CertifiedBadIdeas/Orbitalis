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

package org.cbi.orbitalis.neoforge

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.map.MapRequest
import org.cbi.orbitalis.minecraft.map.MapSnapshot
import org.cbi.orbitalis.minecraft.map.MapSnapshotReceiver
import org.cbi.orbitalis.minecraft.map.OrbitalMapService
import org.cbi.orbitalis.minecraft.navigation.FlightPlanCommand
import org.cbi.orbitalis.minecraft.navigation.FlightPlanReceiver
import org.cbi.orbitalis.minecraft.navigation.FlightPlanService
import org.cbi.orbitalis.minecraft.navigation.FlightPlanState

@EventBusSubscriber(modid = Orbitalis.MOD_ID)
object OrbitalMapEvents {
    private val services = mutableMapOf<MinecraftServer, OrbitalMapService>()

    private fun service(server: MinecraftServer) =
        services.getOrPut(server) {
            OrbitalMapService(server, publish = { player, snapshot -> PacketDistributor.sendToPlayer(player, snapshot) })
        }

    fun request(
        player: ServerPlayer,
        request: MapRequest,
    ) = service(player.server).request(player, request)

    fun plan(
        player: ServerPlayer,
        request: FlightPlanCommand,
    ) {
        FlightPlanService.handle(player, request, service(player.server))?.let { PacketDistributor.sendToPlayer(player, it) }
    }

    fun viewing(player: ServerPlayer) = services[player.server]?.viewing(player) == true

    @JvmStatic @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        services[event.server]?.tick()
    }

    @JvmStatic @SubscribeEvent
    fun logout(event: PlayerEvent.PlayerLoggedOutEvent) {
        (event.entity as? ServerPlayer)?.let { services[it.server]?.logout(it.uuid) }
    }

    @JvmStatic @SubscribeEvent
    fun stop(event: ServerStoppedEvent) {
        services.remove(event.server)
    }
}

@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object OrbitalMapNetwork {
    @JvmStatic @SubscribeEvent
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("4")
        registrar.playToServer(MapRequest.TYPE, MapRequest.CODEC) { request, context ->
            context.enqueueWork { (context.player() as? ServerPlayer)?.let { OrbitalMapEvents.request(it, request) } }
        }
        registrar.playToServer(FlightPlanCommand.TYPE, FlightPlanCommand.CODEC) { request, context ->
            context.enqueueWork { (context.player() as? ServerPlayer)?.let { OrbitalMapEvents.plan(it, request) } }
        }
        registrar.playToClient(FlightPlanState.TYPE, FlightPlanState.CODEC) { state, context ->
            context.enqueueWork { FlightPlanReceiver.accept(state) }
        }
        registrar.playToClient(MapSnapshot.TYPE, MapSnapshot.CODEC) { snapshot, context ->
            context.enqueueWork { MapSnapshotReceiver.accept(snapshot) }
        }
    }
}
