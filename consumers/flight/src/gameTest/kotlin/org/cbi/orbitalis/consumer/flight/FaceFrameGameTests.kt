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

import com.mojang.authlib.GameProfile
import com.simibubi.create.content.logistics.vault.ItemVaultBlock
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.integration.sable.SableCubePlanet
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerMode
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import java.util.UUID
import kotlin.math.atan2

@PrefixGameTestTemplate(false)
object FaceFrameGameTests {
    private fun scene(helper: GameTestHelper): FaceFrameScene {
        val scene = FaceFrameScene(checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)))
        onFinished(helper, scene::close)
        scene.prepare()
        return scene
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "face_frames", timeoutTicks = 120)
    fun localFaceVaultsKeepNativeInventory(helper: GameTestHelper) {
        val scene = scene(helper)
        val positions = SableCubePlanet.faces.map { scene.world(it, 0.0, 0.0, 0.0) }
        helper.runAfterDelay(60) {
            for ((index, body) in scene.bodies.withIndex()) {
                val face = SableCubePlanet.faces[index]
                helper.assertTrue(
                    scene.world(face, 0.0, 0.0, 0.0).distanceTo(positions[index]) < .01,
                    "Anchored face moved index=$index before=${positions[index]} after=${scene.world(
                        face,
                        0.0,
                        0.0,
                        0.0,
                    )} pose=${body.logicalPose()}",
                )
                val up = scene.world(face, 0.0, 1.0, 0.0).subtract(scene.world(face, 0.0, 0.0, 0.0))
                helper.assertTrue(
                    up.dot(SableCubePlanet.normal(face)) > .999,
                    "Local up is wrong face=$face expected=${SableCubePlanet.normal(
                        face,
                    )}: $up body=${body.name} pose=${body.logicalPose()}",
                )
                val accessor = body.plot.embeddedLevelAccessor
                val vaults = (-8..-6).map { accessor.getBlockEntity(BlockPos(it, 0, -10)) as ItemVaultBlockEntity }
                val controller = checkNotNull(vaults.first().controllerBE)
                helper.assertTrue(controller.height == 3 && controller.width == 1, "Native vault failed to form on face $index")
                helper.assertTrue(vaults.all { it.controllerBE === controller }, "Vaults have different controllers")
                val first = checkNotNull(scene.level.getCapability(Capabilities.ItemHandler.BLOCK, vaults.first().blockPos, Direction.UP))
                val last = checkNotNull(scene.level.getCapability(Capabilities.ItemHandler.BLOCK, vaults.last().blockPos, Direction.DOWN))
                helper.assertTrue(first.insertItem(0, ItemStack(Items.DIAMOND, 37), false).isEmpty, "Native insertion failed")
                val extracted = last.extractItem(0, 37, false)
                helper.assertTrue(extracted.`is`(Items.DIAMOND) && extracted.count == 37, "Shared inventory lost contents")
                // Exercise Sable's native placement context, including its temporary entity-local
                // queries, rather than only trusting the prebuilt fixture's block states.
                val actor: Player =
                    object : Player(scene.level, BlockPos.ZERO, 0f, GameProfile(UUID.randomUUID(), "face-place")) {
                        override fun isSpectator(): Boolean = false

                        override fun isCreative(): Boolean = false

                        override fun isLocalPlayer(): Boolean = true
                    }
                try {
                    (actor as CubePlayerMode).orbitalisSetCubeEnrolled(true)
                    (actor as CubePlayerFrame).orbitalisSetUp(face)
                    actor.setPos(scene.world(face, -8.0, .91, -10.5).add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
                    CubePlayerPhysics.refresh(actor)
                    val before = actor.position()
                    val block = vaults.first().blockState.block
                    val floor = body.plot.centerBlock.offset(0, -1, -10)
                    for ((yaw, expected) in listOf(-90f to Direction.Axis.X, 0f to Direction.Axis.Z)) {
                        actor.yRot = yaw
                        actor.yHeadRot = yaw
                        val hit = BlockHitResult(Vec3.atCenterOf(floor).add(0.0, .5, 0.0), Direction.UP, floor, false)
                        val context = BlockPlaceContext(actor, InteractionHand.MAIN_HAND, ItemStack(block.asItem()), hit)
                        val placed = checkNotNull(block.getStateForPlacement(context))
                        helper.assertTrue(
                            placed.getValue(ItemVaultBlock.HORIZONTAL_AXIS) == expected,
                            "Native placement chose wrong local axis face=$face yaw=$yaw state=$placed",
                        )
                        helper.assertTrue(
                            actor.position().distanceTo(before) < 1e-6,
                            "Local placement query changed physical player position",
                        )
                    }
                } finally {
                    actor.discard()
                }
                val axis = scene.world(face, 1.0, 0.0, 0.0).subtract(scene.world(face, 0.0, 0.0, 0.0))
                helper.assertTrue(axis.length() > .999, "Vault did not follow face coordinates")
            }
            println("ORBITALIS_FACE_FRAMES faces=6 anchored=true nativeVaults=true sharedInventory=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "face_frames_walk", timeoutTicks = 100)
    fun playerWalksBetweenLocalFaceFrames(helper: GameTestHelper) {
        val scene = scene(helper)
        helper.runAfterDelay(8) {
            for (from in SableCubePlanet.faces) {
                for (destination in SableCubePlanet.faces.filter { it.axis != from.axis }) {
                    val other = Direction.entries.first { it.axis != from.axis && it.axis != destination.axis }
                    val relative =
                        SableCubePlanet
                            .normal(from)
                            .scale(16.91)
                            .add(SableCubePlanet.normal(destination).scale(10.0))
                            .add(SableCubePlanet.normal(other).scale(10.0))
                    val start = PlanetLabCoordinates.CENTRE.add(relative)
                    val player: Player =
                        object : Player(scene.level, BlockPos.containing(start), 0f, GameProfile(UUID.randomUUID(), "face-frame")) {
                            override fun isSpectator(): Boolean = false

                            override fun isCreative(): Boolean = false

                            override fun isLocalPlayer(): Boolean = true
                        }
                    try {
                        (player as CubePlayerMode).orbitalisSetCubeEnrolled(true)
                        (player as CubePlayerFrame).orbitalisSetUp(from)
                        player.setPos(start.add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
                        val heading =
                            CubePlayerPhysics
                                .rotation(
                                    from,
                                ).conjugate()
                                .transform(
                                    org.joml.Vector3f(
                                        destination.stepX.toFloat(),
                                        destination.stepY.toFloat(),
                                        destination.stepZ.toFloat(),
                                    ),
                                )
                        player.yRot = Math.toDegrees(atan2(-heading.x.toDouble(), heading.z.toDouble())).toFloat()
                        player.yHeadRot = player.yRot
                        CubePlayerPhysics.refresh(player)
                        var landed = false
                        for (tick in 0 until 240) {
                            CubePlayerPhysics.travel(player, Vec3(0.0, 0.0, 1.0))
                            val p = CubePlayerPhysics.centre(player).subtract(PlanetLabCoordinates.CENTRE)
                            helper.assertTrue(p.length() < 35.0, "Player escaped seam $from->$destination: $p")
                            if (CubePlayerPhysics.up(player) == destination && player.onGround() &&
                                p.dot(SableCubePlanet.normal(from)) < 13.0
                            ) {
                                landed = true
                                break
                            }
                        }
                        helper.assertTrue(
                            landed,
                            "Could not cross $from->$destination p=${player.position()} up=${CubePlayerPhysics.up(player)}",
                        )
                        helper.assertTrue(
                            (player as EntityMovementExtension).`sable$getTrackingSubLevel`() === scene.planet.body(destination),
                            "Destination chart did not own contact: $from->$destination",
                        )
                        repeat(20) { CubePlayerPhysics.travel(player, Vec3.ZERO) }
                        helper.assertTrue(player.onGround(), "Unstable destination: $from->$destination")
                    } finally {
                        player.discard()
                    }
                }
            }
            println("ORBITALIS_FACE_FRAMES_WALK directions=24 nativeContacts=true noTeleport=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "face_frames_reload", timeoutTicks = 160)
    fun localFaceFramesSurviveNativeReload(helper: GameTestHelper) {
        val scene = scene(helper)
        helper.runAfterDelay(20) {
            val container =
                checkNotNull(
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer
                        .getContainer(scene.level),
                )
            val ids = scene.bodies.map { it.uniqueId }
            for (body in scene.bodies) {
                val be = body.plot.embeddedLevelAccessor.getBlockEntity(BlockPos(-8, 0, -10)) as ItemVaultBlockEntity
                val handler = checkNotNull(scene.level.getCapability(Capabilities.ItemHandler.BLOCK, be.blockPos, Direction.UP))
                helper.assertTrue(handler.insertItem(0, ItemStack(Items.DIAMOND, 23), false).isEmpty, "Could not fill vault before reload")
                helper.assertTrue(
                    body.plot.embeddedLevelAccessor.setBlock(
                        BlockPos(8, -1, -10),
                        net.minecraft.world.level.block.Blocks.AIR
                            .defaultBlockState(),
                        3,
                    ),
                    "Excavation failed on ${body.name}: ${body.plot.embeddedLevelAccessor.getBlockState(BlockPos(8,-1,-10))}",
                )
            }
            container.holdingChunkMap.saveAll()
            // Native unload/reload serializes blocks, BEs and user data; no fixture reconstruction.
            for (body in scene.bodies.toList()) {
                if (!body.isRemoved) {
                    container.holdingChunkMap.moveToUnloaded(
                        body,
                        net.minecraft.world.level
                            .ChunkPos(BlockPos.containing(PlanetLabCoordinates.CENTRE)),
                    )
                }
            }
            for (id in ids) container.holdingChunkMap.getHoldingSubLevel(id)?.let { container.holdingChunkMap.loadHoldingSubLevel(it) }
            scene.planet.prepare()
            helper.assertTrue(scene.bodies.map { it.uniqueId } == ids, "Reload replaced chart identities")
            helper.runAfterDelay(40) {
                for (body in scene.bodies) {
                    helper.assertTrue(
                        body.plot.embeddedLevelAccessor
                            .getBlockState(BlockPos(8, -1, -10))
                            .isAir,
                        "Reload refilled excavation",
                    )
                    val be = body.plot.embeddedLevelAccessor.getBlockEntity(BlockPos(-8, 0, -10)) as ItemVaultBlockEntity
                    val handler = checkNotNull(scene.level.getCapability(Capabilities.ItemHandler.BLOCK, be.blockPos, Direction.UP))
                    val stack = handler.extractItem(0, 23, false)
                    helper.assertTrue(stack.`is`(Items.DIAMOND) && stack.count == 23, "Reload lost native vault inventory")
                    val face = checkNotNull(SableCubePlanet.direction(body))
                    helper.assertTrue(
                        scene.world(face, 0.0, 0.0, 0.0).distanceTo(SableCubePlanet.origin(face)) < .01,
                        "Reload lost chart anchor",
                    )
                }
                println("ORBITALIS_FACE_FRAMES_RELOAD faces=6 identities=true inventories=true excavations=true anchors=true")
                helper.succeed()
            }
        }
    }
}
