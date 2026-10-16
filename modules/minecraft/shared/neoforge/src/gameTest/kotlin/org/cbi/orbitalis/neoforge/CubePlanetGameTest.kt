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

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.integration.sable.SableCubePlanet
import org.cbi.orbitalis.minecraft.lab.CubePlanetLab
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.CubePlayerVisualFrame
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object CubePlanetGameTest {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "cube_fixture")
    fun cubeFixturePreservesExcavation(helper: GameTestHelper) {
        val lab = CubePlanetLab.forServer(helper.level.server)
        lab.rebuild()
        val level = helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)!!
        val planet = SableCubePlanet(level)
        val occupied = mutableSetOf<BlockPos>()
        for (body in planet.bodies) {
            for (x in -20..19) {
                for (y in -20..3) {
                    for (z in -20..19) {
                        if (body.plot.embeddedLevelAccessor
                                .getBlockState(BlockPos(x, y, z))
                                .isAir
                        ) {
                            continue
                        }
                        val world = planet.world(checkNotNull(SableCubePlanet.direction(body)), x + .5, y + .5, z + .5)
                        val relative = BlockPos.containing(world.subtract(CubePlayerPhysics.CENTRE))
                        helper.assertTrue(occupied.add(relative), "Chart cells overlap at $relative")
                        helper.assertTrue(
                            org.cbi.orbitalis.core.planet.CubicPlanetFixture
                                .solid(relative.x, relative.y, relative.z),
                            "Unexpected terrain at $relative",
                        )
                    }
                }
            }
        }
        val expected =
            (-20..19).sumOf { x ->
                (-20..19).sumOf { y ->
                    (-20..19).count { z ->
                        org.cbi.orbitalis.core.planet.CubicPlanetFixture
                            .solid(x, y, z)
                    }
                }
            }
        helper.assertTrue(occupied.size == expected, "Missing interior cells: ${occupied.size}/$expected")
        val body = planet.body(Direction.UP)
        val tunnel = BlockPos(-8, -3, -10)
        val previous = body.plot.embeddedLevelAccessor.getBlockState(tunnel)
        helper.assertTrue(!previous.isAir, "Excavation fixture is empty")
        body.plot.embeddedLevelAccessor.setBlock(tunnel, Blocks.AIR.defaultBlockState(), 3)
        try {
            CubePlanetLab(helper.level.server).prepare()
            helper.assertTrue(
                body.plot.embeddedLevelAccessor
                    .getBlockState(tunnel)
                    .isAir,
                "Re-entry refilled excavation",
            )
        } finally {
            body.plot.embeddedLevelAccessor.setBlock(tunnel, previous, 3)
        }
        // A nearby block shares the integer query envelope but does not intersect the player.
        val neighbour = BlockPos(11, 0, -11)
        for (face in Direction.entries) {
            planet
                .body(
                    face,
                ).plot.embeddedLevelAccessor
                .setBlock(neighbour, Blocks.GLASS.defaultBlockState(), 3)
        }
        val entryPilot = GameTestPlayers.create(helper)
        try {
            for (face in Direction.entries) {
                helper.assertTrue(lab.enter(entryPilot, face), "Clear chart entry rejected on $face")
                GameTestPlayers.acknowledgeTeleport(entryPilot)
                helper.assertTrue(lab.leave(entryPilot), "Could not leave $face")
                GameTestPlayers.acknowledgeTeleport(entryPilot)
            }
        } finally {
            entryPilot.discard()
            for (face in Direction.entries) {
                planet
                    .body(
                        face,
                    ).plot.embeddedLevelAccessor
                    .setBlock(neighbour, Blocks.AIR.defaultBlockState(), 3)
            }
        }
        val obstruction = BlockPos(10, 0, -10)
        val original = body.plot.embeddedLevelAccessor.getBlockState(obstruction)
        val pilot = GameTestPlayers.create(helper)
        val before = pilot.position()
        try {
            body.plot.embeddedLevelAccessor.setBlock(obstruction, Blocks.GLASS.defaultBlockState(), 3)
            helper.assertTrue(!lab.enter(pilot), "Entry accepted a chart obstruction")
            helper.assertTrue(pilot.position() == before && !CubePlayerPhysics.active(pilot), "Rejected entry changed the pilot")
        } finally {
            body.plot.embeddedLevelAccessor.setBlock(obstruction, original, 3)
            pilot.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 220)
    fun cubePlayerCrossesEdge(helper: GameTestHelper) {
        val pilot = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(pilot), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(pilot)
        val player = GameTestPlayers.controller(pilot)
        var crossed = false
        var grounded = false
        try {
            repeat(160) {
                player.travel(if (crossed) Vec3.ZERO else Vec3(0.0, 0.0, 1.0))
                if (CubePlayerPhysics.up(player) == Direction.EAST) crossed = true
                if (crossed && player.onGround()) grounded = true
                helper.assertTrue(
                    player.level().noCollision(player, player.boundingBox.deflate(1e-5)),
                    "Collider entered terrain at ${player.position()}",
                )
            }
            helper.assertTrue(crossed, "Walking off the edge did not change the physical up axis")
            helper.assertTrue(grounded, "Player did not land on the adjacent face")
            helper.assertTrue(CubePlayerPhysics.up(player) == Direction.EAST, "Unstable face selection after landing")
            helper.assertTrue(lab.leave(pilot), "Cube leave failed")
            helper.assertTrue(!CubePlayerPhysics.active(pilot), "Cube locomotion leaked into ordinary world")
        } finally {
            player.discard()
            pilot.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeSlowEdgeLandsWithoutSliding(helper: GameTestHelper) {
        val pilot = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(pilot), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(pilot)
        val player = GameTestPlayers.controller(pilot)
        try {
            for (from in Direction.entries) {
                for (to in Direction.entries.filter { it.axis != from.axis }) {
                    for (speed in listOf(0.0, 0.005, 0.03)) {
                        val start =
                            Vec3
                                .atLowerCornerOf(from.normal)
                                .scale(16.9)
                                .add(Vec3.atLowerCornerOf(to.normal).scale(16.32))
                                .add(
                                    if (from.axis != Direction.Axis.X && to.axis != Direction.Axis.X) 10.5 else 0.0,
                                    if (from.axis != Direction.Axis.Y && to.axis != Direction.Axis.Y) 10.5 else 0.0,
                                    if (from.axis != Direction.Axis.Z && to.axis != Direction.Axis.Z) 10.5 else 0.0,
                                )
                        val label = "$from->$to at $speed blocks/tick"
                        (player as dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                            .`sable$setTrackingSubLevel`(
                                null,
                            )
                        (player as dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension)
                            .`sable$getInheritedVelocity`()
                            .zero()
                        (player as CubePlayerFrame).orbitalisSetOrientation(null)
                        player.orbitalisSetUp(from)
                        player.setPos(CubePlayerPhysics.CENTRE.add(start).subtract(0.0, CubePlayerPhysics.HALF_HEIGHT, 0.0))
                        player.setOnGround(false)
                        player.deltaMovement = Vec3.atLowerCornerOf(to.normal).scale(speed)
                        repeat(120) {
                            player.travel(Vec3.ZERO)
                            helper.assertTrue(
                                player.level().noCollision(player, player.boundingBox.deflate(1e-5)),
                                "$label entered terrain at ${CubePlayerPhysics.centre(player)}",
                            )
                        }
                        helper.assertTrue(
                            CubePlayerPhysics.up(player) == to && player.onGround(),
                            "$label failed to stand: up=${CubePlayerPhysics.up(
                                player,
                            )}, centre=${CubePlayerPhysics.centre(player)}, velocity=${player.deltaMovement}",
                        )
                        val landed = CubePlayerPhysics.centre(player)
                        repeat(40) { player.travel(Vec3.ZERO) }
                        helper.assertTrue(CubePlayerPhysics.centre(player).distanceTo(landed) < 0.01, "$label kept sliding after landing")
                    }
                }
            }
        } finally {
            lab.leave(pilot)
            player.discard()
            pilot.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeCreativeFlightEscapesAlongLocalUp(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        try {
            player.setPos(CubePlayerPhysics.CENTRE.add(18.9, 5.0 - CubePlayerPhysics.HALF_HEIGHT, 0.5))
            CubePlayerPhysics.refresh(player)
            player.abilities.mayfly = true
            player.abilities.flying = true
            player.deltaMovement = Vec3.ZERO
            val before = CubePlayerPhysics.centre(player)
            repeat(20) {
                val lift = CubePlayerPhysics.rotate(Vec3(0.0, 0.15, 0.0), CubePlayerPhysics.up(player))
                player.deltaMovement = player.deltaMovement.add(lift)
                player.travel(Vec3.ZERO)
            }
            val after = CubePlayerPhysics.centre(player)
            helper.assertTrue(player.abilities.flying, "Cube travel disabled creative flight")
            helper.assertTrue(after.x > before.x + 3.0, "Creative lift did not escape along the side face normal")
            helper.assertTrue(abs(after.y - before.y) < 1e-5, "Vanilla global-Y flight or gravity leaked into local lift")
        } finally {
            lab.leave(player)
            player.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeHeadingTurnsAroundSharedEdge(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val remote = GameTestPlayers.create(helper)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player) && lab.enter(remote), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        GameTestPlayers.acknowledgeTeleport(remote)
        try {
            for (from in Direction.entries) {
                for (to in Direction.entries.filter { it.axis != from.axis }) {
                    val oldUp = Vector3f(from.stepX.toFloat(), from.stepY.toFloat(), from.stepZ.toFloat())
                    val newUp = Vector3f(to.stepX.toFloat(), to.stepY.toFloat(), to.stepZ.toFloat())
                    val edge = Vector3f(oldUp).cross(newUp)
                    val turn = Quaternionf().rotationAxis((Math.PI / 2).toFloat(), edge)
                    val position =
                        CubePlayerPhysics.CENTRE
                            .add(Vec3.atLowerCornerOf(from.normal).scale(18.0))
                            .add(Vec3.atLowerCornerOf(to.normal).scale(19.0))
                            .add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0)
                    for (yaw in listOf(-135f, -90f, 0f, 45f, 90f, 179f)) {
                        for (pitch in listOf(-60f, 0f, 60f)) {
                            player.setPos(position)
                            (player as CubePlayerFrame).orbitalisSetUp(from)
                            player.yRot = yaw
                            player.yRotO = yaw
                            player.xRot = pitch
                            player.xRotO = pitch
                            val before = CubePlayerPhysics.view(player, 1f)
                            val expected = turn.transform(Vector3f(before.x.toFloat(), before.y.toFloat(), before.z.toFloat()))
                            val rebase = player.orbitalisGetYawRebase()
                            CubePlayerPhysics.refresh(player)
                            val after = CubePlayerPhysics.view(player, 1f)
                            val label = "$from->$to yaw=$yaw pitch=$pitch"
                            helper.assertTrue(
                                after.distanceTo(Vec3(expected.x.toDouble(), expected.y.toDouble(), expected.z.toDouble())) < 1e-5,
                                "Heading twisted away from the shared edge: $label",
                            )
                            helper.assertTrue(
                                player.xRot == pitch && player.yRotO == player.yRot,
                                "Chart rebase changed pitch or added yaw interpolation",
                            )
                            // Exercise the tick-owned frame and every rendered substep used by camera/model.
                            val visual = CubePlayerVisualFrame(CubePlayerPhysics.rotation(from))
                            val oldLook = CubePlayerPhysics.rotation(from).rotateY(Math.toRadians(-yaw.toDouble()).toFloat())
                            for (step in 0 until 10) {
                                visual.tick(CubePlayerPhysics.rotation(to), if (step == 0) player.orbitalisGetYawRebase() - rebase else 0.0)
                                for (substep in 0..4) {
                                    val partial = substep / 4f
                                    val expectedLook =
                                        Quaternionf().rotationAxis((Math.PI * (step + partial) / 20).toFloat(), edge).mul(oldLook)
                                    val actualLook = visual.sample(partial).rotateY(Math.toRadians(-player.yRot.toDouble()).toFloat())
                                    for (basis in listOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))) {
                                        helper.assertTrue(
                                            actualLook.transform(Vector3f(basis)).distance(expectedLook.transform(Vector3f(basis))) < 2e-4,
                                            "Visual turn used an axis other than the shared edge: $label step=$step partial=$partial",
                                        )
                                    }
                                }
                            }
                            player.abilities.flying = true
                            player.deltaMovement = Vec3.ZERO
                            player.travel(Vec3(0.0, 0.0, 1.0))
                            val flatLook =
                                Quaternionf(turn).transform(
                                    CubePlayerPhysics.rotation(from).transform(
                                        Vector3f(
                                            -kotlin.math.sin(Math.toRadians(yaw.toDouble())).toFloat(),
                                            0f,
                                            kotlin.math.cos(Math.toRadians(yaw.toDouble())).toFloat(),
                                        ),
                                    ),
                                )
                            val motion = player.deltaMovement.normalize()
                            helper.assertTrue(
                                motion.distanceTo(Vec3(flatLook.x.toDouble(), flatLook.y.toDouble(), flatLook.z.toDouble())) < 1e-5,
                                "Forward control diverged from transported heading: $label",
                            )
                        }
                    }
                    // Incoming vanilla yaw is already in the destination chart. The server must not rebase it again.
                    remote.setPos(position)
                    (remote as CubePlayerFrame).orbitalisSetUp(from)
                    remote.yRot = 37f
                    CubePlayerPhysics.refresh(remote)
                    helper.assertTrue(
                        remote.yRot == 37f && remote.orbitalisGetYawRebase() == 0.0,
                        "Server rebased incoming client yaw twice",
                    )
                }
            }
        } finally {
            lab.leave(player)
            lab.leave(remote)
            player.discard()
            remote.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeFramePreservesMotionAndBlocksUnsafeTurns(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        val level = player.serverLevel()
        try {
            player.setPos(CubePlayerPhysics.CENTRE.add(19.5, 18.0 - CubePlayerPhysics.HALF_HEIGHT, 0.5))
            (player as CubePlayerFrame).orbitalisSetUp(Direction.UP)
            val centre = CubePlayerPhysics.centre(player)
            val velocity = Vec3(0.1, -0.2, 0.03)
            player.deltaMovement = velocity
            CubePlayerPhysics.refresh(player)
            helper.assertTrue(CubePlayerPhysics.up(player) == Direction.EAST, "Clear collider did not turn")
            helper.assertTrue(
                CubePlayerPhysics.centre(player) == centre && player.deltaMovement == velocity,
                "Frame turn changed centre or velocity",
            )
            helper.assertTrue(
                abs(player.boundingBox.xsize - 1.8) < 1e-6 && abs(player.boundingBox.ysize - 0.6) < 1e-6,
                "Standing collider did not rotate",
            )
            CubePlayerPhysics.jump(player)
            helper.assertTrue(player.deltaMovement.x > 0 && abs(player.deltaMovement.y - velocity.y) < 1e-9, "Jump did not use local up")
            (player as CubePlayerFrame).orbitalisSetUp(Direction.UP)
            val obstruction = BlockPos.containing(centre.add(0.8, 0.0, 0.0))
            level.setBlock(obstruction, Blocks.GLASS.defaultBlockState(), 2)
            try {
                CubePlayerPhysics.refresh(player)
                helper.assertTrue(CubePlayerPhysics.up(player) == Direction.UP, "Turn ignored an obstructed destination box")
            } finally {
                level.removeBlock(obstruction, false)
            }
            // New box can clear the support, but the old body's head would hit this overhang.
            val base = BlockPos.containing(CubePlayerPhysics.CENTRE)
            val support = base.offset(18, 18, 0)
            val overhang = base.offset(20, 19, 0)
            (player as CubePlayerFrame).orbitalisSetUp(Direction.UP)
            player.setPos(CubePlayerPhysics.CENTRE.add(19.5, 18.5 - CubePlayerPhysics.HALF_HEIGHT, 0.5))
            level.setBlock(support, Blocks.GLASS.defaultBlockState(), 2)
            level.setBlock(overhang, Blocks.GLASS.defaultBlockState(), 2)
            try {
                val before = CubePlayerPhysics.centre(player)
                CubePlayerPhysics.refresh(player)
                helper.assertTrue(CubePlayerPhysics.up(player) == Direction.UP, "Turn moved the old collider through an overhang")
                helper.assertTrue(CubePlayerPhysics.centre(player) == before, "Blocked turn displaced the player")
                level.removeBlock(overhang, false)
                CubePlayerPhysics.refresh(player)
                helper.assertTrue(CubePlayerPhysics.up(player) == Direction.EAST, "Clear support contact did not turn")
                val shift = CubePlayerPhysics.centre(player).subtract(before)
                helper.assertTrue(
                    abs(shift.x - 0.4) < 1e-5 && shift.y == 0.0 && shift.z == 0.0,
                    "Turn did not use minimum outward clearance: $shift",
                )
                helper.assertTrue(level.noCollision(player, player.boundingBox.deflate(1e-5)), "Clearance left the body in terrain")
            } finally {
                level.removeBlock(support, false)
                level.removeBlock(overhang, false)
            }
            val outside = helper.level.sharedSpawnPos
            helper.assertTrue(!helper.level.getBlockState(outside).`is`(Blocks.ORANGE_CONCRETE), "Cube fixture touched the ordinary world")
        } finally {
            lab.leave(player)
            player.discard()
        }
        helper.succeed()
    }
}
