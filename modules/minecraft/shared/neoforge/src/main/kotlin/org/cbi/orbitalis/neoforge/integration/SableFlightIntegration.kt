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

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.PlayerSpaceTravel
import org.cbi.orbitalis.minecraft.integration.sable.AutomaticShipTravel
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferTickGate
import java.util.Locale

/** Registered only with Sable present. No Create or Propulsion classes cross this boundary. */
object SableFlightIntegration {
    private val automatic = mutableMapOf<MinecraftServer, AutomaticShipTravel>()

    private val transfers = mutableMapOf<MinecraftServer, SableShipTransfer>()

    /** Server-thread diagnostic adapter entry point; consumers must not instantiate another coordinator. */
    fun transferService(server: MinecraftServer): SableShipTransfer = checkNotNull(transfers[server]) { "Transfer service is not ready" }

    fun install() {
        org.cbi.orbitalis.minecraft.flight.PlayerGravity.serverAcceleration = { SableFlightGravity.flatField(it).nativeAcceleration }
        NeoForge.EVENT_BUS.addListener { event: EntityTickEvent.Pre ->
            if (ShipTransferTickGate
                    .blocksEntity(event.entity)
            ) {
                event.isCanceled = true
            }
        }
        NeoForge.EVENT_BUS.addListener { event: ServerStartedEvent ->
            transfers[event.server] =
                SableShipTransfer(
                    event.server,
                    event.server.getWorldPath(LevelResource.ROOT).resolve("data/orbitalis-transfers"),
                ).also {
                    it.tick()
                    automatic[event.server] =
                        org.cbi.orbitalis.minecraft.integration.sable
                            .AutomaticShipTravel(event.server, it)
                }
        }
        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post ->
            transfers[event.server]?.let { manager ->
                manager.tick()
                if (!manager.busy) {
                    event.server.playerList.players
                        .forEach(manager::loggedIn)
                    automatic[event.server]?.tick()
                    if (!manager.busy) {
                        event.server.playerList.players.filter { automatic[event.server]?.attached(it) != true }.forEach {
                            PlayerSpaceTravel
                                .transfer(it)
                        }
                    }
                }
            }
        }
        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.RightClickBlock ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.RightClickItem ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.LeftClickBlock ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.EntityInteract ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.EntityInteractSpecific ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: BlockEvent.BreakEvent ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: BlockEvent.EntityPlaceEvent ->
            val world = event.level as? ServerLevel
            if (world != null && transfers[world.server]?.locks(world) == true) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            register(event.dispatcher)
        }
        NeoForge.EVENT_BUS.addListener { event: ForgeSablePrePhysicsTickEvent ->
            SableFlightGravity.beforePhysics(event.physicsSystem.level, event.timeStep)
        }
        NeoForge.EVENT_BUS.addListener { event: ForgeSablePostPhysicsTickEvent ->
            SableFlightGravity.afterPhysics(event.physicsSystem.level, event.timeStep)
        }
        NeoForge.EVENT_BUS.addListener { event: ServerStoppedEvent ->
            automatic.remove(event.server)?.close()
            transfers.remove(event.server)?.close()
        }
    }

    private fun failure(message: String): Nothing = throw SimpleCommandExceptionType(Component.literal(message)).create()

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val flight = Commands.literal("flight")
        flight.then(
            Commands.literal("sample").executes { context ->
                val source = context.source
                val player = source.playerOrException
                if (!SableFlightGravity.isManaged(source.level)) failure("Use Overworld or orbitalis:space")
                val body =
                    Sable.HELPER.getTrackingSubLevel(player) as? ServerSubLevel
                        ?: failure("Stand on a Sable ship and wait for native contact")
                val sample = SableFlightGravity.reading(body)
                val global = SableFlightGravity.globalPosition(source.level, sample.position)
                val message =
                    String.format(
                        Locale.ROOT,
                        "Ship %s; mass=%.3f Sable units; centre-of-mass=(%.2f,%.2f,%.2f) m; " +
                            "height above sea=%.2f m; speed=%.3f m/s; gravity=(%.4f,%.4f,%.4f) m/s²; |g|=%.4f m/s²",
                        body.uniqueId,
                        sample.mass,
                        sample.position.x,
                        sample.position.y,
                        sample.position.z,
                        global.y - FlatPlanetSpace.SEA_LEVEL,
                        sample.velocity.length(),
                        sample.acceleration.x,
                        sample.acceleration.y,
                        sample.acceleration.z,
                        sample.acceleration.length(),
                    )
                source.sendSuccess({ Component.literal(message) }, false)
                1
            },
        )
        flight.then(
            Commands.literal("transfer").executes { context ->
                val source = context.source
                val player = source.playerOrException
                val body =
                    Sable.HELPER.getTrackingSubLevel(player) as? ServerSubLevel
                        ?: failure("Stand on the ship with creative flight disabled")
                try {
                    player.closeContainer()
                    transferService(source.server).begin(player, body)
                } catch (error: IllegalArgumentException) {
                    failure(error.message ?: "Flight transfer rejected")
                }
                source.sendSuccess({ Component.literal("Preparing ship transfer; remain on deck.") }, false)
                1
            },
        )
        dispatcher.register(Commands.literal("orbitalis").requires { it.hasPermission(2) }.then(flight))
    }
}
