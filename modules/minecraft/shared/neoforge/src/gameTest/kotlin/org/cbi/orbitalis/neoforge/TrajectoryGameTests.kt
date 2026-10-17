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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.util.Unit
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.ShipCoastForecast
import org.cbi.orbitalis.minecraft.navigation.NavigationBookmarks
import org.cbi.orbitalis.minecraft.navigation.TrajectoryPayload
import org.joml.Vector3d
import java.util.UUID
import java.util.concurrent.CompletableFuture

@PrefixGameTestTemplate(false)
object TrajectoryGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "navigation", timeoutTicks = 300)
    fun nativeCoastForecastAndPacket(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val physics = SubLevelPhysicsSystem.require(level)
        val pose = Pose3d()
        pose.position().set(0.0, 5600.0, 0.0)
        val body = container.allocateNewSubLevel(pose) as ServerSubLevel
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testPassed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()

                override fun testFailed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) = cleanup()

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}

                fun cleanup() {
                    if (!body.isRemoved) {
                        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                    }
                }
            },
        )
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
        body.updateLastPose()
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)

        fun start() {
            level.resetEmptyTime()
            if (body.massTracker.centerOfMass == null) {
                helper.runAfterDelay(1) { start() }
                return
            }
            val sample = SableFlightGravity.reading(body)
            val target = FlightSpaceCoordinates.localPosition(FlatPlanetSpace.SPACE, Vector3(0.0, 1500.0, 0.0))
            val delta = target - sample.position
            val handle = physics.getPhysicsHandle(body)
            handle.teleport(Vector3d(body.logicalPose().position()).add(delta.x, delta.y, delta.z), body.logicalPose().orientation())
            handle.addLinearAndAngularVelocity(Vector3d(sample.velocity).negate(), Vector3d())
            helper.runAfterDelay(1) {
                val request = checkNotNull(ShipCoastForecast.capture(body))
                val future = CompletableFuture.supplyAsync { request.calculate() }
                helper.runAfterDelay(30) {
                    val actual = SableFlightGravity.reading(body)
                    val physical = SableFlightGravity.globalPosition(level, actual.position)

                    fun finish() {
                        level.resetEmptyTime()
                        if (!future.isDone) {
                            helper.runAfterDelay(1) { finish() }
                            return
                        }
                        val result = future.join()
                        helper.assertTrue(
                            result.ship == body.uniqueId && result.dimension == FlatPlanetSpace.SPACE.location(),
                            "Forecast identity/frame changed",
                        )
                        helper.assertTrue(
                            result.prediction.points
                                .first()
                                .state.position.y < 2000.0,
                            "Native space translation leaked into physical forecast",
                        )
                        val predicted =
                            result.prediction.points
                                .minBy { kotlin.math.abs(it.seconds - 1.5) }
                                .state
                        helper.assertTrue(
                            (predicted.position - physical).length() < 0.3,
                            "Native coast disagrees with forecast: $physical vs " + predicted.position,
                        )
                        helper.assertTrue(
                            (predicted.velocity - Vector3(actual.velocity.x, actual.velocity.y, actual.velocity.z)).length() < 0.15,
                            "Native velocity disagrees with forecast",
                        )
                        val buffer = FriendlyByteBuf(Unpooled.buffer())
                        try {
                            val payload = TrajectoryPayload(result, Vector3(10.0, 80.0, 20.0))
                            TrajectoryPayload.CODEC.encode(buffer, payload)
                            helper.assertTrue(buffer.readableBytes() < 40000, "Trajectory packet exceeds budget")
                            helper.assertTrue(
                                TrajectoryPayload.CODEC.decode(buffer) == payload && !buffer.isReadable,
                                "Forecast packet lost state",
                            )
                            buffer.clear()
                            TrajectoryPayload.CODEC.encode(buffer, TrajectoryPayload(null))
                            helper.assertTrue(TrajectoryPayload.CODEC.decode(buffer) == TrajectoryPayload(null), "Clear packet broken")
                            buffer.clear()
                            buffer
                                .writeBoolean(
                                    false,
                                ).writeBoolean(true)
                                .writeUUID(body.uniqueId)
                                .writeResourceLocation(result.dimension)
                                .writeLong(0)
                            buffer.writeVarInt(0).writeVarInt(603)
                            var rejected = false
                            try {
                                TrajectoryPayload.CODEC.decode(buffer)
                            } catch (error: IllegalArgumentException) {
                                rejected = true
                            }
                            helper.assertTrue(rejected, "Oversized packet accepted")
                        } finally {
                            buffer.release()
                        }
                        println(
                            "ORBITALIS_COAST_FORECAST nativeError=" + (predicted.position - physical).length() + " points=" +
                                result.prediction.points.size,
                        )
                        helper.succeed()
                    }
                    finish()
                }
            }
        }
        helper.runAfterDelay(5) { start() }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "navigation", timeoutTicks = 60)
    fun navigationBookmarkPersistence(helper: GameTestHelper) {
        val registries = helper.level.registryAccess()
        val pilot = UUID.randomUUID()
        val position = Vector3(123.0, 80.0, -456.0)
        val markers = NavigationBookmarks.get(helper.level.server)
        markers.put(pilot, position)
        helper.level.server
            .overworld()
            .dataStorage
            .save()
        net.neoforged.neoforge.common.IOUtilities
            .waitUntilIOWorkerComplete()
        val storage =
            net.minecraft.world.level.storage.DimensionDataStorage(
                helper.level.server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data")
                    .toFile(),
                helper.level.server.fixerUpper,
                registries,
            )
        val loaded =
            storage.computeIfAbsent(
                net.minecraft.world.level.saveddata.SavedData
                    .Factory(::NavigationBookmarks, NavigationBookmarks::load, null),
                "orbitalis_navigation_markers",
            )
        helper.assertTrue(loaded.get(pilot) == position, "Navigation marker did not survive native save")
        val saved = loaded.save(CompoundTag(), registries)
        for (version in listOf(0, 2)) {
            val malformed = saved.copy()
            malformed.putInt("version", version)
            var rejected = false
            try {
                NavigationBookmarks.load(malformed, registries)
            } catch (error: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Unknown marker version accepted")
        }
        val malformed = saved.copy()
        val entries = malformed.get("markers") as ListTag
        entries.getCompound(entries.size - 1).putDouble("y", Double.NaN)
        var rejected = false
        try {
            NavigationBookmarks.load(malformed, registries)
        } catch (error: IllegalArgumentException) {
            rejected = true
        }
        helper.assertTrue(rejected, "Nonfinite marker accepted")
        markers.clear(pilot)
        helper.succeed()
    }
}
