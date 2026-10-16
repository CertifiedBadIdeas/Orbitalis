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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync
import org.joml.Quaterniond
import org.joml.Vector3d
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.atan2

@PrefixGameTestTemplate(false)
object OrbitalMissionGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 40000, batch = "orbital_mission")
    fun nativeOrbitalInsertionAndReturn(helper: GameTestHelper) {
        val rig = PropulsionGameTests.Rig(helper, -1400.0)
        val pilot = InertPilot(helper)
        val frame = FlightTestSpace
        val worlds = listOf(frame.SURFACE, frame.ORBIT, frame.SPACE).map { checkNotNull(helper.level.server.getLevel(it)) }
        val terrain = mutableMapOf<BlockPos, BlockState>()
        for (x in -1405..-1395) {
            for (z in -5..5) {
                val pos = BlockPos(x, 230, z)
                terrain[pos] = rig.level.getBlockState(pos)
                rig.level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3)
            }
        }
        val transfer =
            SableShipTransfer(
                helper.level.server,
                Files.createTempDirectory("orbitalis-mission-test-"),
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

        class Sequence :
            Runnable,
            AutoCloseable {
            var mission: OrbitalMission? = null
            lateinit var site: LaunchSites.Site
            var tick = 0
            var coastTicks = 0
            var askedToLand = false
            var cleaned = false
            var firstCoastEnergy = 0.0
            var previousCoastAngle = 0.0
            var coastAngle = 0.0
            val phases = mutableSetOf<OrbitalMission.Phase>()

            override fun close() {
                if (cleaned) return
                cleaned = true
                mission?.close()
                transfer.close()
                pilot.close()
                for (world in worlds) {
                    val container = checkNotNull(SubLevelContainer.getContainer(world))
                    val body = container.getSubLevel(rig.body.uniqueId) as? ServerSubLevel
                    if (body != null) {
                        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                    }
                    SubLevelPhysicsSystem.require(world).paused = false
                }
                terrain.forEach { (pos, state) -> rig.level.setBlock(pos, state, 3) }
            }

            override fun run() {
                tick++
                worlds.forEach { it.resetEmptyTime() }
                transfer.tick()
                helper.assertTrue(transfer.error == null, "Native mission transfer failed: ${transfer.error}")
                if (!transfer.busy) {
                    for (world in worlds) {
                        val container = checkNotNull(SubLevelContainer.getContainer(world))
                        val body = container.getSubLevel(rig.body.uniqueId) as? ServerSubLevel
                        if (body != null) {
                            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                            if (pilot.player.level() === world) pilot.attach(body)
                        }
                    }
                }
                if (tick == 100) {
                    rig.configure()
                    helper.assertTrue(
                        SubLevelPhysicsSystem
                            .require(rig.level)
                            .getPhysicsHandle(rig.body)
                            .getLinearVelocity(Vector3d())
                            .length() < 0.3,
                        "Native rig did not settle on the test terrain",
                    )
                    val chestPos = BlockPos(0, 1, 0)
                    rig.body.plot.embeddedLevelAccessor
                        .setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3)
                    (
                        rig.body.plot.embeddedLevelAccessor.getBlockEntity(
                            chestPos,
                        ) as ChestBlockEntity
                    ).setItem(0, ItemStack(Items.COBBLESTONE, 16))
                }
                if (tick == 120) {
                    val p = AutopilotSession.position(rig.body)
                    helper.assertTrue(
                        pilot.player.teleportTo(rig.level, p.x, p.y + 3, p.z, emptySet(), 0.0f, 0.0f),
                        "Could not place inert pilot",
                    )
                    pilot.acknowledge(rig.body)
                    site = LaunchSites.Site(rig.body.uniqueId, p, Quaterniond(rig.body.logicalPose().orientation()))
                    mission = OrbitalMission(pilot.player, rig.body, site, transfer, false)
                }
                val active = mission
                if (active != null) {
                    val landed = active.tick(pilot.player)
                    if (phases.add(active.phase)) println("ORBITALIS_MISSION tick=$tick ${active.status()}")
                    if (tick % 1000 == 0) println("ORBITALIS_MISSION tick=$tick ${active.status()}")
                    if (active.phase == OrbitalMission.Phase.COAST && !askedToLand && AutopilotSession.enginesFaded(active.body)) {
                        val body = active.body
                        val radial = frame.globalPosition(body.level, AutopilotSession.position(body)) - frame.CENTRE
                        val velocity = SubLevelPhysicsSystem.require(body.level).getPhysicsHandle(body).getLinearVelocity(Vector3d())
                        val energy = velocity.lengthSquared() / 2 - 353160000 / radial.length()
                        val angle = atan2(radial.x, radial.y)
                        if (coastTicks++ == 0) {
                            firstCoastEnergy = energy
                            previousCoastAngle = angle
                        }
                        coastAngle += OrbitalGuidance.angleStep(previousCoastAngle, angle)
                        previousCoastAngle = angle
                        helper.assertTrue(
                            energy < 0 && abs((energy - firstCoastEnergy) / firstCoastEnergy) < 0.001,
                            "Engines-off orbit did not preserve a bound orbit energy",
                        )
                        if (coastTicks >= 200) {
                            helper.assertTrue(coastAngle > 0.05, "Orbit did not advance after insertion")
                            helper.assertTrue(AutopilotSession.enginesFaded(body), "Coast retained real engine power")
                            val bookmarks = LaunchSites()
                            bookmarks.put(pilot.player.uuid, site)
                            val restored =
                                LaunchSites
                                    .load(bookmarks.save(CompoundTag(), rig.level.registryAccess()), rig.level.registryAccess())
                                    .require(pilot.player.uuid, body.uniqueId)
                            active.close()
                            // Exercise manual return from a restored bookmark, with no resumed controller.
                            mission = OrbitalMission(pilot.player, body, restored, transfer, true)
                            askedToLand = true
                        }
                    }
                    if (landed) {
                        helper.assertTrue(
                            phases.containsAll(
                                listOf(
                                    OrbitalMission.Phase.CLIMB,
                                    OrbitalMission.Phase.INSERTION,
                                    OrbitalMission.Phase.COAST,
                                    OrbitalMission.Phase.RETURN,
                                    OrbitalMission.Phase.DESCENT,
                                    OrbitalMission.Phase.APPROACH,
                                    OrbitalMission.Phase.TOUCHDOWN,
                                ),
                            ),
                            "Mission skipped flight stages: $phases",
                        )
                        helper.assertTrue(
                            active.body.uniqueId == site.ship && active.body.level === rig.level,
                            "Mission changed ship identity or failed to return",
                        )
                        helper.assertTrue(
                            (AutopilotSession.position(active.body) - site.position).length() < 0.5,
                            "Return missed launch point",
                        )
                        val cargo =
                            active.body.plot.loadedChunks
                                .flatMap { it.chunk.blockEntities.values }
                                .filterIsInstance<ChestBlockEntity>()
                                .first()
                        helper.assertTrue(cargo.getItem(0).`is`(Items.COBBLESTONE) && cargo.getItem(0).count == 16, "Mission changed cargo")
                        helper.assertTrue(AutopilotSession.enginesFaded(active.body), "Landing retained engine support")
                        println("ORBITALIS_MISSION_LANDED tick=$tick ${active.status()}")
                        helper.succeed()
                        return
                    }
                }
                helper.runAfterDelay(1) { run() }
            }
        }
        val sequence = Sequence()
        onFinished(helper, sequence::close)
        helper.runAfterDelay(1, sequence)
    }
}
