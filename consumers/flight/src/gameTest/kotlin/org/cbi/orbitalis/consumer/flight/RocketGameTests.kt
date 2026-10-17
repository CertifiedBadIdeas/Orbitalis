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
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object RocketGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 40000, batch = "rocket_mission")
    fun nativeRocketRoundTrip(helper: GameTestHelper) = roundTrip(helper, BlockPos(0, 21, 0), false)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 40000, batch = "rocket_offaxis")
    fun nativeRocketOffAxisRoundTrip(helper: GameTestHelper) = roundTrip(helper, BlockPos(800, 81, 500), true)

    private fun roundTrip(
        helper: GameTestHelper,
        origin: BlockPos,
        reload: Boolean,
    ) {
        val server = helper.level.server
        val level = server.overworld()
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val previous = container.allSubLevels.map { it.uniqueId }.toSet()
        val worlds = listOf(level, checkNotNull(server.getLevel(org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE)))
        val pilot = InertPilot(helper)
        val transfer =
            org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer(
                server,
                java.nio.file.Files
                    .createTempDirectory("orbitalis-rocket-mission-"),
                object : org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync {
                    override fun hasShip(
                        player: net.minecraft.server.level.ServerPlayer,
                        body: ServerSubLevel,
                    ) = true

                    override fun isReady(
                        player: net.minecraft.server.level.ServerPlayer,
                        body: ServerSubLevel,
                    ): Boolean {
                        pilot.acknowledge(body)
                        return org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync.NATIVE
                            .isReady(player, body)
                    }
                },
            )
        var body: ServerSubLevel? = null
        var mission: RocketMission? = null
        var site: LaunchSites.Site? = null
        var tick = 0
        var assembledAt = 0
        var handoffs = 0
        var requested = false
        var previousState: org.cbi.orbitalis.core.planet.CartesianState? = null
        var previousWorld: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>? = null
        var minimumClearance = Double.POSITIVE_INFINITY
        var maximumHeight = -Double.MAX_VALUE
        var maximumStepAcceleration = 0.0
        var earlyHorizontal = 0.0
        var freeTicks = 0
        var initialEnergy: Double? = null
        var maximumEnergyDrift = 0.0
        val trace = StringBuilder("tick,phase,x,y,z,vx,vy,vz\n")
        val forced = mutableListOf<Long>()
        for (x in ((origin.x - 7) shr 4)..((origin.x + 7) shr 4)) {
            for (z in ((origin.z - 7) shr 4)..((origin.z + 7) shr 4)) {
                val key = ChunkPos.asLong(x, z)
                if (!level.forcedChunks.contains(key)) {
                    level.setChunkForced(x, z, true)
                    forced.add(key)
                }
                level.getChunk(x, z)
            }
        }
        onFinished(helper) {
            mission?.close()
            transfer.close()
            pilot.close()
            for (world in worlds) {
                val c = checkNotNull(SubLevelContainer.getContainer(world))
                body?.uniqueId?.let { id ->
                    (c.getSubLevel(id) as? ServerSubLevel)?.let {
                        c.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        c.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
                    }
                }
                SubLevelPhysicsSystem.require(world).paused = false
            }
            forced.forEach { level.setChunkForced(ChunkPos.getX(it), ChunkPos.getZ(it), false) }
        }
        val source =
            server
                .createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atLowerCornerOf(origin))
                .withSuppressedOutput()

        fun run() {
            tick++
            worlds.forEach { it.resetEmptyTime() }
            transfer.tick()
            helper.assertTrue(transfer.error == null, "Rocket transfer failed: ${transfer.error}")
            if (body == null) {
                if (!level.isPositionEntityTicking(origin) || forced.any { !level.areEntitiesLoaded(it) }) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(5_000_000L)
                    helper.runAfterDelay(1) { run() }
                    return
                }
                // The stand reuses its world; remove debris in this owned fixture area before building.
                val fixtureArea = AABB(Vec3.atLowerCornerOf(origin.offset(-7, -1, -7)), Vec3.atLowerCornerOf(origin.offset(8, 18, 8)))
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java, fixtureArea).forEach { it.discard() }
                server.commands.performPrefixedCommand(source, "fill ~-7 ~-1 ~-7 ~7 ~-1 ~7 minecraft:stone")
                server.commands.performPrefixedCommand(source, "fill ~-6 ~ ~-6 ~6 ~16 ~6 minecraft:air")
                server.commands.performPrefixedCommand(source, "function orbitalis_flight_test:rocket")
                (level.getBlockEntity(origin.offset(0, 10, 0)) as PhysicsAssemblerBlockEntity).assembleOrDisassemble()
                body = container.allSubLevels.filter { it.uniqueId !in previous }.single() as ServerSubLevel
                assembledAt = tick
            }
            if (!transfer.busy) {
                for (world in worlds) {
                    val c = checkNotNull(SubLevelContainer.getContainer(world))
                    val b = c.getSubLevel(body!!.uniqueId) as? ServerSubLevel ?: continue
                    c.addForceLoadTicket(b, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    if (pilot.player.level() === world) pilot.attach(b)
                }
            }
            if (mission == null && tick >= assembledAt + 120) {
                val b = body!!
                val p = AutopilotSession.position(b)
                pilot.player.teleportTo(level, p.x, p.y + 3, p.z, emptySet(), 0f, 0f)
                pilot.acknowledge(b)
                val loose =
                    level.getAllEntities().filterIsInstance<net.minecraft.world.entity.item.ItemEntity>().filter {
                        b.plot.contains(it.position()) ||
                            b.boundingBox().let { box ->
                                net.minecraft.world.phys
                                    .AABB(
                                        box.minX(),
                                        box.minY(),
                                        box.minZ(),
                                        box.maxX(),
                                        box.maxY(),
                                        box.maxZ(),
                                    ).inflate(3.0)
                                    .contains(it.position())
                            }
                    }
                helper.assertTrue(loose.isEmpty(), "Rocket fixture shed items: ${loose.map { it.item }}")
                site =
                    LaunchSites.Site(
                        b.uniqueId,
                        p,
                        org.joml.Quaterniond(b.logicalPose().orientation()),
                        net.minecraft.world.level.Level.OVERWORLD,
                    )
                mission = RocketMission(pilot.player, b, site!!, transfer, false)
            }
            mission?.let { active ->
                val before = active.phase
                val landed = active.tick(pilot.player)
                if (!active.body.isRemoved && active.phase != RocketMission.Phase.TRANSFER) {
                    val b = active.body
                    val p =
                        org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
                            .globalPosition(b.level, AutopilotSession.position(b))
                    val v =
                        AutopilotSession.vector(
                            SubLevelPhysicsSystem.require(b.level).getPhysicsHandle(b).getLinearVelocity(Vector3d()),
                        )
                    val current =
                        org.cbi.orbitalis.core.planet
                            .CartesianState(p, v)
                    trace.append("$tick,${active.phase},${p.x},${p.y},${p.z},${v.x},${v.y},${v.z}\n")
                    maximumHeight = maxOf(maximumHeight, p.y - 64.0)
                    if (p.y < FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT &&
                        active.phase == RocketMission.Phase.ASCENT
                    ) {
                        earlyHorizontal = maxOf(earlyHorizontal, kotlin.math.hypot(v.x, v.z))
                    }
                    val old = previousState
                    if (old != null && previousWorld == b.level.dimension() &&
                        before !in listOf(RocketMission.Phase.TRANSFER, RocketMission.Phase.TOUCHDOWN)
                    ) {
                        maximumStepAcceleration = maxOf(maximumStepAcceleration, (v - old.velocity).length() / 0.05)
                        helper.assertTrue(
                            ((p - old.position) - (v + old.velocity) * 0.025).length() < 0.3,
                            "Native trajectory has a position jump",
                        )
                    }
                    previousState = current
                    previousWorld = b.level.dimension()
                    if (active.phase in listOf(RocketMission.Phase.COAST, RocketMission.Phase.RETURN, RocketMission.Phase.FLIP)) {
                        val clearance =
                            Vector3(
                                maxOf(0.0, abs(p.x) - FlatPlanetSpace.HALF_SIZE),
                                maxOf(0.0, abs(p.y) - 64.0),
                                maxOf(0.0, abs(p.z) - FlatPlanetSpace.HALF_SIZE),
                            ).length()
                        minimumClearance = minOf(minimumClearance, clearance)
                        helper.assertTrue(clearance > 32.0, "Rocket's free coast passes too close to the planet: $clearance")
                        if (active.phase != RocketMission.Phase.FLIP) {
                            helper.assertTrue(AutopilotSession.enginesFaded(b), "An engine fired during the unpowered revolution")
                            val energy =
                                org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
                                    .flatField(
                                        b.level,
                                    ).specificEnergy(current)
                            if (initialEnergy == null) initialEnergy = energy
                            maximumEnergyDrift = maxOf(maximumEnergyDrift, abs((energy - initialEnergy!!) / initialEnergy!!))
                            helper.assertTrue(maximumEnergyDrift < 0.01, "Free-orbit energy drifted: $maximumEnergyDrift")
                            freeTicks++
                        }
                        val main =
                            b.plot.loadedChunks
                                .flatMap { it.chunk.blockEntities.values }
                                .filterIsInstance<CreativeVectorThrusterBlockEntity>()
                        helper.assertTrue(
                            main.all { it.power == 0.0f && it.currentThrust == 0.0f },
                            "Main engines fired during free coast/flip",
                        )
                    }
                }
                if (before != active.phase || tick % 200 == 0) println("ORBITALIS_ROCKET_MISSION tick=$tick ${active.status()}")
                if (before == RocketMission.Phase.TRANSFER && active.phase != before) handoffs++
                if (active.phase == RocketMission.Phase.COAST && !requested) {
                    if (reload) {
                        val bookmarks = LaunchSites()
                        bookmarks.put(pilot.player.uuid, site!!)
                        val saved =
                            LaunchSites
                                .load(
                                    bookmarks.save(net.minecraft.nbt.CompoundTag(), level.registryAccess()),
                                    level.registryAccess(),
                                ).require(pilot.player.uuid, active.body.uniqueId)
                        active.close()
                        mission = RocketMission(pilot.player, active.body, saved, transfer, true)
                    }
                    requested = true
                }
                if (tick % 200 == 0 && !active.body.isRemoved) {
                    println(
                        "ORBITALIS_ROCKET_POSE p=${AutopilotSession.position(active.body)} q=${active.body.logicalPose().orientation()}",
                    )
                }
                if (landed) {
                    helper.assertTrue(
                        handoffs == 2 && requested && active.coastAngle >= 2 * Math.PI,
                        "Rocket did not complete full round trip",
                    )
                    helper.assertTrue((AutopilotSession.position(active.body) - site!!.position).length() < 0.5, "Rocket missed its pad")
                    val cargo =
                        active.body.plot.loadedChunks
                            .flatMap { it.chunk.blockEntities.values }
                            .filterIsInstance<ChestBlockEntity>()
                    helper.assertTrue(cargo.size == 4 && cargo.all { it.getItem(0).count == 16 }, "Rocket cargo lost")
                    helper.assertTrue(AutopilotSession.enginesFaded(active.body), "Rocket landed with thrust")
                    helper.assertTrue(freeTicks > 1000, "No complete native engine-off revolution")
                    helper.assertTrue(earlyHorizontal > 2.0, "No horizontal acceleration before ascent handoff")
                    helper.assertTrue(
                        maximumHeight < FlatPlanetSpace.ORBIT_HEIGHT * 1.2,
                        "Rocket repeated the high-climb route: $maximumHeight",
                    )
                    helper.assertTrue(maximumStepAcceleration < 180.0, "Rocket motion has an acceleration spike: $maximumStepAcceleration")
                    val tracePath =
                        java.nio.file.Path
                            .of("build/agent-logs/rocket-trace-${origin.x}-${origin.z}.csv")
                    java.nio.file.Files
                        .createDirectories(tracePath.parent)
                    java.nio.file.Files
                        .writeString(tracePath, trace)
                    println(
                        "ORBITALIS_ROCKET_RESULT pad=$origin clearance=$minimumClearance maxHeight=$maximumHeight " +
                            "maxAcceleration=$maximumStepAcceleration earlyHorizontal=$earlyHorizontal",
                    )
                    helper.succeed()
                    return
                }
                // Accelerated GameTest ticks must give the real background planners CPU time.
                if (active.phase in listOf(RocketMission.Phase.PLANNING, RocketMission.Phase.LIFTOFF, RocketMission.Phase.BRAKE)) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(1_000_000L)
                }
            }
            helper.runAfterDelay(1) { run() }
        }
        run()
    }

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

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 5000, batch = "rocket_rcs")
    fun nativeRocketRcsFlip(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val origin = BlockPos(200, 160, 200)
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val previous = container.allSubLevels.map { it.uniqueId }.toSet()
        val forced = mutableListOf<Long>()
        var body: ServerSubLevel? = null
        var drive: RocketDrive? = null
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
            body?.let {
                container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                if (!it.isRemoved) container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
            }
            for (key in forced) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false)
        }

        class Sequence {
            var attempts = 0
            var tick = 0
            var expectedVelocity = Vector3.ZERO

            fun prepare() {
                level.resetEmptyTime()
                helper.assertTrue(++attempts < 4000, "RCS test entity chunks did not load")
                if (!level.isPositionEntityTicking(origin) || forced.any { !level.areEntitiesLoaded(it) }) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(5_000_000L)
                    helper.runAfterDelay(1) { prepare() }
                    return
                }
                val source =
                    level.server
                        .createCommandSourceStack()
                        .withLevel(
                            level,
                        ).withPosition(Vec3.atLowerCornerOf(origin))
                        .withSuppressedOutput()
                val bounds = AABB(Vec3.atLowerCornerOf(origin.offset(-6, 0, -6)), Vec3.atLowerCornerOf(origin.offset(7, 17, 7)))
                level.getEntitiesOfClass(SuperGlueEntity::class.java, bounds).forEach { it.discard() }
                level.server.commands.performPrefixedCommand(source, "fill ~-6 ~ ~-6 ~6 ~16 ~6 minecraft:air")
                level.server.commands.performPrefixedCommand(source, "function orbitalis_flight_test:rocket")
                (level.getBlockEntity(origin.offset(0, 10, 0)) as PhysicsAssemblerBlockEntity).assembleOrDisassemble()
                val created = container.allSubLevels.filter { it.uniqueId !in previous }
                helper.assertTrue(created.size == 1, "RCS rocket split during assembly")
                body = created.single() as ServerSubLevel
                container.addForceLoadTicket(body!!, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                helper.runAfterDelay(10) {
                    val main =
                        body!!
                            .plot.loadedChunks
                            .flatMap { it.chunk.blockEntities.values }
                            .filterIsInstance<CreativeVectorThrusterBlockEntity>()
                    for (block in main) {
                        // Native deadband can hide a residual digital command while in NORMAL mode.
                        block.setControlMode(
                            dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode.PERIPHERAL,
                        )
                        block.setDigitalInput(0.001f)
                        block.setDigitalInput(0.00005f)
                        block.setDigitalInput(0.0f)
                        helper.assertTrue(block.power > 0.0f, "Native input deadband fixture did not reproduce")
                        block.setControlMode(
                            dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity.ControlMode.NORMAL,
                        )
                        block.setRedstoneInput(0)
                    }
                    drive = RocketDrive(body!!, true)
                    helper.assertTrue(main.all { it.power == 0.0f }, "Reacquiring control restored a residual main-engine command")
                    expectedVelocity =
                        AutopilotSession.vector(SubLevelPhysicsSystem.require(level).getPhysicsHandle(body!!).getLinearVelocity(Vector3d()))
                    run()
                }
            }

            fun run() {
                level.resetEmptyTime()
                val rocket = body!!
                val handle = SubLevelPhysicsSystem.require(level).getPhysicsHandle(rocket)
                drive!!.turn(Vector3(0.0, -1.0, 0.0))
                val main =
                    rocket.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<CreativeVectorThrusterBlockEntity>()
                helper.assertTrue(
                    main.size == 4 && main.all { it.power == 0.0f && it.currentThrust == 0.0f },
                    "Main engines fired during RCS flip",
                )
                expectedVelocity += SableFlightGravity.acceleration(level, AutopilotSession.position(rocket)) * 0.05
                if (++tick % 100 ==
                    0
                ) {
                    println(
                        "ORBITALIS_ROCKET_FLIP tick=$tick alignment=${drive!!.alignment(
                            Vector3(0.0,-1.0,0.0),
                        )} omega=${handle.getAngularVelocity(Vector3d())}",
                    )
                }
                if (tick < 400) {
                    helper.runAfterDelay(1) { run() }
                    return
                }
                helper.assertTrue(drive!!.alignment(Vector3(0.0, -1.0, 0.0)) > 0.999, "RCS did not complete the 180-degree flip")
                helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.02, "RCS did not settle rotation")
                val velocity = AutopilotSession.vector(handle.getLinearVelocity(Vector3d()))
                helper.assertTrue(
                    (velocity - expectedVelocity).length() < 0.5,
                    "RCS introduced excessive translation: $velocity vs $expectedVelocity",
                )
                drive!!.close()
                helper.runAfterDelay(30) {
                    helper.assertTrue(AutopilotSession.enginesFaded(rocket), "RCS remained powered after stop")
                    helper.succeed()
                }
            }
        }
        helper.runAfterDelay(1) { Sequence().prepare() }
    }
}
