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

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Unit
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.flight.OrbitalGuidance
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.control.FlatOrbitalMission
import org.cbi.orbitalis.minecraft.flight.control.FlightBody
import org.cbi.orbitalis.minecraft.flight.control.FlightGuidanceFrames
import org.cbi.orbitalis.minecraft.flight.control.LaunchSites
import org.cbi.orbitalis.minecraft.integration.propulsion.AutopilotSession
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync
import org.joml.Quaterniond
import org.joml.Vector3d
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

@PrefixGameTestTemplate(false)
object ExpeditionMissionGameTests {
    private fun liquidEngines(body: ServerSubLevel) =
        body.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }.filterIsInstance<ThrusterBlockEntity>()

    private fun powerEngines(body: ServerSubLevel) =
        body.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }.filterIsInstance<PortableEngineBlockEntity>()

    private fun remainingFuel(body: ServerSubLevel): Int {
        val tanks = body.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }.filterIsInstance<FluidTankBlockEntity>()
        return tanks.filter { it.isController }.sumOf { it.tankInventory.fluidAmount } + liquidEngines(body).sumOf { it.fuelAmountMb }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 40000, batch = "expedition_mission")
    fun nativeExpeditionOrbitAndReturn(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val body = container.allocateNewSubLevel(Pose3d().also { it.position().set(500.0, 21.0, 0.0) }) as ServerSubLevel
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        val shipId = body.uniqueId
        val address = body.plot.centerBlock.offset(0, 64, 0)
        for (x in ((address.x - 6) shr 4)..((address.x + 6) shr 4)) {
            for (z in ((address.z - 15) shr 4)..((address.z + 15) shr 4)) body.plot.newEmptyChunk(ChunkPos(x, z))
        }
        ExpeditionBlueprint.place(level, address)
        body.updateMergedMassData(1.0f)
        // Place the complete native plot at the pad before the first physics step.
        // All later motion comes from native gravity and finite liquid engines.
        body.logicalPose().rotationPoint().set(address.x.toDouble(), address.y.toDouble(), address.z.toDouble())
        body.logicalPose().position().set(500.0, 21.0, 0.0)
        body.updateBoundingBox()
        body.forceUpdateGlobalBounds()
        body.updateLastPose()
        SubLevelPhysicsSystem.require(level).getPhysicsHandle(body).teleport(
            FlightBody.position(body).let { Vector3d(it.x, it.y, it.z) },
            body.logicalPose().orientation(),
        )
        val pilot = InertPilot(helper)
        val server = helper.level.server
        val worlds =
            listOf(Level.OVERWORLD, FlatPlanetSpace.SPACE)
                .map { checkNotNull(server.getLevel(it)) }
        val terrain =
            (-8..8)
                .flatMap { dx ->
                    (-17..17).map { z -> BlockPos(500 + dx, 20, z) }
                }.associateWith { level.getBlockState(it) }
        terrain.keys.forEach { level.setBlock(it, Blocks.STONE.defaultBlockState(), 3) }
        val transfer =
            SableShipTransfer(
                server,
                Files.createTempDirectory("orbitalis-expedition-mission-"),
                object : ShipTransferPilotSync {
                    override fun hasShip(
                        player: ServerPlayer,
                        body: ServerSubLevel,
                    ) = true

                    override fun isReady(
                        player: ServerPlayer,
                        body: ServerSubLevel,
                    ): Boolean {
                        pilot.acknowledge(body)
                        return ShipTransferPilotSync.NATIVE.isReady(player, body)
                    }
                },
            )
        var mission: FlatOrbitalMission? = null
        var tick = 0
        var site: LaunchSites.Site? = null
        var handoffs = 0
        var transferVelocity: Vector3d? = null
        var coastTicks = 0
        var coastAngle = 0.0
        var previousAngle = 0.0
        var firstEnergy = 0.0
        var coastWork = 0.0
        var coastPrevious: org.cbi.orbitalis.core.planet.CartesianState? = null
        var maximumEnergyError = 0.0
        var returned = false
        var handoffFuel: Int? = null
        var handoffCoal: Int? = null
        var coastFuel: Int? = null
        val stages = mutableSetOf<FlatOrbitalMission.Phase>()
        onFinished(helper) {
            mission?.close()
            transfer.close()
            for (world in worlds) {
                val container = checkNotNull(SubLevelContainer.getContainer(world))
                val body = container.getSubLevel(shipId) as? ServerSubLevel
                if (body != null) {
                    VacuumTestBlueprint.discardGlue(body)
                    container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                }
                SubLevelPhysicsSystem.require(world).paused = false
            }
            terrain.forEach { (pos, state) -> level.setBlock(pos, state, 3) }
            pilot.close()
        }

        fun run() {
            tick++
            worlds.forEach { it.resetEmptyTime() }
            transfer.tick()
            helper.assertTrue(transfer.error == null, "Expedition transfer failed: ${transfer.error}")
            if (!transfer.busy) {
                for (world in worlds) {
                    val container = checkNotNull(SubLevelContainer.getContainer(world))
                    val body = container.getSubLevel(shipId) as? ServerSubLevel ?: continue
                    container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    if (pilot.player.level() === world) pilot.attach(body)
                }
            }
            if (tick == 620) {
                val engines = liquidEngines(body)
                helper.assertTrue(engines.size == 24 && engines.all { it.fuelAmountMb >= 900 }, "Exact expedition failed to prime all native liquid engines")
                helper.assertTrue(remainingFuel(body) == ExpeditionFuelSupply.INITIAL_FUEL_MB, "Priming changed finite onboard fuel stock")
                helper.assertTrue(powerEngines(body).all { !it.isCurrentFuelInfinite && it.inventory.getItem(0).count < ExpeditionFuelSupply.COAL_BLOCKS_PER_ENGINE }, "Native finite coal supply was not consumed")
                val p = FlightBody.position(body)
                helper.assertTrue(pilot.player.teleportTo(level, p.x, p.y + 3, p.z, emptySet(), 0f, 0f), "Pilot placement failed")
                pilot.acknowledge(body)
                site = LaunchSites.Site(body.uniqueId, p, Quaterniond(body.logicalPose().orientation()), Level.OVERWORLD)
                println(
                    "ORBITALIS_EXPEDITION_LAUNCH site=${checkNotNull(
                        site,
                    ).position} minY=${body.boundingBox().minY()} q=${body.logicalPose().orientation()} mass=${body.massTracker.mass} com=${body.massTracker.centerOfMass}",
                )
                helper.assertTrue(
                    FlightGuidanceFrames.earthState(body).velocity.length() < 0.3,
                    "Launch fixture did not settle relative to the rotating pad",
                )
                helper.assertTrue(
                    FlightGuidanceFrames.rootState(body).velocity.length() > 1.0,
                    "Launch fixture did not exercise Earth transport velocity",
                )
                mission = FlatOrbitalMission(pilot.player, body, checkNotNull(site), transfer, false)
            }
            val active = mission
            if (active != null) {
                if (active.waitingForCoastCheck) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(50_000_000L)
                }
                val before = active.phase
                val landed = active.tick(pilot.player)
                if (active.phase != before) println("ORBITALIS_EXPEDITION_MISSION tick=$tick ${active.status()}")
                if (tick % 500 == 0) {
                    println("ORBITALIS_EXPEDITION_MISSION tick=$tick ${active.status()}")
                    if (active.phase == FlatOrbitalMission.Phase.APPROACH) {
                        val p = FlightBody.position(active.body)
                        val q = active.body.logicalPose().orientation()
                        val v = SubLevelPhysicsSystem.require(active.body.level).getPhysicsHandle(active.body).getLinearVelocity(Vector3d())
                        println(
                            "ORBITALIS_EXPEDITION_APPROACH error=${p -
                                checkNotNull(
                                    site,
                                ).position
                            } minY=${active.body.boundingBox().minY()} q=$q siteQ=${checkNotNull(
                                site,
                            ).attitude} velocity=$v mass=${active.body.massTracker.mass} com=${active.body.massTracker.centerOfMass}",
                        )
                    }
                }
                stages.add(active.phase)
                if (active.phase != FlatOrbitalMission.Phase.TRANSFER) {
                    helper.assertTrue(remainingFuel(active.body) in 1..ExpeditionFuelSupply.INITIAL_FUEL_MB, "Finite expedition supply was exhausted or generated fuel")
                }
                if (active.phase != before && active.phase == FlatOrbitalMission.Phase.TRANSFER) {
                    val root = FlightGuidanceFrames.rootState(active.body).velocity
                    transferVelocity = Vector3d(root.x, root.y, root.z)
                    handoffFuel = remainingFuel(active.body)
                    handoffCoal = powerEngines(active.body).sumOf { it.inventory.getItem(0).count }
                    helper.assertTrue(checkNotNull(transferVelocity).length() > 15.0, "Transfer braked to zero")
                }
                if (before == FlatOrbitalMission.Phase.TRANSFER && active.phase != before) {
                    val root = FlightGuidanceFrames.rootState(active.body).velocity
                    val velocity = Vector3d(root.x, root.y, root.z)
                    helper.assertTrue(
                        velocity.distance(checkNotNull(transferVelocity)) < 8.0,
                        "Transfer lost/reversed motion",
                    )
                    val fuel = remainingFuel(active.body)
                    val coal = powerEngines(active.body).sumOf { it.inventory.getItem(0).count }
                    helper.assertTrue(fuel > 0 && fuel == handoffFuel, "Handoff lost or created finite liquid fuel: $handoffFuel -> $fuel")
                    helper.assertTrue(coal in (checkNotNull(handoffCoal) - 4)..checkNotNull(handoffCoal), "Handoff lost or created coal inventory: $handoffCoal -> $coal")
                    helper.assertTrue(liquidEngines(active.body).size == 24 && powerEngines(active.body).size == 4, "Handoff lost native expedition machinery")
                    handoffs++
                }
                if (active.phase == FlatOrbitalMission.Phase.COAST && !returned && AutopilotSession.enginesFaded(active.body)) {
                    val fuel = remainingFuel(active.body)
                    if (coastFuel == null) coastFuel = fuel
                    helper.assertTrue(fuel == coastFuel, "Unpowered free coast consumed or generated liquid fuel")
                    val sample =
                        org.cbi.orbitalis.minecraft.flight.CelestialFlight
                            .sample(active.body.level)
                    val current = FlightGuidanceFrames.rootState(active.body)
                    val p = current.position
                    val local = sample.nativeState(current, Level.OVERWORLD).position
                    val v = current.velocity
                    val energy = v.dot(v) * 0.5
                    coastPrevious?.let { old ->
                        coastWork +=
                            sample.dynamics.acceleration((p + old.position) * 0.5, -0.025).dot((v + old.velocity) * 0.5) * 0.05
                    }
                    coastPrevious = current
                    val angle = atan2(p.x, p.y)
                    if (coastTicks++ == 0) {
                        firstEnergy = energy
                        previousAngle = angle
                    }
                    coastAngle += OrbitalGuidance.angleStep(previousAngle, angle)
                    previousAngle = angle
                    maximumEnergyError = maxOf(maximumEnergyError, abs((energy - firstEnergy - coastWork) / firstEnergy))
                    helper.assertTrue(maximumEnergyError < 0.01, "Unpowered gravity work/energy residual: $maximumEnergyError")
                    val clearance =
                        Vector3(
                            maxOf(0.0, abs(local.x) - FlatPlanetSpace.HALF_SIZE),
                            maxOf(0.0, abs(local.y) - 64),
                            maxOf(
                                0.0,
                                abs(local.z) - FlatPlanetSpace.HALF_SIZE,
                            ),
                        ).length()
                    helper.assertTrue(clearance > 24.0, "Free trajectory approached planet too closely: $clearance")
                    if (coastAngle <= -2 * PI) {
                        helper.assertTrue(coastTicks > 1000, "No complete native free revolution")
                        println("ORBITALIS_EXPEDITION_NATIVE_ORBIT ticks=$coastTicks angle=$coastAngle energyError=$maximumEnergyError")
                        val bookmarks = LaunchSites()
                        bookmarks.put(pilot.player.uuid, checkNotNull(site))
                        val saved =
                            LaunchSites
                                .load(
                                    bookmarks.save(CompoundTag(), level.registryAccess()),
                                    level.registryAccess(),
                                ).require(pilot.player.uuid, active.body.uniqueId)
                        active.close()
                        mission = FlatOrbitalMission(pilot.player, active.body, saved, transfer, true)
                        returned = true
                    }
                }
                if (landed) {
                    helper.assertTrue(returned && handoffs == 2 && active.body.level === level, "Round trip incomplete")
                    helper.assertTrue(
                        (FlightBody.position(active.body) - checkNotNull(site).position).length() < 0.5,
                        "Missed launch pad",
                    )
                    val cargo =
                        active.body.plot.loadedChunks
                            .flatMap { it.chunk.blockEntities.values }
                            .filterIsInstance<ChestBlockEntity>()
                            .map { it.getItem(0) }
                    helper.assertTrue(cargo.size == 4 && cargo.all { it.`is`(Items.COBBLESTONE) && it.count == 16 }, "Exact expedition cargo lost")
                    helper.assertTrue(remainingFuel(active.body) in 1 until ExpeditionFuelSupply.INITIAL_FUEL_MB, "Round trip did not consume finite onboard fuel or exhausted all reserve")
                    helper.assertTrue(powerEngines(active.body).all { !it.isCurrentFuelInfinite }, "Expedition acquired infinite coal power")
                    helper.assertTrue(AutopilotSession.enginesFaded(active.body), "Landing still needs thrust")
                    helper.assertTrue(
                        stages.containsAll(
                            listOf(
                                FlatOrbitalMission.Phase.ASCENT,
                                FlatOrbitalMission.Phase.CLIMB,
                                FlatOrbitalMission.Phase.INSERTION,
                                FlatOrbitalMission.Phase.COAST,
                                FlatOrbitalMission.Phase.RETURN,
                                FlatOrbitalMission.Phase.DESCENT,
                                FlatOrbitalMission.Phase.APPROACH,
                                FlatOrbitalMission.Phase.TOUCHDOWN,
                            ),
                        ),
                        "Mission skipped phases",
                    )
                    println("ORBITALIS_EXPEDITION_LANDED tick=$tick ${active.status()}")
                    helper.succeed()
                    return
                }
            }
            helper.runAfterDelay(1) { run() }
        }
        run()
    }
}
