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

package org.cbi.orbitalis.consumer.flight

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

@PrefixGameTestTemplate(false)
object ShuttleGameTests {
    /** Uses the actual player blueprint and native assembler, including Create glue. */
    private class Shuttle(
        helper: GameTestHelper,
        val level: ServerLevel,
    ) : AutoCloseable {
        val origin = BlockPos(1400, 180, 8)
        val previous = mutableSetOf<UUID>()
        val owned = mutableSetOf<UUID>()
        val forcedChunks = mutableListOf<Long>()
        val launchChunks = mutableListOf<Long>()
        val glue = mutableSetOf<UUID>()
        var closed = false
        lateinit var body: ServerSubLevel
        var session: AutopilotSession? = null

        init {
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            for (existing in container.allSubLevels) previous.add(existing.uniqueId)
            onFinished(helper, ::close)
            for (x in (origin.x - 10 shr 4)..(origin.x + 10 shr 4)) {
                for (z in (origin.z - 10 shr 4)..(origin.z + 10 shr 4)) {
                    val key = ChunkPos.asLong(x, z)
                    launchChunks.add(key)
                    if (!level.forcedChunks.contains(key)) {
                        level.setChunkForced(x, z, true)
                        forcedChunks.add(key)
                    }
                    level.getChunk(x, z)
                }
            }
        }

        fun ready(): Boolean {
            level.resetEmptyTime()
            return launchChunks.all(level::areEntitiesLoaded) && level.isPositionEntityTicking(origin)
        }

        fun assemble(helper: GameTestHelper) {
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            val box = AABB(Vec3.atLowerCornerOf(origin.offset(-10, 0, -10)), Vec3.atLowerCornerOf(origin.offset(11, 11, 11)))
            // The disposable GameTest launch area may contain data from an earlier failed run.
            level.getEntitiesOfClass(SuperGlueEntity::class.java, box).forEach { it.discard() }
            val source =
                level.server
                    .createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(Vec3.atLowerCornerOf(origin))
                    .withSuppressedOutput()
            level.server.commands.performPrefixedCommand(source, "fill ~-10 ~ ~-10 ~10 ~10 ~10 minecraft:air")
            level.server.commands.performPrefixedCommand(source, "function orbitalis_flight_test:shuttle")
            level.getEntitiesOfClass(SuperGlueEntity::class.java, box).forEach { glue.add(it.uuid) }
            helper.assertTrue(glue.size == 1, "Fixture glue missing after entity chunks became ready")
            helper.assertTrue(
                SuperGlueEntity.isGlued(level, origin.offset(0, 3, 0), Direction.EAST, null),
                "Blueprint glue does not bind cabin floor",
            )
            val assembler = level.getBlockEntity(origin.offset(0, 4, 0))
            helper.assertTrue(assembler is PhysicsAssemblerBlockEntity, "Fixture assembler missing")
            (assembler as PhysicsAssemblerBlockEntity).assembleOrDisassemble()
            val created = container.allSubLevels.filter { it.uniqueId !in previous }
            helper.assertTrue(created.size == 1, "Native fixture assembly did not create one ship")
            body = created.first() as ServerSubLevel
            created.forEach { owned.add(it.uniqueId) }
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        }

        fun checkLayout(helper: GameTestHelper) {
            helper.assertTrue(!body.isRemoved, "Native connectivity removed/split the fixture")
            val blocks = body.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }
            val engines = blocks.filterIsInstance<CreativeThrusterBlockEntity>()
            helper.assertTrue(engines.size == 24, "Fixture lost engines: ${engines.size}")
            helper.assertTrue(engines.all { it.isController }, "Native connectivity merged independent engine controllers")
            val cargo = blocks.filterIsInstance<ChestBlockEntity>()
            helper.assertTrue(
                cargo.size == 4 && cargo.all { it.getItem(0).count == 16 && it.getItem(0).`is`(Items.COBBLESTONE) },
                "Fixture lost cargo sentinels",
            )
            helper.assertTrue(
                checkNotNull(SubLevelContainer.getContainer(level)).allSubLevels.count { it.uniqueId !in previous } == 1,
                "Fixture split into multiple bodies",
            )
        }

        override fun close() {
            if (closed) return
            closed = true
            session?.close()
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            for (b in container.allSubLevels.toList()) {
                if (b.uniqueId in owned || b.splitFromSubLevel in owned) {
                    container.removeForceLoadTicket(b, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    container.removeSubLevel(b, SubLevelRemovalReason.REMOVED)
                }
            }
            for (id in glue) level.getEntity(id)?.discard()
            for (key in forcedChunks) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false)
            forcedChunks.clear()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 1000, batch = "shuttle_hold")
    fun shuttleAssemblyAndHold(helper: GameTestHelper) {
        val shuttle = Shuttle(helper, checkNotNull(helper.level.server.getLevel(FlightTestSpace.SURFACE)))
        val physics = SubLevelPhysicsSystem.require(shuttle.level)

        class Sequence : Runnable {
            var tick = 0
            var assembledAt = 0
            lateinit var attitude: Quaterniond

            override fun run() {
                shuttle.level.resetEmptyTime()
                tick++
                if (assembledAt == 0) {
                    helper.assertTrue(tick < 200, "Timed out waiting for native entity chunks")
                    if (shuttle.ready()) {
                        shuttle.assemble(helper)
                        assembledAt = tick
                    }
                    helper.runAfterDelay(1) { run() }
                    return
                }
                val flightTick = tick - assembledAt
                if (flightTick == 10) {
                    shuttle.checkLayout(helper)
                    val start = AutopilotSession.position(shuttle.body)
                    attitude = Quaterniond(shuttle.body.logicalPose().orientation())
                    shuttle.session = AutopilotSession(shuttle.body, Vector3(start.x, start.y + 16, start.z))
                    // Initial test disturbance only; installed engines power all recovery.
                    val handle = physics.getPhysicsHandle(shuttle.body)
                    val mass = shuttle.body.massTracker.mass
                    handle.applyLinearImpulse(Vector3d(mass * 2, 0.0, mass))
                    handle.applyAngularImpulse(Vector3d(mass * 0.1, mass * 0.15, mass * 0.1))
                }
                if (flightTick >= 10) shuttle.session!!.update()
                if (flightTick == 800) {
                    shuttle.checkLayout(helper)
                    val handle = physics.getPhysicsHandle(shuttle.body)
                    helper.assertTrue(
                        (AutopilotSession.position(shuttle.body) - shuttle.session!!.target).length() < 1,
                        "Shuttle ascent/hold failed: ${shuttle.session!!.status()}",
                    )
                    helper.assertTrue(handle.getLinearVelocity(Vector3d()).length() < 0.4, "Shuttle did not settle translation")
                    helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.04, "Shuttle did not settle rotation")
                    val angle = Quaterniond(shuttle.body.logicalPose().orientation()).conjugate().mul(attitude).angle()
                    helper.assertTrue(min(angle, PI * 2 - angle) < 0.08, "Shuttle attitude did not recover")
                    println("ORBITALIS_SHUTTLE ${shuttle.session!!.status()}")
                    helper.succeed()
                    return
                }
                helper.runAfterDelay(1) { run() }
            }
        }
        helper.runAfterDelay(1, Sequence())
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 600, batch = "shuttle_gravity")
    fun shuttleGravityProfile(helper: GameTestHelper) {
        val server = helper.level.server
        val surface = Shuttle(helper, checkNotNull(server.getLevel(FlightTestSpace.SURFACE)))
        val orbit = Shuttle(helper, checkNotNull(server.getLevel(FlightTestSpace.ORBIT)))
        val craft = listOf(surface, surface, surface, surface, orbit)
        val points =
            listOf(
                Vector3(0.0, 240.0, 0.0),
                Vector3(1600.0, 240.0, 0.0),
                Vector3(1600.0, 2064.0, 0.0),
                Vector3(1600.0, 6064.0, 0.0),
                Vector3(1600.0, 5808.0, 0.0),
            )
        val measured = mutableListOf<Vector3>()

        class Trial : Runnable {
            var index = 0

            override fun run() {
                val shuttle = craft[index]
                shuttle.checkLayout(helper)
                val handle = SubLevelPhysicsSystem.require(shuttle.level).getPhysicsHandle(shuttle.body)
                // Arrange trial initial conditions; no state corrections during measurement.
                val delta = points[index] - AutopilotSession.position(shuttle.body)
                handle.teleport(
                    Vector3d(shuttle.body.logicalPose().position()).add(delta.x, delta.y, delta.z),
                    shuttle.body.logicalPose().orientation(),
                )
                handle.addLinearAndAngularVelocity(
                    handle.getLinearVelocity(Vector3d()).negate(),
                    handle.getAngularVelocity(Vector3d()).negate(),
                )
                helper.runAfterDelay(8) {
                    val position = AutopilotSession.position(shuttle.body)
                    val global = FlightTestSpace.globalPosition(shuttle.level, position)
                    val expected = FlightTestSpace.gravity.acceleration(global - FlightTestSpace.CENTRE)
                    val velocity = handle.getLinearVelocity(Vector3d())
                    helper.runAfterDelay(20) {
                        val actual = AutopilotSession.vector(handle.getLinearVelocity(Vector3d()).sub(velocity))
                        // Exactly one second of native integration; field variation is tiny over this fall.
                        helper.assertTrue(
                            (actual - expected).length() < 0.05,
                            "Native gravity mismatch at $global: measured=$actual, field=$expected",
                        )
                        helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.01, "Gravity introduced torque")
                        helper.assertTrue(
                            shuttle.body.plot.loadedChunks
                                .flatMap { it.chunk.blockEntities.values }
                                .filterIsInstance<CreativeThrusterBlockEntity>()
                                .all { it.currentThrust == 0.0f },
                            "Free-fall trial has engine output",
                        )
                        measured.add(actual)
                        println(
                            "ORBITALIS_SHUTTLE_GRAVITY frame=${shuttle.level.dimension().location()} global=$global field=$expected measured=$actual",
                        )
                        if (++index < points.size) {
                            helper.runAfterDelay(1) { run() }
                        } else {
                            helper.assertTrue(abs(measured[1].x) < 0.02, "Near-surface offset produced lateral gravity")
                            helper.assertTrue(measured[2].x < -0.01, "Transition field lacks centreward lateral force")
                            helper.assertTrue(measured[3].length() < measured[1].length() * 0.5, "High field did not weaken")
                            helper.assertTrue((measured[3] - measured[4]).length() < 0.03, "Frame translation changed gravity")
                            helper.succeed()
                        }
                    }
                }
            }
        }

        class Prepare : Runnable {
            var attempts = 0

            override fun run() {
                helper.assertTrue(++attempts < 200, "Timed out waiting for native entity chunks")
                if (surface.ready() && orbit.ready()) {
                    surface.assemble(helper)
                    orbit.assemble(helper)
                    helper.runAfterDelay(10, Trial())
                } else {
                    helper.runAfterDelay(1) { run() }
                }
            }
        }
        helper.runAfterDelay(1, Prepare())
    }
}
