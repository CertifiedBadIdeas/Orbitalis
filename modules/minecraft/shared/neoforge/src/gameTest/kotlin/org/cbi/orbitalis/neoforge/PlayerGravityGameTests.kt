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

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.PlayerGravity
import kotlin.math.abs

@PrefixGameTestTemplate(false)
class PlayerGravityGameTests {
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100)
    fun playerVacuumInertia(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val space = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        try {
            player.teleportTo(space, 10000.0, 12096.0, 0.0, emptySet(), 0f, 0f)
            player.setGameMode(GameType.CREATIVE)
            player.abilities.flying = true
            player.setOnGround(false)
            val velocity = Vec3(1.0, 0.5, -0.25)
            player.deltaMovement = velocity
            repeat(20) { player.travel(Vec3.ZERO) }
            helper.assertTrue(player.deltaMovement.distanceTo(velocity) < 1e-10, "Creative flight lost momentum in vacuum")
            helper.assertTrue(abs(player.x - 10020.0) < 1e-8 && abs(player.y - 12106.0) < 1e-8, "Creative travel displacement changed")

            val tinyVelocity = Vec3(0.0002, 0.0001, -0.0002)
            player.deltaMovement = tinyVelocity
            player.aiStep()
            helper.assertTrue(player.deltaMovement.distanceTo(tinyVelocity) < 1e-10, "Native rest threshold erased weak vacuum motion")

            player.setGameMode(GameType.SURVIVAL)
            player.abilities.flying = false
            player.deltaMovement = velocity
            player.startFallFlying()
            val before = PlayerGravity.acceleration(player)
            player.travel(Vec3(1.0, 0.0, 1.0))
            val after = PlayerGravity.acceleration(player)
            val expected = velocity.add(before.scale(0.5)).add(after.scale(0.5))
            helper.assertTrue(player.deltaMovement.distanceTo(expected) < 1e-9, "Vacuum retained elytra lift, air control or drag")
            helper.assertTrue(before.x < 0.0 && before.y < 0.0, "Player is not attracted toward the body")
        } finally {
            player.discard()
        }
        helper.succeed()
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100)
    fun playerSurfaceAndDistantGravity(helper: GameTestHelper) {
        val buffer =
            net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled
                    .buffer(),
            )
        try {
            val payload =
                org.cbi.orbitalis.minecraft.flight
                    .PlayerGravityPayload(
                        11.0,
                        500,
                        org.cbi.orbitalis.core.orbit
                            .SolarSystemProfile(11.0, 400),
                        42,
                        false,
                    )
            org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                .encode(buffer, payload)
            helper.assertTrue(
                org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                    .decode(buffer) == payload,
                "Gravity strength changed in transit",
            )
            buffer.clear().writeDouble(Double.NaN)
            buffer.writeLong(0)
            buffer.writeLong(0)
            buffer.writeBoolean(true)
            buffer.writeBoolean(false)
            helper.assertTrue(
                runCatching {
                    org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                        .decode(buffer)
                }.isFailure,
                "Nonfinite client gravity was accepted",
            )
        } finally {
            buffer.release()
        }

        val player = GameTestPlayers.create(helper, locallyControlled = true)
        try {
            player.setGameMode(GameType.SURVIVAL)
            player.setPos(4500.0, 100.0, 4500.0)
            val near = PlayerGravity.acceleration(player)
            helper.assertTrue(abs(near.x) < 1e-12 && abs(near.z) < 1e-12 && near.y < 0.0, "Surface gravity is not vertical")
            helper.assertTrue(abs(near.y + 0.08) < 1e-10, "Default surface player gravity differs from Minecraft")
            val vanilla = GameTestPlayers.create(helper, locallyControlled = true)
            try {
                vanilla.teleportTo(
                    checkNotNull(helper.level.server.getLevel(net.minecraft.world.level.Level.NETHER)),
                    0.0,
                    10000.0,
                    0.0,
                    emptySet(),
                    0f,
                    0f,
                )
                vanilla.setGameMode(GameType.SURVIVAL)
                player.setOnGround(false)
                vanilla.setOnGround(false)
                player.deltaMovement = Vec3(0.0, 0.42, 0.0)
                vanilla.deltaMovement = player.deltaMovement
                repeat(20) {
                    player.travel(Vec3.ZERO)
                    vanilla.travel(Vec3.ZERO)
                    helper.assertTrue(
                        player.deltaMovement.distanceTo(vanilla.deltaMovement) < 0.005,
                        "Surface spin changed jump velocity beyond the small Coriolis correction",
                    )
                    helper.assertTrue(
                        abs((player.y - 100.0) - (vanilla.y - 10000.0)) < 0.02,
                        "Surface jump height differs from native Minecraft",
                    )
                }
            } finally {
                vanilla.discard()
            }
            val sample =
                org.cbi.orbitalis.minecraft.flight.CelestialFlight
                    .sample(player.level())
            val frame = sample.frames.profile.earthSurfaceFrame(sample.frames.tick)

            fun physical(position: org.cbi.orbitalis.core.planet.Vector3): org.cbi.orbitalis.core.planet.Vector3 {
                player.setPos(position.x, position.y, position.z)
                player.deltaMovement = Vec3.ZERO
                val a = PlayerGravity.acceleration(player)
                return frame.toParentAcceleration(
                    org.cbi.orbitalis.core.planet
                        .CartesianState(position, org.cbi.orbitalis.core.planet.Vector3.ZERO),
                    org.cbi.orbitalis.core.planet
                        .Vector3(a.x, a.y, a.z) * 400.0,
                )
            }
            val position =
                org.cbi.orbitalis.core.planet
                    .Vector3(10000.0, 10000.0, 0.0)
            val far = physical(position)
            val farther = physical(position * 2.0)
            helper.assertTrue(
                far.dot(frame.orientation.apply(position)) < 0.0 && farther.length() < far.length(),
                "Inertial gravity failed to weaken with distance",
            )
        } finally {
            player.discard()
        }
        helper.succeed()
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100)
    fun vacuumMovementPackets(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper)
        val space = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        try {
            player.teleportTo(space, 10000.0, 12096.0, 0.0, emptySet(), 0f, 0f)
            GameTestPlayers.acknowledgeTeleport(player)
            player.hasChangedDimension()
            player.setGameMode(GameType.SURVIVAL)
            player.deltaMovement = Vec3.ZERO
            // Native accepted movement is the reference; do not simply disable the speed check.
            player.setKnownMovement(Vec3(12.0, 0.0, 0.0))
            space.getChunk(625, 0)
            player.connection.resetPosition()
            player.connection.handleMovePlayer(
                net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(
                    10012.0,
                    12096.0,
                    0.0,
                    false,
                ),
            )
            helper.assertTrue(abs(player.x - 10012.0) < 1e-8, "Native validation rejected accepted vacuum momentum")
            val floating = net.minecraft.server.network.ServerGamePacketListenerImpl::class.java.getDeclaredField("clientIsFloating")
            floating.isAccessible = true
            helper.assertTrue(!floating.getBoolean(player.connection), "Vacuum coast was flagged as illegal hovering")
            player.connection.resetPosition()
            player.connection.handleMovePlayer(
                net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(
                    10512.0,
                    12096.0,
                    0.0,
                    false,
                ),
            )
            helper.assertTrue(abs(player.x - 10012.0) < 1e-8, "Vacuum adaptation disabled the native speed check")
        } finally {
            player.discard()
        }
        helper.succeed()
    }
}
