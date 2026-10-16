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

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import java.util.UUID;

/** Models server acknowledgments/contact for handoff tests; never claims a real game client. */
final class InertPilot implements AutoCloseable {
    final ServerPlayer player;
    private final EmbeddedChannel channel;
    private Integer teleportId;
    InertPilot(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "orbitalis-pilot");
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channel = new EmbeddedChannel(connection);
        player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) { if (packet instanceof ClientboundPlayerPositionPacket p) teleportId = p.getId(); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        };
        player.setGameMode(GameType.CREATIVE);
    }
    void attach(ServerSubLevel body) {
        ((EntityMovementExtension) player).sable$setTrackingSubLevel(body);
        var p = AutopilotSession.position(body);
        player.setPos(p.getX(), p.getY() + 3, p.getZ());
    }
    void acknowledge(ServerSubLevel body) {
        if (teleportId != null) {
            int id = teleportId; teleportId = null;
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
        }
        attach(body);
    }
    @Override public void close() { player.discard(); channel.finishAndReleaseAll(); }
}
