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
import org.cbi.orbitalis.core.flight.OrbitalGuidance
import org.cbi.orbitalis.core.flight.RocketAttitudeControl
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.control.AutopilotFlightLoading
import org.cbi.orbitalis.minecraft.flight.control.FlightBody
import org.cbi.orbitalis.minecraft.flight.control.FlightGuidanceFrames
import org.cbi.orbitalis.minecraft.flight.control.LaunchSites
import org.cbi.orbitalis.minecraft.flight.control.RocketMission
import org.cbi.orbitalis.minecraft.integration.propulsion.AutopilotSession
import org.cbi.orbitalis.minecraft.integration.propulsion.RocketDrive
import org.cbi.orbitalis.minecraft.integration.propulsion.RocketSteering
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object RocketGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 120000, batch = "rocket_mission")
    fun nativeRocketRoundTrip(helper: GameTestHelper) = roundTrip(helper, BlockPos(0, 21, 0), false, uncrewed = true)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 120000, batch = "rocket_offaxis")
    fun nativeRocketOffAxisRoundTrip(helper: GameTestHelper) = roundTrip(helper, BlockPos(800, 81, 500), true)

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 120000, batch = "rocket_seated")
    fun nativeRocketCreateSeatRoundTrip(helper: GameTestHelper) = roundTrip(helper, BlockPos(-800, 81, -500), false, true)

    private fun roundTrip(
        helper: GameTestHelper,
        origin: BlockPos,
        reload: Boolean,
        seated: Boolean = false,
        uncrewed: Boolean = false,
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
        var relocationBlocker: ServerSubLevel? = null

        fun releaseRelocationBlocker() {
            relocationBlocker?.let { blocker ->
                org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                    .release(blocker.uniqueId, helper)
                if (!blocker.isRemoved) {
                    val c = checkNotNull(SubLevelContainer.getContainer(blocker.level))
                    c.removeForceLoadTicket(blocker, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    c.removeSubLevel(blocker, SubLevelRemovalReason.REMOVED)
                    helper.assertTrue(
                        c.allSubLevels.none { it.uniqueId == blocker.uniqueId } &&
                            c.collectForceLoadedSubLevels().none { it.uniqueId == blocker.uniqueId },
                        "Plot reservation retained its native body or force-load ticket",
                    )
                }
            }
            relocationBlocker = null
        }
        var sourcePlot: net.minecraft.world.level.ChunkPos? = null
        var body: ServerSubLevel? = null
        var seatId: UUID? = null
        var mission: RocketMission? = null
        var site: LaunchSites.Site? = null
        var tick = 0
        var assembledAt = 0
        var handoffs = 0
        var requested = false
        var coastBeforeLanding = 0.0
        var previousState: org.cbi.orbitalis.core.planet.CartesianState? = null
        var previousWorld: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>? = null
        var minimumClearance = Double.POSITIVE_INFINITY
        var maximumHeight = -Double.MAX_VALUE
        var maximumStepAcceleration = 0.0
        var earlyHorizontal = 0.0
        var freeTicks = 0
        var disturbed = false
        var maximumCoastRateError = 0.0
        var initialEnergy: Double? = null
        var maximumEnergyDrift = 0.0
        var coastWork = 0.0
        var mainImpulse = 0.0
        var rcsImpulse = 0.0
        var angularTravel = 0.0
        var previousOrientation: org.joml.Quaterniond? = null
        var returnDeltaV = 0.0
        var returnDirection: Vector3? = null
        var maximumPadOvershoot = 0.0
        var flightLoading: AutopilotFlightLoading? = null
        val trace =
            StringBuilder(
                "tick,phase,x,y,z,vx,vy,vz,nx,ny,nz,mainImpulse,rcsImpulse,angularTravel,epoch,earthX,earthY,earthZ,earthVx,earthVy,earthVz,referenceX,referenceY,referenceZ,referenceVx,referenceVy,referenceVz\n",
            )
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
            val evidence =
                java.nio.file.Path
                    .of("build/agent-logs/rocket-attempt-${origin.x}-${origin.z}.csv")
            java.nio.file.Files
                .createDirectories(evidence.parent)
            java.nio.file.Files
                .writeString(evidence, trace.toString())
            mission?.close()
            flightLoading?.close()
            transfer.close()
            pilot.close()
            releaseRelocationBlocker()
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
            pilot.acknowledgeTeleport()
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
                    if (!uncrewed || mission == null) c.addForceLoadTicket(b, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    if (!uncrewed && pilot.player.level() === world) pilot.attach(b)
                }
            }
            if (mission == null && tick >= assembledAt + 120) {
                val b = body!!
                val p = FlightBody.position(b)
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
                if (seated) {
                    val assembler =
                        b.plot.loadedChunks
                            .flatMap { it.chunk.blockEntities.values }
                            .filterIsInstance<PhysicsAssemblerBlockEntity>()
                            .single()
                    // Previously excluded native block entities must survive the same real mission.
                    val dispenserPosition = assembler.blockPos.offset(1, 1, 1)
                    level.setBlock(
                        dispenserPosition,
                        net.minecraft.world.level.block.Blocks.DISPENSER
                            .defaultBlockState(),
                        3,
                    )
                    val dispenser = level.getBlockEntity(dispenserPosition) as net.minecraft.world.level.block.entity.DispenserBlockEntity
                    dispenser.setItem(
                        0,
                        net.minecraft.world.item
                            .ItemStack(net.minecraft.world.item.Items.DIAMOND, 7),
                    )
                    val seatPosition = assembler.blockPos.offset(0, 0, 1)
                    val block =
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                            net.minecraft.resources.ResourceLocation
                                .parse("create:orange_seat"),
                        )
                    level.setBlock(seatPosition, block.defaultBlockState(), 3)
                    com.simibubi.create.content.contraptions.actors.seat.SeatBlock
                        .sitDown(level, seatPosition, pilot.player)
                    seatId = checkNotNull(pilot.player.vehicle).uuid
                    helper.assertTrue(
                        org.cbi.orbitalis.minecraft.integration.sable.SableShipContact
                            .resolve(pilot.player) === b,
                        "Create seat did not resolve to its plot-owned ship",
                    )
                }
                if (reload) {
                    // Force a different destination plot, exercising embedded vector-link addresses.
                    sourcePlot = b.plot.plotPos
                    val target = checkNotNull(SubLevelContainer.getContainer(worlds[1]))
                    val plotX = b.plot.plotPos.x - container.origin.x
                    val plotZ = b.plot.plotPos.z - container.origin.y
                    if (!target.occupancy[target.getIndex(plotX, plotZ)]) {
                        val pose =
                            dev.ryanhcode.sable.companion.math
                                .Pose3d()
                                .also { it.position().set(FlatPlanetSpace.HALF_SIZE + 1000.0, 10000.0, FlatPlanetSpace.HALF_SIZE + 1000.0) }
                        val blocker = target.allocateSubLevel(UUID.randomUUID(), plotX, plotZ, pose) as ServerSubLevel
                        blocker.plot.newEmptyChunk(blocker.plot.centerChunk)
                        blocker.plot.embeddedLevelAccessor.setBlock(
                            BlockPos.ZERO,
                            net.minecraft.world.level.block.Blocks.GOLD_BLOCK
                                .defaultBlockState(),
                            3,
                        )
                        blocker.updateMergedMassData(1.0f)
                        blocker.updateBoundingBox()
                        blocker.forceUpdateGlobalBounds()
                        blocker.updateLastPose()
                        target.addForceLoadTicket(blocker, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                            .reserve(blocker.uniqueId, helper)
                        relocationBlocker = blocker
                    }
                }
                mission = RocketMission(pilot.player, b, site!!, transfer, false)
                if (uncrewed) {
                    flightLoading = AutopilotFlightLoading(checkNotNull(mission).body)
                    container.removeForceLoadTicket(b, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    pilot.player.discard()
                }
            }
            mission?.let { active ->
                val before = active.phase
                flightLoading?.follow(active.body)
                val landed = active.tick(if (uncrewed) null else pilot.player)
                flightLoading?.follow(active.body)
                if (!active.body.isRemoved && active.phase != RocketMission.Phase.TRANSFER) {
                    val b = active.body
                    val sample =
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight
                            .sample(b.level)
                    val current = FlightGuidanceFrames.rootState(b)
                    val earth = sample.nativeState(current, net.minecraft.world.level.Level.OVERWORLD)
                    val p = current.position
                    val v = current.velocity
                    val orientation = org.joml.Quaterniond(b.logicalPose().orientation())
                    val nose =
                        sample.rootDirection(
                            FlightBody.vector(orientation.transform(Vector3d(0.0, 1.0, 0.0))),
                            b.level.dimension(),
                        )
                    if (previousWorld == b.level.dimension()) {
                        previousOrientation?.let {
                            angularTravel += 2.0 * kotlin.math.acos(kotlin.math.abs(it.dot(orientation)).coerceIn(0.0, 1.0))
                        }
                    }
                    previousOrientation = orientation
                    val rcs =
                        b.plot.loadedChunks
                            .flatMap {
                                it.chunk.blockEntities.values
                            }.filterIsInstance<AbstractThrusterBlockEntity>()
                            .filter { it !is CreativeVectorThrusterBlockEntity }
                    helper.assertTrue(
                        rcs.all { it.power <= RocketAttitudeControl.RCS_THROTTLE + 0.0001 },
                        "RCS exceeded its correction thrust cap",
                    )
                    // Sum scalar native engine impulse: opposing RCS couples must not cancel.
                    for (engine in b.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<AbstractThrusterBlockEntity>()) {
                        val impulse =
                            engine.currentThrust.toDouble() /
                                dev.propulsionteam.propulsionsimulated.PropulsionConfig
                                    .getThrustUnitsPerKnOrDefault() *
                                0.05 /
                                b.massTracker.mass
                        if (engine is CreativeVectorThrusterBlockEntity) mainImpulse += impulse else rcsImpulse += impulse
                    }
                    val reference = active.returnReference?.state
                    trace.append(
                        "$tick,${active.phase},${p.x},${p.y},${p.z},${v.x},${v.y},${v.z},${nose.x},${nose.y},${nose.z},$mainImpulse,$rcsImpulse,$angularTravel,${sample.frames.tick},${earth.position.x},${earth.position.y},${earth.position.z},${earth.velocity.x},${earth.velocity.y},${earth.velocity.z},${reference?.position?.x ?: ""},${reference?.position?.y ?: ""},${reference?.position?.z ?: ""},${reference?.velocity?.x ?: ""},${reference?.velocity?.y ?: ""},${reference?.velocity?.z ?: ""}\n",
                    )
                    if (active.phase == RocketMission.Phase.ASCENT) maximumHeight = maxOf(maximumHeight, earth.position.y - 64.0)
                    if (earth.position.y < FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.DESCENT_HEIGHT &&
                        active.phase == RocketMission.Phase.ASCENT
                    ) {
                        earlyHorizontal = maxOf(earlyHorizontal, kotlin.math.hypot(v.x, v.z))
                    }
                    val old = previousState
                    if (old != null && previousWorld == b.level.dimension() &&
                        before !in listOf(RocketMission.Phase.TRANSFER, RocketMission.Phase.TOUCHDOWN)
                    ) {
                        if (active.phase in listOf(RocketMission.Phase.BRAKE, RocketMission.Phase.APPROACH) ||
                            (active.phase == RocketMission.Phase.FADE && returnDirection != null)
                        ) {
                            val field = SableFlightGravity.flatField(b.level)
                            val acceleration = (v - old.velocity) * 20.0
                            returnDeltaV += (acceleration - sample.dynamics.acceleration((p + old.position) * 0.5, -0.025)).length() * 0.05
                        }
                        maximumStepAcceleration = maxOf(maximumStepAcceleration, (v - old.velocity).length() / 0.05)
                        helper.assertTrue(
                            ((p - old.position) - (v + old.velocity) * 0.025).length() < 0.3,
                            "Native trajectory has a position jump: phase=${active.phase} before=$before tick=$tick old=$old current=$current",
                        )
                    }
                    if (active.phase == RocketMission.Phase.BRAKE && returnDirection == null) {
                        returnDirection = Vector3(earth.velocity.x, 0.0, earth.velocity.z).normalized()
                        helper.assertTrue(
                            (site!!.position - earth.position).dot(returnDirection!!) > 20.0,
                            "Braking started after flying past the pad",
                        )
                    }
                    returnDirection?.let { direction ->
                        maximumPadOvershoot = maxOf(maximumPadOvershoot, (earth.position - site!!.position).dot(direction))
                    }
                    previousState = current
                    previousWorld = b.level.dimension()
                    val replanningReturn = reload && requested && active.phase == RocketMission.Phase.PLANNING
                    if (replanningReturn ||
                        active.phase in listOf(RocketMission.Phase.COAST, RocketMission.Phase.RETURN, RocketMission.Phase.FLIP)
                    ) {
                        val clearance =
                            Vector3(
                                maxOf(0.0, abs(earth.position.x) - FlatPlanetSpace.HALF_SIZE),
                                maxOf(0.0, abs(earth.position.y) - 64.0),
                                maxOf(0.0, abs(earth.position.z) - FlatPlanetSpace.HALF_SIZE),
                            ).length()
                        minimumClearance = minOf(minimumClearance, clearance)
                        helper.assertTrue(clearance > 32.0, "Rocket's free coast passes too close to the planet: $clearance")
                        if (active.phase != RocketMission.Phase.FLIP) {
                            val mains =
                                b.plot.loadedChunks
                                    .flatMap { it.chunk.blockEntities.values }
                                    .filterIsInstance<CreativeVectorThrusterBlockEntity>()
                            helper.assertTrue(
                                mains.all { it.power == 0.0f && it.currentThrust == 0.0f },
                                "A main engine fired during the ballistic revolution",
                            )
                            val handle = SubLevelPhysicsSystem.require(b.level).getPhysicsHandle(b)
                            if (!disturbed) {
                                // A real native angular impulse recreates residual roll after insertion.
                                handle.applyAngularImpulse(b.massTracker.inertiaTensor.transform(Vector3d(0.0, 0.2, 0.0), Vector3d()))
                                disturbed = true
                            }
                            if (freeTicks > 100) {
                                val gravity = sample.dynamics.acceleration(p)
                                val expectedRate = v.cross(gravity) * (1.0 / v.dot(v))
                                val rate =
                                    sample.rootAngular(
                                        FlightBody.vector(handle.getAngularVelocity(Vector3d())),
                                        b.level.dimension(),
                                    )
                                if (active.phase == RocketMission.Phase.COAST) {
                                    maximumCoastRateError = maxOf(maximumCoastRateError, (rate - expectedRate).length())
                                    helper.assertTrue(
                                        maximumCoastRateError < 0.35,
                                        "RCS failed to track changing orbit curvature: $maximumCoastRateError",
                                    )
                                } else {
                                    // Return deliberately turns retrograde; compare its bounded spin,
                                    // not its rate against prograde tracking while executing that turn.
                                    helper.assertTrue(rate.length() < 0.35, "Return RCS exceeded controlled turn rate: $rate")
                                }
                                val nose =
                                    sample.rootDirection(
                                        FlightBody.vector(b.logicalPose().orientation().transform(Vector3d(0.0, 1.0, 0.0))),
                                        b.level.dimension(),
                                    )
                                helper.assertTrue(
                                    active.phase != RocketMission.Phase.COAST || nose.dot(v.normalized()) > 0.97,
                                    "Rocket failed to hold prograde on the ballistic orbit",
                                )
                                helper.assertTrue(abs(rate.dot(nose)) < 0.03, "RCS failed to damp longitudinal roll: ${rate.dot(nose)}")
                            }
                            val energy = v.dot(v) * 0.5
                            if (initialEnergy == null) {
                                initialEnergy = energy
                            } else if (old !=
                                null
                            ) {
                                coastWork +=
                                    sample.dynamics.acceleration((p + old.position) * 0.5, -0.025).dot((v + old.velocity) * 0.5) * 0.05
                            }
                            maximumEnergyDrift = maxOf(maximumEnergyDrift, abs((energy - initialEnergy!! - coastWork) / initialEnergy!!))
                            helper.assertTrue(
                                maximumEnergyDrift < 0.01,
                                "Free-orbit gravity work disagrees with native energy: $maximumEnergyDrift",
                            )
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
                if (before == RocketMission.Phase.TRANSFER && active.phase != before) {
                    handoffs++
                    if (reload && handoffs == 1) {
                        helper.assertTrue(active.body.plot.plotPos != sourcePlot, "Outbound rocket did not relocate its occupied plot")
                        // Its only purpose was to reserve the first target plot. A retained falling
                        // body generates an unrelated chunk trail and can trigger automatic travel.
                        releaseRelocationBlocker()
                    }
                    if (seated) helper.assertTrue(pilot.player.vehicle?.uuid == seatId, "Create seat/rider link lost at handoff")
                }
                if (!requested) {
                    helper.assertTrue(
                        active.phase !in listOf(RocketMission.Phase.RETURN, RocketMission.Phase.FLIP, RocketMission.Phase.BRAKE),
                        "Orbit started returning without an explicit landing request",
                    )
                }
                // The centred flight must stay in orbit beyond a complete revolution. The other
                // flights request landing early, including reconnecting through a saved bookmark.
                val landingAngle = if (reload || seated) 0.15 else 2 * Math.PI + 0.1
                if (active.phase == RocketMission.Phase.COAST && !requested && active.coastAngle >= landingAngle) {
                    coastBeforeLanding = active.coastAngle
                    println("ORBITALIS_EXPLICIT_LAND coastTurns=${coastBeforeLanding / (2 * Math.PI)} reconnect=$reload")
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
                    } else {
                        active.requestLanding()
                    }
                    requested = true
                }
                if (tick % 200 == 0 && !active.body.isRemoved) {
                    println(
                        "ORBITALIS_ROCKET_POSE p=${FlightBody.position(active.body)} q=${active.body.logicalPose().orientation()}",
                    )
                }
                if (landed) {
                    helper.assertTrue(
                        handoffs == 2 && requested && (reload || seated || coastBeforeLanding > 2 * Math.PI),
                        "Rocket did not complete the separately requested orbit and landing",
                    )
                    helper.assertTrue((FlightBody.position(active.body) - site!!.position).length() < 0.5, "Rocket missed its pad")
                    val cargo =
                        active.body.plot.loadedChunks
                            .flatMap { it.chunk.blockEntities.values }
                            .filterIsInstance<ChestBlockEntity>()
                    helper.assertTrue(cargo.size == 4 && cargo.all { it.getItem(0).count == 16 }, "Rocket cargo lost")
                    helper.assertTrue(AutopilotSession.enginesFaded(active.body), "Rocket landed with thrust")
                    if (seated) {
                        val dispensers =
                            active.body.plot.loadedChunks
                                .flatMap { it.chunk.blockEntities.values }
                                .filterIsInstance<net.minecraft.world.level.block.entity.DispenserBlockEntity>()
                        helper.assertTrue(
                            dispensers.size == 1 && dispensers.single().getItem(0).`is`(net.minecraft.world.item.Items.DIAMOND) &&
                                dispensers.single().getItem(0).count == 7,
                            "Previously excluded dispenser data or cargo was lost",
                        )
                        helper.assertTrue(
                            pilot.player.vehicle?.uuid == seatId && pilot.player.vehicle?.type?.let {
                                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                    .getKey(it)
                                    .toString() == "create:seat"
                            } == true,
                            "Create seat or pilot mount was lost",
                        )
                    }
                    helper.assertTrue(freeTicks > (if (reload || seated) 100 else 1000), "No native engine-off coast before landing")
                    helper.assertTrue(earlyHorizontal > 2.0, "No horizontal acceleration before ascent handoff")
                    helper.assertTrue(
                        maximumHeight < FlatPlanetSpace.ORBIT_HEIGHT * 1.2,
                        "Rocket repeated the high-climb route: $maximumHeight",
                    )
                    helper.assertTrue(maximumStepAcceleration < 180.0, "Rocket motion has an acceleration spike: $maximumStepAcceleration")
                    helper.assertTrue(maximumPadOvershoot < 10.0, "Return flew past the pad by $maximumPadOvershoot metres")
                    helper.assertTrue(
                        returnDeltaV < 600.0 * org.cbi.orbitalis.core.planet.FlatPlanetDefinition.SCALE,
                        "Return wasted excessive native delta-v: $returnDeltaV",
                    )
                    val tracePath =
                        java.nio.file.Path
                            .of("build/agent-logs/rocket-trace-${origin.x}-${origin.z}.csv")
                    java.nio.file.Files
                        .createDirectories(tracePath.parent)
                    java.nio.file.Files
                        .writeString(tracePath, trace)
                    println(
                        "ORBITALIS_ROCKET_RESULT pad=$origin clearance=$minimumClearance maxHeight=$maximumHeight " +
                            "maxAcceleration=$maximumStepAcceleration earlyHorizontal=$earlyHorizontal " +
                            "returnDeltaV=$returnDeltaV padOvershoot=$maximumPadOvershoot " +
                            "mainImpulse=$mainImpulse rcsImpulse=$rcsImpulse angularTravel=$angularTravel",
                    )
                    helper.succeed()
                    return
                }
                // Accelerated GameTest ticks must give the real background planners CPU time.
                if (active.phase in
                    listOf(
                        RocketMission.Phase.PLANNING,
                        RocketMission.Phase.LIFTOFF,
                        RocketMission.Phase.BRAKE,
                        RocketMission.Phase.RETURN,
                        RocketMission.Phase.FLIP,
                        RocketMission.Phase.TRANSFER,
                    )
                ) {
                    val waiting = active.waitingForAscentPlan || active.waitingForReturnPlan || transfer.busy
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(if (waiting) 50_000_000L else 1_000_000L)
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
                org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                    .release(it.uniqueId, helper)
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
                org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                    .reserve(body!!.uniqueId, helper)
                container.addForceLoadTicket(body!!, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                helper.runAfterDelay(10) { steeringTrial() }
            }

            lateinit var engines: List<CreativeVectorThrusterBlockEntity>
            var target = Vector3.ZERO
            var flightTick = 0
            var steeringUsed = false

            fun holdTick() {
                level.resetEmptyTime()
                val rocket = checkNotNull(body)
                val handle = SubLevelPhysicsSystem.require(level).getPhysicsHandle(rocket)
                val position = FlightBody.position(rocket)
                drive!!.update(
                    FlightGuidanceFrames.rootAcceleration(
                        rocket,
                        OrbitalGuidance.point(
                            CartesianState(position, FlightBody.vector(handle.getLinearVelocity(Vector3d()))),
                            target,
                            12.0,
                        ),
                    ),
                )
                // Vectoring can correct a disturbance with very little hull tilt. Check actual
                // commanded gimbals, while the following motion checks prove the correction.
                if (flightTick > 20) {
                    steeringUsed = steeringUsed ||
                        engines.any {
                            abs(it.targetVectorX) > 0.01f || abs(it.targetVectorY) > 0.01f
                        }
                }
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
                helper.assertTrue(steeringUsed, "Rocket did not vector native thrust to correct lateral disturbance")
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
                    target = FlightBody.position(rocket) + Vector3(0.0, 20.0, 0.0)
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
        val frame =
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .sample(level)
        val rootOrigin = Vector3(200.0, frame.dynamics.profile.geometry.earthHalfSize * 3.0 + 2000.0, 200.0)
        val nativeOrigin = frame.nativePoint(rootOrigin, level.dimension())
        val origin = BlockPos.containing(nativeOrigin.x, nativeOrigin.y, nativeOrigin.z)
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
                VacuumTestBlueprint.discardGlue(it)
                container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                    .release(it.uniqueId, helper)
                if (!it.isRemoved) container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
            }
            for (key in forced) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false)
        }

        class Sequence {
            var attempts = 0
            var tick = 0
            lateinit var initial: CartesianState
            lateinit var initialModel: org.cbi.orbitalis.core.orbit.CelestialDynamics
            var nativeRcsImpulseBound = 0.0

            fun prepare() {
                level.resetEmptyTime()
                helper.assertTrue(++attempts < 4000, "RCS test entity chunks did not load")
                if (!level.isPositionEntityTicking(origin) || forced.any { !level.areEntitiesLoaded(it) }) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(5_000_000L)
                    helper.runAfterDelay(1) { prepare() }
                    return
                }
                if (org.cbi.orbitalis.minecraft.space.SpaceWorlds
                        .objects(level)
                ) {
                    body = VacuumTestBlueprint.create(level, origin, "rocket")
                } else {
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
                }
                org.cbi.orbitalis.minecraft.flight.SpaceTravelReservations
                    .reserve(body!!.uniqueId, helper)
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
                    // The counterfactual owns this observed epoch and full root state, including
                    // the finite field's time dependence throughout the native flip.
                    initial = FlightGuidanceFrames.rootState(body!!)
                    initialModel =
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight
                            .sample(level)
                            .dynamics
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
                if (++tick % 100 ==
                    0
                ) {
                    println(
                        "ORBITALIS_ROCKET_FLIP tick=$tick alignment=${drive!!.alignment(
                            Vector3(0.0,-1.0,0.0),
                        )} omega=${handle.getAngularVelocity(Vector3d())}",
                    )
                }
                val pods =
                    rocket.plot.loadedChunks
                        .flatMap {
                            it.chunk.blockEntities.values
                        }.filterIsInstance<AbstractThrusterBlockEntity>()
                        .filter { it !is CreativeVectorThrusterBlockEntity }
                // Sum actual native output vectors before taking the norm: balanced couples
                // rotate the hull while their net translational impulse remains zero.
                val nativeForce = Vector3d()
                for (pod in pods) {
                    nativeForce.add(
                        Vector3d(pod.thrustDirectionLocal).mul(
                            pod.currentThrust.toDouble() /
                                dev.propulsionteam.propulsionsimulated.PropulsionConfig
                                    .getThrustUnitsPerKnOrDefault(),
                        ),
                    )
                }
                nativeRcsImpulseBound += nativeForce.length() / rocket.massTracker.mass * 0.05
                helper.assertTrue(
                    pods.size == 12 &&
                        pods.all {
                            it.power <= RocketAttitudeControl.RCS_THROTTLE + 0.0001
                        },
                    "RCS flip exceeded its throttle cap",
                )
                helper.assertTrue(
                    handle.getAngularVelocity(Vector3d()).length() <= RocketAttitudeControl.RCS_RATE + 0.03,
                    "RCS caused excessive spin",
                )
                if (tick < 600) {
                    helper.runAfterDelay(1) { run() }
                    return
                }
                helper.assertTrue(drive!!.alignment(Vector3(0.0, -1.0, 0.0)) > 0.999, "RCS did not complete the 180-degree flip")
                helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.02, "RCS did not settle rotation")
                val elapsed =
                    (
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight
                            .sample(level)
                            .dynamics.epochTick - initialModel.epochTick
                    ) / 20.0
                val coast =
                    org.cbi.orbitalis.core.planet.CoastPrediction.predict(
                        initialModel.earth,
                        initial,
                        org.cbi.orbitalis.core.planet.CoastPrediction
                            .Limits(horizon = elapsed, range = initialModel.profile.flightRange),
                        initialModel,
                    )
                helper.assertTrue(
                    coast.end == org.cbi.orbitalis.core.planet.CoastPrediction.End.HORIZON,
                    "RCS counterfactual left its prediction envelope: ${coast.end}",
                )
                val expectedVelocity = coast.terminal.state.velocity
                val velocity = FlightGuidanceFrames.rootState(rocket).velocity
                println(
                    "ORBITALIS_ROCKET_RCS_COAST elapsed=$elapsed error=${(velocity - expectedVelocity).length()} " +
                        "nativeImpulseBound=$nativeRcsImpulseBound actual=$velocity expected=$expectedVelocity",
                )
                helper.assertTrue(
                    (velocity - expectedVelocity).length() < 0.5,
                    "RCS introduced excessive translation: $velocity vs $expectedVelocity; nativeImpulseBound=$nativeRcsImpulseBound",
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
