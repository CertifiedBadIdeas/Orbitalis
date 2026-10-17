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
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.creative_vector_thruster.CreativeVectorThrusterBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object RocketGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 5000, batch = "rocket_vector")
    fun nativeRocketAssemblySteeringAndHold(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val origin = BlockPos(-600, 180, 400)
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val previous = container.allSubLevels.map { it.uniqueId }.toSet()
        val owned = mutableSetOf<UUID>()
        val forced = mutableListOf<Long>()
        var drive: RocketDrive? = null
        var body: ServerSubLevel? = null
        for (x in (origin.x - 6 shr 4)..(origin.x + 6 shr 4)) {
            for (z in (origin.z - 6 shr 4)..(origin.z + 6 shr 4)) {
                val key = ChunkPos.asLong(x, z)
                if (!level.forcedChunks.contains(key)) {
                    level.setChunkForced(x, z, true)
                    forced.add(key)
                }
                level.getChunk(x, z)
            }
        }
        onFinished(helper) {
            drive?.close()
            container.allSubLevels.toList().filter { it.uniqueId in owned || it.splitFromSubLevel in owned }.forEach {
                container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
            }
            for (key in forced) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false)
        }
        val source =
            level.server
                .createCommandSourceStack()
                .withLevel(
                    level,
                ).withPosition(Vec3.atLowerCornerOf(origin))
                .withSuppressedOutput()

        class Sequence {
            var attempts = 0

            fun prepare() {
                level.resetEmptyTime()
                helper.assertTrue(++attempts < 4000, "Rocket entity chunks did not become ready")
                if (!level.isPositionEntityTicking(origin) || forced.any { !level.areEntitiesLoaded(it) }) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(5_000_000L)
                    helper.runAfterDelay(1) { prepare() }
                    return
                }
                val bounds = AABB(Vec3.atLowerCornerOf(origin.offset(-6, 0, -6)), Vec3.atLowerCornerOf(origin.offset(7, 17, 7)))
                level.getEntitiesOfClass(SuperGlueEntity::class.java, bounds).forEach { it.discard() }
                level.server.commands.performPrefixedCommand(source, "fill ~-6 ~ ~-6 ~6 ~16 ~6 minecraft:air")
                level.server.commands.performPrefixedCommand(source, "function orbitalis_flight_test:rocket")
                (level.getBlockEntity(origin.offset(0, 10, 0)) as PhysicsAssemblerBlockEntity).assembleOrDisassemble()
                val created = container.allSubLevels.filter { it.uniqueId !in previous }
                helper.assertTrue(created.size == 1, "Rocket split or failed native assembly")
                body = created.single() as ServerSubLevel
                owned.add(body!!.uniqueId)
                container.addForceLoadTicket(body!!, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                helper.runAfterDelay(10) { steeringTrial() }
            }

            lateinit var engines: List<CreativeVectorThrusterBlockEntity>
            var target = Vector3.ZERO
            var flightTick = 0
            var tilted = false

            fun holdTick() {
                level.resetEmptyTime()
                val rocket = checkNotNull(body)
                val handle = SubLevelPhysicsSystem.require(level).getPhysicsHandle(rocket)
                val position = AutopilotSession.position(rocket)
                drive!!.update(
                    OrbitalGuidance.point(
                        CartesianState(position, AutopilotSession.vector(handle.getLinearVelocity(Vector3d()))),
                        target,
                        12.0,
                    ),
                )
                tilted = tilted || rocket
                    .logicalPose()
                    .orientation()
                    .transform(Vector3d(0.0, 1.0, 0.0))
                    .y < 0.9995
                flightTick++
                if (flightTick % 100 ==
                    0
                ) {
                    println("ORBITALIS_ROCKET_HOLD tick=$flightTick error=${(position - target).length()} ${drive!!.status()}")
                }
                if (flightTick < 800) {
                    helper.runAfterDelay(1) { holdTick() }
                    return
                }
                helper.assertTrue((position - target).length() < 1.0, "Rocket did not reach/hold target: $position, $target")
                helper.assertTrue(handle.getLinearVelocity(Vector3d()).length() < 0.4, "Rocket failed to settle translation")
                helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.04, "Rocket failed to settle rotation")
                helper.assertTrue(tilted, "Rocket did not tilt to correct lateral disturbance")
                val cargo =
                    rocket.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<ChestBlockEntity>()
                helper.assertTrue(cargo.size == 4 && cargo.all { it.getItem(0).count == 16 }, "Rocket lost cargo")
                drive!!.close()
                helper.runAfterDelay(30) {
                    helper.assertTrue(AutopilotSession.enginesFaded(rocket), "Rocket engines remained powered after stop")
                    helper.assertTrue(
                        engines.all { it.targetVectorX == 0.0f && it.targetVectorY == 0.0f },
                        "Steering owner remained latched",
                    )
                    helper.succeed()
                }
            }

            fun beginHold() {
                val rocket = checkNotNull(body)
                engines.forEach { (it as RocketSteering).clearRocketVector() }
                helper.runAfterDelay(30) {
                    drive = RocketDrive(rocket, true)
                    target = AutopilotSession.position(rocket) + Vector3(0.0, 20.0, 0.0)
                    val handle = SubLevelPhysicsSystem.require(level).getPhysicsHandle(rocket)
                    // Arrange the initial disturbance once; recovery thereafter uses native engines.
                    handle.addLinearAndAngularVelocity(
                        handle.getLinearVelocity(Vector3d()).negate(),
                        handle.getAngularVelocity(Vector3d()).negate(),
                    )
                    handle.applyLinearImpulse(Vector3d(rocket.massTracker.mass * 2.0, 0.0, 0.0))
                    helper.runAfterDelay(1) { holdTick() }
                }
            }

            fun steeringTrial() {
                val rocket = checkNotNull(body)
                engines =
                    rocket.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<CreativeVectorThrusterBlockEntity>()
                helper.assertTrue(engines.size == 4, "Rocket does not contain four native vector engines")
                engines.forEach { (it as RocketSteering).setRocketVector(0.5f, -0.25f) }
                helper.runAfterDelay(30) {
                    for (engine in engines) {
                        val expected =
                            Vector3d(
                                engine.currentVectorX * 0.5773502691896257,
                                1.0,
                                engine.currentVectorY * 0.5773502691896257,
                            ).normalize()
                        helper.assertTrue(
                            engine.thrustDirectionLocal.distance(expected) < 1e-6,
                            "Native vector basis differs from allocator",
                        )
                        helper.assertTrue(abs(engine.currentVectorX - 0.5f) < 0.002f, "Native gimbal interpolation ignored steering")
                        helper.assertTrue(engine.currentThrust == 0.0f, "Unpowered steering created thrust")
                    }
                    beginHold()
                }
            }
        }
        helper.runAfterDelay(1) { Sequence().prepare() }
    }
}
