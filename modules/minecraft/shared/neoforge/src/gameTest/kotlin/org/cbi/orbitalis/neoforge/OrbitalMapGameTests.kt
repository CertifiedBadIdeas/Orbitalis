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

import io.netty.buffer.Unpooled
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.ManeuverNode
import org.cbi.orbitalis.core.planet.ManeuverPlan
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableMapObjects
import org.cbi.orbitalis.minecraft.integration.sable.SableOrbitalObjects
import org.cbi.orbitalis.minecraft.map.MapObject
import org.cbi.orbitalis.minecraft.map.MapRequest
import org.cbi.orbitalis.minecraft.map.MapSnapshot
import org.cbi.orbitalis.minecraft.map.OrbitalMapService
import org.cbi.orbitalis.minecraft.navigation.FlightPlanCommand
import org.cbi.orbitalis.minecraft.navigation.FlightPlanService
import org.cbi.orbitalis.minecraft.navigation.FlightPlanState
import org.cbi.orbitalis.minecraft.navigation.FlightPlans
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData
import java.util.UUID

@PrefixGameTestTemplate(false)
object OrbitalMapGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "map")
    fun mapPacketAndLease(helper: GameTestHelper) {
        val buffer = FriendlyByteBuf(Unpooled.buffer())
        val token = UUID.randomUUID()
        try {
            val request = MapRequest(token, true)
            MapRequest.CODEC.encode(buffer, request)
            helper.assertTrue(MapRequest.CODEC.decode(buffer) == request, "Request token lost")
            buffer.clear()
            val objects =
                List(
                    MapSnapshot.MAX_OBJECTS,
                ) { MapObject(UUID.randomUUID(), "界".repeat(32), Vector3(1.0, 8000.0, 3.0), Vector3.ZERO, false, 0) }
            val value = MapSnapshot(token, 0, Vector3.ZERO, objects, objects.size, 7.5)
            MapSnapshot.CODEC.encode(buffer, value)
            helper.assertTrue(buffer.writerIndex() < 1048576, "Snapshot exceeded native client payload budget")
            helper.assertTrue(MapSnapshot.CODEC.decode(buffer) == value, "Object snapshot roundtrip changed")
            for (gravity in listOf(Double.NaN, -1.0, 0.0, Double.POSITIVE_INFINITY)) {
                var rejectedGravity = false
                try {
                    value.copy(gravity = gravity)
                } catch (_: IllegalArgumentException) {
                    rejectedGravity = true
                }
                helper.assertTrue(rejectedGravity, "Invalid gravity accepted: $gravity")
            }
            buffer.clear()
            buffer.writeUUID(token)
            buffer.writeLong(0)
            repeat(3) { buffer.writeDouble(0.0) }
            buffer.writeDouble(11.0)
            buffer.writeVarInt(999999)
            buffer.writeVarInt(999999)
            var rejected = false
            try {
                MapSnapshot.CODEC.decode(buffer)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Oversized object list accepted")
            rejected = false
            try {
                MapSnapshot(token, 0, Vector3.ZERO, listOf(objects[0], objects[0]), 2)
            } catch (_: IllegalArgumentException) {
                rejected =
                    true
            }
            helper.assertTrue(rejected, "Duplicate object identities accepted")
        } finally {
            buffer.release()
        }
        val ship = UUID.randomUUID()
        val owner = UUID.randomUUID()
        val plan =
            ManeuverPlan(
                helper.level.server
                    .overworld()
                    .gameTime,
                List(8) { ManeuverNode((it + 1) * 60.0, 10.0, -3.0, 2.0) },
            )
        val command = FlightPlanCommand(token, ship, FlightPlanCommand.Action.SAVE, 0, plan)
        val wire = FriendlyByteBuf(Unpooled.buffer())
        try {
            FlightPlanCommand.CODEC.encode(wire, command)
            helper.assertTrue(wire.writerIndex() < 512 && FlightPlanCommand.CODEC.decode(wire) == command, "Plan command round trip failed")
            wire.clear()
            val state = FlightPlanState(token, ship, plan.epochTick, 1, plan, true, false, "test")
            FlightPlanState.CODEC.encode(wire, state)
            helper.assertTrue(FlightPlanState.CODEC.decode(wire) == state, "Plan state round trip failed")
            wire.clear()
            wire.writeUUID(token)
            wire.writeUUID(ship)
            wire.writeEnum(FlightPlanCommand.Action.SAVE)
            wire.writeLong(0)
            wire.writeLong(0)
            wire.writeVarInt(9)
            var rejected = false
            try {
                FlightPlanCommand.CODEC.decode(wire)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Unbounded node count accepted")
        } finally {
            wire.release()
        }
        val plans = FlightPlans()
        plans.put(owner, ship, 0, plan)
        val saved = plans.save(CompoundTag(), helper.level.registryAccess())
        val restored = FlightPlans.load(saved, helper.level.registryAccess())
        helper.assertTrue(
            restored.get(owner, ship)?.plan == plan && restored.get(UUID.randomUUID(), ship) == null,
            "Personal flight plan save lost ownership or nodes",
        )
        var stale = false
        try {
            restored.put(owner, ship, 0, plan)
        } catch (_: IllegalArgumentException) {
            stale = true
        }
        helper.assertTrue(stale, "Stale revision overwrote plan")
        restored.put(owner, ship, 1, plan.copy(nodes = emptyList()))
        helper.assertTrue(restored.get(owner, ship)?.revision == 2L, "Cleared plan lost revision")
        for (bad in listOf(
            saved.copy().also {
                it.putInt("version", 99)
            },
            saved.copy().also { ((it.get("plans") as net.minecraft.nbt.ListTag)[0] as CompoundTag).putLong("epoch", -1) },
        )) {
            var rejected = false
            try {
                FlightPlans.load(bad, helper.level.registryAccess())
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Malformed persisted flight plan accepted")
        }
        val player = GameTestPlayers.create(helper)
        val snapshots = mutableListOf<MapSnapshot>()
        val service =
            OrbitalMapService(helper.level.server, { _, payload -> snapshots.add(payload) }, {
                if (it ==
                    player.uuid
                ) {
                    player
                } else {
                    null
                }
            })
        try {
            helper.assertTrue(FlightPlanService.handle(player, command, service) == null, "Plan write without subscription accepted")
            service.request(player, MapRequest(token, true))
            helper.assertTrue(
                FlightPlanService.handle(player, command.copy(token = UUID.randomUUID()), service) == null,
                "Wrong screen token accepted",
            )
            val denied = FlightPlanService.handle(player, command, service)
            helper.assertTrue(
                denied?.error?.isNotEmpty() == true && FlightPlans.get(helper.level.server).get(player.uuid, ship) == null,
                "Unauthorised plan changed server state",
            )
            service.tick()
            helper.assertTrue(
                service.viewing(player) && snapshots.size == 1 && snapshots[0].token == token,
                "Surface map subscription failed",
            )
            repeat(9) { service.tick() }
            helper.assertTrue(snapshots.size == 1, "Map bypassed snapshot rate limit")
            val next = UUID.randomUUID()
            service.request(player, MapRequest(next, true))
            service.request(player, MapRequest(token, false))
            helper.assertTrue(service.viewing(player), "Stale close cancelled a newer screen")
            service.tick()
            helper.assertTrue(snapshots.last().token == next, "New screen received an old token")
            repeat(100) { service.tick() }
            helper.assertTrue(!service.viewing(player), "Abandoned map did not expire")
            service.request(player, MapRequest(next, true))
            service.request(player, MapRequest(next, false))
            helper.assertTrue(!service.viewing(player), "Closed map remained subscribed")
            service.request(player, MapRequest(next, true))
            service.logout(player.uuid)
            helper.assertTrue(!service.viewing(player), "Logout retained map subscription")
        } finally {
            player.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "map", timeoutTicks = 100)
    fun mapNativeOrbitalObjects(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val container =
            checkNotNull(
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                    .getContainer(level),
            )
        val pose =
            dev.ryanhcode.sable.companion.math
                .Pose3d()
                .also { it.position().set(12000.0, 15096.0, 13000.0) }
        val body = container.allocateNewSubLevel(pose) as dev.ryanhcode.sable.sublevel.ServerSubLevel
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(
            net.minecraft.core.BlockPos.ZERO,
            net.minecraft.world.level.block.Blocks.STONE
                .defaultBlockState(),
            3,
        )
        body.updateMergedMassData(1.0f)
        body.updateBoundingBox()
        body.forceUpdateGlobalBounds()
        body.updateLastPose()
        body.name = "Map native fixture"
        val data = SableOrbitalObjects.data(level)
        val cold = UUID.randomUUID()
        helper.runAfterDelay(10) {
            try {
                val stale = Vector3(-2000.0, 10000.0, 3000.0)
                data.put(
                    OrbitalObjectData.Record(
                        body.uniqueId,
                        UUID.randomUUID(),
                        data.clock,
                        data.clock,
                        CartesianState(stale, Vector3.ZERO),
                        1.0,
                    ),
                )
                data.put(
                    OrbitalObjectData.Record(
                        cold,
                        UUID.randomUUID(),
                        data.clock,
                        data.clock,
                        CartesianState(stale, Vector3(0.0, 0.0, 50.0)),
                        1.0,
                    ),
                )
                val before = container.allSubLevels.size
                val terrain = level.chunkSource.getLoadedChunksCount()
                val objects = SableMapObjects.snapshot(helper.level.server)
                val live = objects.single { it.id == body.uniqueId }
                helper.assertTrue(
                    live.loaded && live.name == body.name && live.position != stale && live.position.y < 14000.0,
                    "Native state did not override stale index or used space coordinates",
                )
                val unloaded = objects.single { it.id == cold }
                helper.assertTrue(
                    !unloaded.loaded && unloaded.position == stale && unloaded.velocity.z == 50.0,
                    "Unloaded motion index lost",
                )
                helper.assertTrue(
                    container.getSubLevel(cold) == null && container.allSubLevels.size == before &&
                        level.chunkSource.getLoadedChunksCount() == terrain,
                    "Map loaded terrain or a dormant native body",
                )
                helper.succeed()
            } finally {
                data.remove(cold)
                data.remove(body.uniqueId)
                if (!body.isRemoved) container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
            }
        }
    }
}
