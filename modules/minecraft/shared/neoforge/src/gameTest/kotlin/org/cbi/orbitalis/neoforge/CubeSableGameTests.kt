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

import com.mojang.authlib.GameProfile
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.lab.CubePlanetLab
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerMode
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID

/** Native Rapier bodies and the controlling-player collision path, without a fake ServerPlayer override. */
@PrefixGameTestTemplate(false)
object CubeSableGameTests {
    private class Scene(
        helper: GameTestHelper,
    ) {
        val level = checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val bodies = mutableListOf<ServerSubLevel>()
        val players = mutableListOf<Player>()

        init {
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
                    ) = close()

                    override fun testAddedForRerun(
                        test: GameTestInfo,
                        retry: GameTestInfo,
                        runner: GameTestRunner,
                    ) {}
                },
            )
        }

        fun close() {
            players.forEach { it.discard() }
            bodies.forEach {
                container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
            }
            bodies.clear()
        }

        fun deck(
            up: Direction,
            radius: Double,
        ): ServerSubLevel {
            val pose = Pose3d()
            val n = Vec3.atLowerCornerOf(up.normal)
            val p = CubePlayerPhysics.CENTRE.add(n.scale(radius))
            pose.position().set(p.x, p.y, p.z)
            pose.orientation().set(Quaterniond(CubePlayerPhysics.rotation(up)))
            val body = container.allocateNewSubLevel(pose) as ServerSubLevel
            bodies.add(body)
            body.plot.newEmptyChunk(body.plot.centerChunk)
            for (x in -2..2) {
                for (z in -2..2) {
                    check(body.plot.embeddedLevelAccessor.setBlock(BlockPos(x, 0, z), Blocks.IRON_BLOCK.defaultBlockState(), 3))
                }
            }
            body.updateLastPose()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            return body
        }

        fun player(
            up: Direction,
            centre: Vec3,
        ): Player {
            val player =
                object : Player(level, BlockPos.containing(centre), 0f, GameProfile(UUID.randomUUID(), "cube-contact")) {
                    override fun isSpectator(): Boolean = false

                    override fun isCreative(): Boolean = false

                    override fun isLocalPlayer(): Boolean = true
                }
            players.add(player)
            (player as CubePlayerMode).orbitalisSetCubeEnrolled(true)
            (player as CubePlayerFrame).orbitalisSetUp(up)
            player.setPos(centre.add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
            CubePlayerPhysics.refresh(player)
            return player
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "cube_streaming", timeoutTicks = 180)
    fun cubeColdChunkPreservesNativeState(helper: GameTestHelper) {
        val scene = Scene(helper)
        val pipeline =
            SubLevelPhysicsSystem
                .require(
                    scene.level,
                ).pipeline as org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline
        val collisionBefore = pipeline.residentColliderCount()
        val wasConstructing =
            org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline
                .constructing()
        var body =
            try {
                org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline
                    .constructing(true)
                scene.container.allocateNewSubLevel(Pose3d().also { it.position().set(4096.0, 100.0, 4096.0) }) as ServerSubLevel
            } finally {
                org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline
                    .constructing(wasConstructing)
            }
        scene.bodies.add(body)
        body.userDataTag =
            net.minecraft.nbt.CompoundTag().also {
                it.putInt("orbitalis_cube_frame_version", 1)
                it.putString("orbitalis_cube_face", "up")
            }
        val origin = body.plot.centerBlock
        body.logicalPose().rotationPoint().set(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
        body.plot.newEmptyChunk(body.plot.centerChunk)
        val global =
            net.minecraft.world.level
                .ChunkPos(body.plot.centerChunk.x + 1, body.plot.centerChunk.z)
        body.plot.newEmptyChunk(global)
        val local = body.plot.toLocal(global)
        (body.plot as org.cbi.orbitalis.minecraft.integration.sable.PlanetPlotAccess).orbitalisDisableExpansion()
        val accessor = body.plot.embeddedLevelAccessor
        accessor.setBlock(BlockPos(0, -1, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3)
        accessor.setBlock(BlockPos(10, -1, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3)
        accessor.setBlock(BlockPos(10, 0, 0), Blocks.CHEST.defaultBlockState(), 3)
        val chestPos = origin.offset(10, 0, 0)
        val chest = scene.level.getBlockEntity(chestPos) as net.minecraft.world.level.block.entity.ChestBlockEntity
        chest.setItem(
            4,
            net.minecraft.world.item
                .ItemStack(net.minecraft.world.item.Items.DIAMOND, 37),
        )
        scene.level.scheduleTick(chestPos.below(), Blocks.IRON_BLOCK, 100)
        body.updateLastPose()
        body.forceUpdateGlobalBounds()
        scene.container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        val chunks = org.cbi.orbitalis.minecraft.integration.sable.SablePlanetChunks
        helper.runAfterDelay(5) {
            helper.assertTrue(pipeline.residentColliderCount() == collisionBefore + 2, "Static chunk colliders were not registered")
            val oldHolder = body.plot.getChunkHolder(local)
            helper.assertTrue(chunks.evict(body, local), "Chunk did not become cold")
            helper.assertTrue(body.plot.getChunkHolder(local) == null && chunks.isCold(body, local), "Chunk has two resident owners")
            helper.assertTrue(chest.isRemoved, "Cold chest still ticks")
            helper.runAfterDelay(5) {
                helper.assertTrue(pipeline.residentColliderCount() == collisionBefore + 1, "Cold chunk collider remains in physics")
                // Save with the chunk cold, then use Sable's actual unload/full-load lifecycle.
                val id = body.uniqueId
                scene.container.holdingChunkMap.saveAll()
                scene.container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                scene.container.holdingChunkMap.moveToUnloaded(
                    body,
                    net.minecraft.world.level
                        .ChunkPos(256, 256),
                )
                helper.assertTrue(scene.container.allSubLevels.none { it.uniqueId == id }, "Native unload did not remove the body")
                val unloadedBody = body
                helper.runAfterDelay(5) {
                    // Sable may already have reloaded it from a ready parent-world chunk.
                    if (scene.container.allSubLevels.none { it.uniqueId == id }) {
                        scene.container.holdingChunkMap
                            .getHoldingSubLevel(id)
                            ?.let { scene.container.holdingChunkMap.loadHoldingSubLevel(it) }
                    }
                    body = scene.container.allSubLevels.single { it.uniqueId == id }
                    helper.assertTrue(body !== unloadedBody, "Native reload reused the previous body")
                    scene.bodies.clear()
                    scene.bodies.add(body)
                    scene.container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    helper.assertTrue(chunks.isCold(body, local) && body.plot.getChunkHolder(local) == null, "Reload lost cold ownership")
                    body.plot.expandIfNecessary(origin.offset(7, 0, 0))
                    helper.assertTrue(body.plot.getChunkHolder(local) == null, "Native expansion replaced a cold chunk with air")
                    helper.assertTrue(chunks.restore(body, local), "Cold restore failed")
                    helper.assertTrue(body.plot.getChunkHolder(local) !== oldHolder, "Restoration retained the unloaded LevelChunk")
                    val restored = scene.level.getBlockEntity(chestPos) as net.minecraft.world.level.block.entity.ChestBlockEntity
                    helper.assertTrue(
                        restored.getItem(4).count == 37 && restored.getItem(4).`is`(net.minecraft.world.item.Items.DIAMOND),
                        "Cold checkpoint lost inventory",
                    )
                    helper.assertTrue(
                        scene.level.blockTicks.hasScheduledTick(chestPos.below(), Blocks.IRON_BLOCK),
                        "Cold checkpoint lost scheduled block tick",
                    )
                    helper.assertTrue(chunks.coldCount(body) == 0 && !chunks.restore(body, local), "Cold checkpoint restored twice")
                    helper.assertTrue(chunks.evict(body, local), "Second unload failed")
                    val valid = checkNotNull(body.userDataTag).copy()
                    body.userDataTag =
                        valid.copy().also {
                            it.getCompound("orbitalis_planet_cold_chunks").putInt("version", 999)
                        }
                    helper.assertTrue(runCatching { chunks.restore(body, local) }.isFailure, "Unknown cold version was accepted")
                    helper.assertTrue(body.plot.getChunkHolder(local) == null, "Invalid version created a resident chunk")
                    body.userDataTag =
                        valid.copy().also {
                            it
                                .getCompound("orbitalis_planet_cold_chunks")
                                .getCompound("chunks")
                                .putByteArray(local.toLong().toString(), byteArrayOf(1, 2, 3))
                        }
                    helper.assertTrue(runCatching { chunks.restore(body, local) }.isFailure, "Corrupt snapshot was accepted")
                    helper.assertTrue(
                        body.plot.getChunkHolder(local) == null && chunks.isCold(body, local),
                        "Corruption discarded cold authority",
                    )
                    body.userDataTag = valid
                    helper.assertTrue(chunks.restore(body, local), "Recovery from rejected snapshot failed")
                    helper.runAfterDelay(5) {
                        helper.assertTrue(
                            pipeline.residentColliderCount() == collisionBefore + 2,
                            "Restored chunk has missing or duplicate collider",
                        )
                        println("ORBITALIS_PLANET_COLD nativeReload=true inventory=37 pendingTick=true colliders=2")
                        helper.succeed()
                    }
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "cube_sable", timeoutTicks = 100)
    fun cubeShipsFallTowardAllFaces(helper: GameTestHelper) {
        val scene = Scene(helper)
        val bodies = Direction.entries.map { scene.deck(it, 45.0) }
        helper.runAfterDelay(5) {
            val before = bodies.map(SableFlightGravity::reading)
            helper.runAfterDelay(20) {
                for ((index, body) in bodies.withIndex()) {
                    val after = SableFlightGravity.reading(body)
                    val force = before[index].acceleration
                    val dv = after.velocity.sub(before[index].velocity, Vector3d())
                    val g = Vector3d(force.x, force.y, force.z)
                    helper.assertTrue(dv.dot(g) > 0.0, "Body accelerates away from face ${Direction.entries[index]}: $dv / $g")
                    helper.assertTrue(dv.length() > g.length() * 0.85, "Cube gravity was omitted or attenuated: $dv / $g")
                    helper.assertTrue(
                        SubLevelPhysicsSystem
                            .require(scene.level)
                            .getPhysicsHandle(body)
                            .getAngularVelocity(Vector3d())
                            .length() < 1e-5,
                        "Gravity imposed body orientation",
                    )
                }
                // This is well outside the old 64-block player scene radius.
                val far = scene.deck(Direction.EAST, 200.0)
                helper.runAfterDelay(5) {
                    val sample = SableFlightGravity.reading(far)
                    helper.assertTrue(sample.acceleration.x < 0 && sample.velocity.x < 0, "Far construction lost attraction")
                    helper.succeed()
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "cube_sable_landing", timeoutTicks = 180)
    fun cubeShipsLandOnAllFaces(helper: GameTestHelper) {
        val scene = Scene(helper)
        CubePlanetLab.forServer(helper.level.server).prepare()
        val bodies = Direction.entries.map { scene.deck(it, 24.0) }
        val frames =
            org.cbi.orbitalis.minecraft.integration.sable
                .SableCubePlanet(scene.level)
                .bodies
        val poses =
            frames.associateWith {
                dev.ryanhcode.sable.companion.math
                    .Pose3d(it.logicalPose())
            }
        for (frame in frames) {
            helper.assertTrue(frame.massTracker.mass == 0.0, "Planet chart still computes rigid-body mass")
            SubLevelPhysicsSystem.require(scene.level).getPhysicsHandle(frame).applyLinearImpulse(Vector3d(1e12, -2e12, 3e12))
        }
        helper.runAfterDelay(120) {
            for (frame in frames) {
                val pose = checkNotNull(poses[frame])
                helper.assertTrue(frame.logicalPose().position().distance(pose.position()) < 1e-12, "Static chart moved after impact")
                helper.assertTrue(frame.logicalPose().orientation().equals(pose.orientation(), 1e-12), "Static chart rotated after impact")
            }
            for ((index, body) in bodies.withIndex()) {
                val reading = SableFlightGravity.reading(body)
                val p = Vec3(reading.position.x, reading.position.y, reading.position.z).subtract(CubePlayerPhysics.CENTRE)
                val radius = p.dot(Vec3.atLowerCornerOf(Direction.entries[index].normal))
                helper.assertTrue(
                    radius in 12.0..22.0,
                    "Construction fell through or failed to approach face ${Direction.entries[index]}: $radius",
                )
                helper.assertTrue(
                    reading.velocity.length() < 0.3,
                    "Construction failed to settle on ${Direction.entries[index]}: ${reading.velocity}",
                )
            }
            println("ORBITALIS_CUBE_SABLE_LANDING faces=6 nativeTerrainContact=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "cube_sable_contact", timeoutTicks = 100)
    fun cubePlayerContactsMovingDeckOnAllFaces(helper: GameTestHelper) {
        val scene = Scene(helper)
        val bodies = Direction.entries.map { scene.deck(it, 35.0) }
        helper.runAfterDelay(5) {
            for ((index, body) in bodies.withIndex()) {
                val up = Direction.entries[index]
                val n = Vec3.atLowerCornerOf(up.normal)
                val sample = SableFlightGravity.reading(body)
                val com = Vec3(sample.position.x, sample.position.y, sample.position.z)
                body.updateLastPose()
                val player = scene.player(up, com.add(n.scale(1.45)))
                player.deltaMovement = n.scale(-0.2)
                val before = player.position()
                player.move(MoverType.SELF, player.deltaMovement)
                helper.assertTrue(player.onGround(), "No native deck support on $up: before=$before after=${player.position()}")
                helper.assertTrue(
                    (player as EntityMovementExtension).`sable$getTrackingSubLevel`() === body,
                    "No native ship tracking on $up",
                )
                helper.assertTrue(player.position().subtract(before).dot(n) > -0.15, "Player passed through deck on $up")
                val tangent = CubePlayerPhysics.rotate(Vec3(0.2, 0.0, 0.0), up)
                body.updateLastPose()
                body.logicalPose().position().add(tangent.x, tangent.y, tangent.z)
                body.forceUpdateGlobalBounds()
                val start = player.position()
                player.deltaMovement = n.scale(-0.03)
                player.move(MoverType.SELF, player.deltaMovement)
                helper.assertTrue(
                    player.position().subtract(start).dot(tangent.normalize()) > 0.15,
                    "Moving deck did not carry player on $up: ${player.position().subtract(start)}",
                )
                helper.assertTrue(
                    CubePlayerPhysics.normal(player).dot(CubePlayerPhysics.gravity(player).normalize().scale(-1.0)) > .9999,
                    "Ship changed planetary player orientation",
                )
                // Holding contact must consume inward acceleration rather than accumulate a hidden
                // fall speed while the native solver keeps the collider above the moving floor.
                body.updateLastPose()
                repeat(20) { CubePlayerPhysics.travel(player, Vec3.ZERO) }
                helper.assertTrue(player.onGround(), "Deck contact became unstable on $up")
                helper.assertTrue(
                    kotlin.math.abs(player.deltaMovement.dot(CubePlayerPhysics.normal(player))) < 1e-6,
                    "Gravity accumulated through the supporting deck on $up",
                )
                // Move away from support: Sable must release tracking so normal flight can resume.
                body.updateLastPose()
                player.jumpFromGround()
                repeat(6) { player.move(MoverType.SELF, n.scale(0.23)) }
                helper.assertTrue(!player.onGround(), "Jump remained grounded on $up")
                // Native tracking is a nearby moving frame, not the grounded flag. Leave its
                // broad-phase margin before requiring frame release, just as in ordinary Sable.
                repeat(24) { player.move(MoverType.SELF, n.scale(0.23)) }
                helper.assertTrue(player.`sable$getTrackingSubLevel`() == null, "Departure failed to release ship frame on $up")
                // Approach this same deck from underneath. Native terrain movement normally consumes
                // blocked vertical velocity; the cube adapter must do that along local up as well.
                player.setPos(com.subtract(n.scale(1.45)).add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
                player.deltaMovement = n.scale(0.2)
                player.move(MoverType.SELF, player.deltaMovement)
                helper.assertTrue(player.verticalCollision && !player.onGround(), "Underside was not a ceiling on $up")
                helper.assertTrue(
                    kotlin.math.abs(player.deltaMovement.dot(CubePlayerPhysics.normal(player))) < 1e-6,
                    "Ceiling retained inward head velocity on $up",
                )
            }
            println("ORBITALIS_CUBE_SABLE_CONTACT faces=6 landing=true carry=true departure=true")
            helper.succeed()
        }
    }
}
