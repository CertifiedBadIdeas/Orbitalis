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
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.joml.Vector3d

/** Probe the pinned native backend before selecting the production orbital policy. */
@PrefixGameTestTemplate(false)
object SparseSpaceGameTests {
    private class Fixture(
        helper: GameTestHelper,
        ignoreTerrain: Boolean,
    ) {
        val level: ServerLevel = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val physics = SubLevelPhysicsSystem.require(level)
        val bodies = mutableListOf<ServerSubLevel>()
        private val previous = DimensionPhysicsData.of(level)

        init {
            val policy = previous
            DimensionPhysicsData.putPhysics(
                level.dimension(),
                DimensionPhysics(
                    policy.dimension(),
                    policy.priority(),
                    policy.universalDrag(),
                    policy.baseGravity(),
                    policy.basePressure(),
                    policy.pressureFunction(),
                    policy.magneticNorth(),
                    ignoreTerrain,
                ),
            )
            helper.testInfo.addListener(
                object : GameTestListener {
                    override fun testStructureLoaded(test: GameTestInfo) {}

                    override fun testPassed(
                        test: GameTestInfo,
                        runner: GameTestRunner,
                    ) = close()

                    override fun testFailed(
                        test: GameTestInfo,
                        runner: GameTestRunner,
                    ) {
                        test.error?.printStackTrace()
                        close()
                    }

                    override fun testAddedForRerun(
                        test: GameTestInfo,
                        retry: GameTestInfo,
                        runner: GameTestRunner,
                    ) {}
                },
            )
        }

        fun body(
            x: Double,
            z: Double,
        ): ServerSubLevel {
            val pose = Pose3d().also { it.position().set(x, 6000.0, z) }
            val body = container.allocateNewSubLevel(pose) as ServerSubLevel
            bodies += body
            body.plot.newEmptyChunk(body.plot.centerChunk)
            for (dx in 0..2) {
                for (dy in 0..2) {
                    for (dz in 0..2) {
                        body.plot.embeddedLevelAccessor.setBlock(BlockPos(dx, dy, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3)
                    }
                }
            }
            body.updateMergedMassData(1.0f)
            body.updateBoundingBox()
            body.forceUpdateGlobalBounds()
            body.updateLastPose()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            return body
        }

        fun close() {
            bodies.filterNot { it.isRemoved }.forEach {
                container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
            }
            DimensionPhysicsData.putPhysics(level.dimension(), previous)
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 200, batch = "sparse_control")
    fun nativeTerrainLoadingControl(helper: GameTestHelper) = terrain(helper, false, 30000.0)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 200, batch = "sparse_vacuum")
    fun nativeVacuumFlightWithoutTerrain(helper: GameTestHelper) = terrain(helper, true, -30000.0)

    private fun terrain(
        helper: GameTestHelper,
        ignore: Boolean,
        z: Double,
    ) {
        val fixture = Fixture(helper, ignore)
        val body = fixture.body(30000.0, z)
        var started = false
        var ticks = 0

        fun tick() {
            fixture.level.resetEmptyTime()
            if (body.massTracker.centerOfMass != null && !started) {
                fixture.physics.getPhysicsHandle(body).addLinearAndAngularVelocity(Vector3d(40.0, 0.0, 0.0), Vector3d())
                started = true
            }
            if (started && ++ticks >= 60) {
                val x = body.logicalPose().position().x
                helper.assertTrue(x > 30100.0, "Native body did not advance through vacuum: $x")
                val terrainLoaded =
                    fixture.level.chunkSource.hasChunk(
                        kotlin.math.floor(x).toInt() shr 4,
                        kotlin.math.floor(body.logicalPose().position().z).toInt() shr 4,
                    )
                helper.assertTrue(terrainLoaded != ignore, "Terrain loading policy disagrees: ignore=$ignore loaded=$terrainLoaded")
                println(
                    "ORBITALIS_SPARSE_PROBE ignore=$ignore distance=${x - 30000.0} holders=${fixture.level.chunkSource.loadedChunksCount}",
                )
                helper.succeed()
            } else {
                helper.runAfterDelay(1) { tick() }
            }
        }
        tick()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 200, batch = "sparse_collision")
    fun nativeVacuumShipCollision(helper: GameTestHelper) {
        val fixture = Fixture(helper, true)
        val left = fixture.body(40000.0, 40000.0)
        val right = fixture.body(40010.0, 40000.0)
        var started = false
        var ticks = 0

        fun tick() {
            fixture.level.resetEmptyTime()
            if (!started && fixture.bodies.all { it.massTracker.centerOfMass != null }) {
                fixture.physics.getPhysicsHandle(left).addLinearAndAngularVelocity(Vector3d(2.0, 0.0, 0.0), Vector3d())
                fixture.physics.getPhysicsHandle(right).addLinearAndAngularVelocity(Vector3d(-2.0, 0.0, 0.0), Vector3d())
                started = true
            }
            if (started && ++ticks >= 100) {
                val separation = right.logicalPose().position().x - left.logicalPose().position().x
                val closing =
                    fixture.physics
                        .getPhysicsHandle(left)
                        .getLinearVelocity(Vector3d())
                        .x -
                        fixture.physics
                            .getPhysicsHandle(right)
                            .getLinearVelocity(Vector3d())
                            .x
                helper.assertTrue(separation > 2.5, "Native ships passed through each other: $separation")
                helper.assertTrue(closing < 0.5, "Native impact did not arrest relative velocity: $closing")
                println("ORBITALIS_SPARSE_COLLISION separation=$separation closing=$closing")
                helper.succeed()
            } else {
                helper.runAfterDelay(1) { tick() }
            }
        }
        tick()
    }
}
