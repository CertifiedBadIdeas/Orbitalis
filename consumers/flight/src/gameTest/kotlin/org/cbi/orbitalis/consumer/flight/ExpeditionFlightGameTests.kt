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
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity
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
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.control.FlightBody
import org.cbi.orbitalis.minecraft.integration.propulsion.AutopilotSession
import org.cbi.orbitalis.minecraft.integration.propulsion.LiquidThrusterSupport
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.PI
import kotlin.math.min

@PrefixGameTestTemplate(false)
object ExpeditionFlightGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 6000, batch = "expedition_hold")
    fun nativeExpeditionAssemblyAndHold(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val origin = BlockPos(0, 82, 0)
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val previous = container.allSubLevels.map { it.uniqueId }.toSet()
        val forced = mutableSetOf<Long>()
        val chunks = mutableSetOf<Long>()
        val saved = linkedMapOf<BlockPos, BlockState>()
        val glue = mutableSetOf<UUID>()
        val owned = mutableSetOf<UUID>()
        var session: AutopilotSession? = null
        lateinit var body: ServerSubLevel
        lateinit var attitude: Quaterniond
        var fuelAtLiftoff = 0
        var placedAt = -1
        var assembledAt = -1
        var tick = 0

        onFinished(helper) {
            session?.close()
            for (b in container.allSubLevels.toList()) {
                if (b.uniqueId in owned || b.splitFromSubLevel in owned) {
                    VacuumTestBlueprint.discardGlue(b)
                    container.removeForceLoadTicket(b, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    if (!b.isRemoved) container.removeSubLevel(b, SubLevelRemovalReason.REMOVED)
                }
            }
            for (id in glue) level.getEntity(id)?.discard()
            for ((pos, state) in saved) level.setBlock(pos, state, 3)
            for (key in forced) level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false)
        }
        for (x in ((origin.x - 8) shr 4)..((origin.x + 8) shr 4)) {
            for (z in ((origin.z - 17) shr 4)..((origin.z + 17) shr 4)) {
                val key = ChunkPos.asLong(x, z)
                chunks.add(key)
                if (!level.forcedChunks.contains(key)) {
                    level.setChunkForced(x, z, true)
                    forced.add(key)
                }
                level.getChunk(x, z)
            }
        }

        fun engines() =
            body.plot.loadedChunks
                .flatMap { it.chunk.blockEntities.values }
                .filterIsInstance<ThrusterBlockEntity>()

        fun remainingFuel(): Int {
            val tanks =
                body.plot.loadedChunks
                    .flatMap { it.chunk.blockEntities.values }
                    .filterIsInstance<FluidTankBlockEntity>()
            return tanks.filter { it.isController }.sumOf { it.tankInventory.fluidAmount } + engines().sumOf { it.fuelAmountMb }
        }

        fun checkLayout() {
            helper.assertTrue(!body.isRemoved, "Expedition was removed or split")
            helper.assertTrue(
                container.allSubLevels.count {
                    it.uniqueId !in previous
                } == 1,
                "Exact expedition blueprint split into multiple native bodies",
            )
            helper.assertTrue(
                org.cbi.orbitalis.minecraft.integration.sable.SableShipChunks
                    .occupied(body)
                    .size <= 9 &&
                    body.plot.boundingBox.volume() <= 4030,
                "Expedition exceeds native transfer bounds: ${body.plot.boundingBox}; " +
                    "volume=${body.plot.boundingBox.volume()}; chunks=${body.plot.loadedChunks.map {
                        it.chunk.pos
                    }}",
            )
            val blocks = body.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }
            val engines = blocks.filterIsInstance<ThrusterBlockEntity>()
            helper.assertTrue(
                engines.size == ExpeditionFuelSupply.engines.size &&
                    engines.all {
                        it.isController && !it.isMultiblock
                    },
                "Expedition lost independent liquid engines: ${engines.size}",
            )
            val cargo = blocks.filterIsInstance<ChestBlockEntity>()
            helper.assertTrue(
                cargo.size == 4 &&
                    cargo.all {
                        it.getItem(0).`is`(Items.COBBLESTONE) && it.getItem(0).count == 16
                    },
                "Expedition cargo sentinel changed",
            )
            helper.assertTrue(
                remainingFuel() in 1..ExpeditionFuelSupply.INITIAL_FUEL_MB,
                "Finite expedition fuel left its native budget: ${remainingFuel()}",
            )
        }

        fun step() {
            tick++
            level.resetEmptyTime()
            if (placedAt < 0) {
                helper.assertTrue(tick < 4000, "Expedition entity chunks did not become ready")
                if (!chunks.all(level::areEntitiesLoaded) || !level.isPositionEntityTicking(origin)) {
                    java.util.concurrent.locks.LockSupport
                        .parkNanos(5_000_000L)
                    helper.runAfterDelay(1) { step() }
                    return
                }
                for (x in -8..8) {
                    for (y in -1..12) {
                        for (z in -17..17) {
                            val pos = origin.offset(x, y, z)
                            saved[pos] = level.getBlockState(pos)
                            level.setBlock(pos, if (y == -1) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState(), 3)
                        }
                    }
                }
                ExpeditionBlueprint.place(level, origin)
                val allGlue = level.getAllEntities().filterIsInstance<SuperGlueEntity>()
                allGlue
                    .filter {
                        it.boundingBox.intersects(
                            net.minecraft.world.phys
                                .AABB(origin)
                                .inflate(20.0),
                        )
                    }.forEach { glue.add(it.uuid) }
                helper.assertTrue(glue.size == 1, "Exact expedition glue missing")
                placedAt = tick
            } else if (assembledAt < 0) {
                val staticEngines =
                    ExpeditionFuelSupply.engines.map {
                        level.getBlockEntity(
                            origin.offset(it.offset),
                        ) as ThrusterBlockEntity
                    }
                helper.assertTrue(
                    tick - placedAt < 1500,
                    "Native pumps failed to prime expedition engines: ${staticEngines.map { it.fuelAmountMb }}",
                )
                if (staticEngines.all { it.fuelAmountMb >= 800 }) {
                    val assembler = level.getBlockEntity(origin.offset(ExpeditionBlueprint.assembler)) as PhysicsAssemblerBlockEntity
                    assembler.assembleOrDisassemble()
                    val created = container.allSubLevels.filter { it.uniqueId !in previous }
                    owned.addAll(created.map { it.uniqueId })
                    helper.assertTrue(created.size == 1, "Native expedition assembly failed: ${created.size} bodies")
                    body = created.single() as ServerSubLevel
                    container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    assembledAt = tick
                }
            } else {
                val flightTick = tick - assembledAt
                if (flightTick == 10) {
                    checkLayout()
                    val start = FlightBody.position(body)
                    attitude = Quaterniond(body.logicalPose().orientation())
                    val authority =
                        engines().filter { it.facing == net.minecraft.core.Direction.UP }.sumOf { LiquidThrusterSupport.capacity(it) } /
                            body.massTracker.mass
                    println(
                        "ORBITALIS_EXPEDITION mass=${body.massTracker.mass} liftAcceleration=$authority chunks=${body.plot.loadedChunks.size} bounds=${body.plot.boundingBox} fuel=${remainingFuel()}",
                    )
                    fuelAtLiftoff = remainingFuel()
                    session = AutopilotSession(body, Vector3(start.x, start.y + 16.0, start.z))
                }
                if (flightTick >= 10) session!!.update()
                if (flightTick == 800) {
                    checkLayout()
                    val handle = SubLevelPhysicsSystem.require(level).getPhysicsHandle(body)
                    helper.assertTrue(
                        (FlightBody.position(body) - session!!.target).length() < 1.0,
                        "Expedition hold failed: ${session!!.status()}",
                    )
                    helper.assertTrue(handle.getLinearVelocity(Vector3d()).length() < 0.4, "Expedition translation did not settle")
                    helper.assertTrue(handle.getAngularVelocity(Vector3d()).length() < 0.04, "Expedition rotation did not settle")
                    val angle = Quaterniond(body.logicalPose().orientation()).conjugate().mul(attitude).angle()
                    helper.assertTrue(min(angle, 2 * PI - angle) < 0.08, "Expedition attitude did not settle")
                    helper.assertTrue(remainingFuel() < fuelAtLiftoff, "Powered expedition hold consumed no finite fuel")
                    println("ORBITALIS_EXPEDITION_HOLD fuelUsed=${fuelAtLiftoff - remainingFuel()} ${session!!.status()}")
                    helper.succeed()
                    return
                }
            }
            helper.runAfterDelay(1) { step() }
        }
        helper.runAfterDelay(1) { step() }
    }
}
