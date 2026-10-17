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

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.surface.FlatOverworld

@PrefixGameTestTemplate(false)
object SurfaceGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "surface")
    fun nativeOverworldBorder(helper: GameTestHelper) {
        val border =
            helper.level.server
                .overworld()
                .worldBorder
        helper.assertTrue(border.centerX == 0.0 && border.centerZ == 0.0 && border.size == FlatOverworld.SIZE, "Missing surface border")
        for (y in listOf(-10000, 64, 20000)) {
            helper.assertTrue(border.isWithinBounds(BlockPos(4999, y, 4999)), "Border added an altitude limit")
            for (p in listOf(BlockPos(5000, y, 0), BlockPos(-5001, y, 0), BlockPos(0, y, 5000), BlockPos(0, y, -5001))) {
                helper.assertTrue(!border.isWithinBounds(p), "Border allowed an outside cell: $p")
            }
        }
        helper.assertTrue(!border.collisionShape.isEmpty, "Native border has no collision shape")
        helper.assertTrue(!border.isWithinBounds(AABB(5001.0, 64.0, 0.0, 5002.0, 66.0, 1.0)), "Outside body accepted")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "surface")
    fun borderInitializationPreservesCustomization(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val settings = level.worldBorder.createSettings()
        try {
            level.worldBorder.setCenter(32.0, -48.0)
            level.worldBorder.size = 1536.0
            level.dataStorage.save()
            // NeoForge queues SavedData writes; a fresh reader must wait for the native I/O barrier.
            net.neoforged.neoforge.common.IOUtilities
                .waitUntilIOWorkerComplete()
            // Exercise a fresh storage reader, rather than just the in-memory marker cache.
            val dataDirectory =
                level.server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data")
                    .toFile()
            val storage =
                net.minecraft.world.level.storage.DimensionDataStorage(
                    dataDirectory,
                    level.server.fixerUpper,
                    level.registryAccess(),
                )
            val tag =
                storage.readTagFromDisk(
                    FlatOverworld.DATA_NAME,
                    null,
                    net.minecraft.SharedConstants
                        .getCurrentVersion()
                        .dataVersion.version,
                )
            helper.assertTrue(tag.getCompound("data").getInt("Version") == 2, "Initialization marker did not persist")
            FlatOverworld.initialize(level, storage)
            helper.assertTrue(
                level.worldBorder.centerX == 32.0 && level.worldBorder.centerZ == -48.0 && level.worldBorder.size == 1536.0,
                "Initialization overwrote native settings",
            )
        } finally {
            level.worldBorder.applySettings(settings)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "surface")
    fun spaceBorderIsIndependent(helper: GameTestHelper) {
        val server = helper.level.server
        val surface = server.overworld().worldBorder
        val settings = surface.createSettings()
        val diagnostics =
            listOf(FlatPlanetSpace.SPACE).map {
                checkNotNull(server.getLevel(it)).worldBorder
            }
        // getPlayers() is an unmodifiable view. This pinned-runtime fixture briefly registers inert
        // recipients in its backing list without simulating a login or negotiating mod payloads.
        val playersField = net.minecraft.server.players.PlayerList::class.java.getDeclaredField("players").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val recipients = playersField.get(server.playerList) as MutableList<net.minecraft.server.level.ServerPlayer>
        val surfacePackets = mutableListOf<net.minecraft.network.protocol.Packet<*>>()
        val spacePackets = mutableListOf<net.minecraft.network.protocol.Packet<*>>()
        val surfacePlayer = GameTestPlayers.create(helper, packets = surfacePackets::add)
        val spacePlayer = GameTestPlayers.create(helper, packets = spacePackets::add)
        spacePlayer.setServerLevel(checkNotNull(server.getLevel(FlatPlanetSpace.SPACE)))
        surfacePackets.clear()
        spacePackets.clear()
        try {
            recipients.addAll(listOf(surfacePlayer, spacePlayer))
            surface.setCenter(128.0, -96.0)
            surface.size = 1024.0
            surface.lerpSizeBetween(1024.0, 512.0, 10000L)
            surface.warningTime = surface.warningTime + 1
            surface.warningBlocks = surface.warningBlocks + 1
            helper.assertTrue(surfacePackets.size == 5, "Surface client missed native border edits")
            helper.assertTrue(spacePackets.isEmpty(), "Surface border packets leaked into space")
            // Both login and dimension changes use this native initialization path, which normally
            // reads the Overworld border regardless of the destination level.
            for ((player, packets) in listOf(surfacePlayer to surfacePackets, spacePlayer to spacePackets)) {
                packets.clear()
                server.playerList.sendLevelInfo(player, player.serverLevel())
                val packet = packets.filterIsInstance<net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket>().single()
                val expected = if (player === surfacePlayer) surface.lerpTarget else WorldBorder.MAX_SIZE
                helper.assertTrue(packet.newSize == expected, "Initial client border uses the wrong dimension")
            }

            diagnostics.forEach { border ->
                helper.assertTrue(
                    border.centerX == 0.0 && border.centerZ == 0.0 && border.size == WorldBorder.MAX_SIZE,
                    "Space inherited surface boundary",
                )
                helper.assertTrue(border.isWithinBounds(BlockPos(20000, 64, 20000)), "Space was bounded by the surface")
            }
            for (key in listOf(net.minecraft.world.level.Level.NETHER, net.minecraft.world.level.Level.END)) {
                val level = checkNotNull(server.getLevel(key)) { "Vanilla border fixture missing: $key" }
                helper.assertTrue(
                    level.worldBorder.centerX == 128.0 / level.dimensionType().coordinateScale() && level.worldBorder.lerpTarget == 512.0 &&
                        level.worldBorder.warningTime == surface.warningTime,
                    "Vanilla border delegation changed",
                )
            }
        } finally {
            recipients.removeAll(listOf(surfacePlayer, spacePlayer))
            surfacePlayer.discard()
            spacePlayer.discard()
            surface.applySettings(settings)
        }
        helper.succeed()
    }
}
