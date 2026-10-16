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

package org.cbi.orbitalis.consumer.flight;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cbi.orbitalis.neoforge.integration.SableFlightIntegration;
import org.joml.Quaterniond;
import org.cbi.orbitalis.core.planet.Vector3;
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(FlightConsumerMod.ID)
@EventBusSubscriber(modid = FlightConsumerMod.ID)
public final class FlightConsumerMod {
    public static final String ID = "orbitalis_flight_consumer";
    private record Control(UUID owner, AutopilotSession session, OrbitalMission mission) {
        String status() { return mission == null ? session.status() : mission.status(); }
        void close() { if (mission == null) session.close(); else mission.close(); }
    }
    private static final Map<MinecraftServer, Control> controls = new HashMap<>();

    private static ServerSubLevel ship(ServerPlayer player) {
        if (!(Sable.HELPER.getTrackingSubLevel(player) instanceof ServerSubLevel body)) {
            throw new IllegalArgumentException("Stand on the ship with creative flight disabled");
        }
        if (body.getMassTracker().getCenterOfMass() == null || body.getMassTracker().getMass() <= 0)
            throw new IllegalStateException("Wait for native ship mass initialization");
        return body;
    }

    private static int engage(CommandSourceStack source, Double altitude) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var player = source.getPlayerOrException();
            if (!player.isCreative()) throw new IllegalArgumentException("Test autopilot requires creative mode");
            var body = ship(player);
            var physics = SubLevelPhysicsSystem.require(body.getLevel());
            if (physics.getPaused()) throw new IllegalArgumentException("Wait for transfer/recovery to finish");
            var existing = controls.get(source.getServer());
            if (existing != null && !existing.owner.equals(player.getUUID())) throw new IllegalArgumentException("Another pilot owns the test autopilot");
            stop(source.getServer(), null);
            Vector3 target = AutopilotSession.position(body);
            if (altitude != null) {
                var global = FlightTestSpace.INSTANCE.globalPosition(body.getLevel(), target);
                var goal = new Vector3(global.getX(), FlightTestSpace.INSTANCE.getCENTRE().getY() + FlightTestSpace.INSTANCE.getGravity().getHalfExtent() + altitude, global.getZ());
                target = FlightTestSpace.INSTANCE.localPosition(body.getLevel().dimension(), goal);
            }
            var session = new AutopilotSession(body, target);
            controls.put(source.getServer(), new Control(player.getUUID(), session, null));
            source.sendSuccess(() -> Component.literal("Propulsion autopilot engaged. Leave manual controls off. " + session.status()), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create();
        }
    }

    private static int mission(CommandSourceStack source, boolean land) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var player = source.getPlayerOrException();
            if (!player.isCreative()) throw new IllegalArgumentException("Test autopilot requires creative mode");
            var current = controls.get(source.getServer());
            if (current != null && !current.owner.equals(player.getUUID())) throw new IllegalArgumentException("Another pilot owns the test autopilot");
            if (land && current != null && current.mission != null) {
                current.mission.requestLanding();
                source.sendSuccess(() -> Component.literal(current.status()), false);
                return 1;
            }
            var body = ship(player);
            stop(source.getServer(), null);
            var sites = LaunchSites.get(source.getServer());
            var site = land ? sites.require(player.getUUID(), body.getUniqueId()) :
                new LaunchSites.Site(body.getUniqueId(), AutopilotSession.position(body), new Quaterniond(body.logicalPose().orientation()));
            var manager = SableFlightIntegration.INSTANCE.transferService(source.getServer());
            var mission = new OrbitalMission(player, body, site, manager, land);
            try {
                if (!land) {
                    sites.put(player.getUUID(), site);
                    source.getServer().overworld().getDataStorage().save();
                }
            } catch (RuntimeException failure) { mission.close(); throw failure; }
            controls.put(source.getServer(), new Control(player.getUUID(), null, mission));
            source.sendSuccess(() -> Component.literal(mission.status()), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new SimpleCommandExceptionType(Component.literal(error.getMessage())).create();
        }
    }

    private static void stop(MinecraftServer server, String reason) {
        Control control = controls.remove(server);
        if (control == null) return;
        control.close();
        var player = server.getPlayerList().getPlayer(control.owner);
        if (player != null && reason != null) player.sendSystemMessage(Component.literal("Autopilot disengaged: " + reason + ". Native engine fade still applies."));
    }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var command = Commands.literal("autopilot")
            .then(Commands.literal("orbit").executes(c -> mission(c.getSource(), false)))
            .then(Commands.literal("land").executes(c -> mission(c.getSource(), true)))
            .then(Commands.literal("hold").executes(c -> engage(c.getSource(), null)))
            .then(Commands.literal("altitude").then(Commands.argument("height", DoubleArgumentType.doubleArg(32, 15000))
                .executes(c -> engage(c.getSource(), DoubleArgumentType.getDouble(c, "height")))))
            .then(Commands.literal("stop").executes(c -> {
                var active = controls.get(c.getSource().getServer());
                if (active != null && !active.owner.equals(c.getSource().getPlayerOrException().getUUID()))
                    throw new SimpleCommandExceptionType(Component.literal("Another pilot owns the test autopilot")).create();
                stop(c.getSource().getServer(), "manual stop");
                return 1;
            }))
            .then(Commands.literal("status").executes(c -> {
                var active = controls.get(c.getSource().getServer());
                c.getSource().sendSuccess(() -> Component.literal(active == null ? "Autopilot is off" : active.status()), false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("orbitalis").requires(s -> s.hasPermission(2))
            .then(Commands.literal("flight").then(command)));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Pre event) {
        var active = controls.get(event.getServer());
        if (active == null) return;
        try {
            var player = event.getServer().getPlayerList().getPlayer(active.owner);
            if (active.mission != null) {
                var previous = active.mission.phase();
                boolean landed = active.mission.tick(player);
                if (player != null && active.mission.phase() != previous) player.sendSystemMessage(Component.literal(active.status()));
                if (landed) stop(event.getServer(), "landed at the saved launch site");
                return;
            }
            var body = active.session.body;
            if (player == null || !player.isAlive() || player.level() != body.getLevel() ||
                Sable.HELPER.getTrackingSubLevel(player) != body || body.isRemoved() ||
                SubLevelPhysicsSystem.require(body.getLevel()).getPaused()) {
                stop(event.getServer(), "pilot/ship unavailable or physics paused");
                return;
            }
            active.session.update();
        } catch (RuntimeException error) {
            stop(event.getServer(), error.getMessage());
        }
    }

    @SubscribeEvent public static void command(CommandEvent event) {
        // Release the actual engine inputs before transfer snapshots are captured, including
        // commands nested under execute. The autopilot is deliberately not carried across handoffs.
        for (CommandContextBuilder<CommandSourceStack> c = event.getParseResults().getContext(); c != null; c = c.getChild()) {
            var nodes = c.getNodes();
            if (nodes.size() >= 3 && nodes.get(0).getNode().getName().equals("orbitalis") &&
                nodes.get(1).getNode().getName().equals("flight") &&
                (nodes.get(2).getNode().getName().equals("transfer") || nodes.get(2).getNode().getName().equals("leave"))) {
                stop(event.getParseResults().getContext().getSource().getServer(), "flight travel requested");
            }
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { stop(event.getServer(), null); }
}
