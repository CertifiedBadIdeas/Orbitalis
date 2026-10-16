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
import org.cbi.orbitalis.minecraft.lab.CubePlanetLab
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object CubePlanetGameTest {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeFixturePreservesExcavation(helper: GameTestHelper) {
        val lab = CubePlanetLab.forServer(helper.level.server)
        lab.rebuild()
        val level = helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)!!
        val origin = BlockPos.containing(CubePlayerPhysics.CENTRE)
        helper.assertTrue(!level.getBlockState(origin.offset(0, 18, 0)).isAir, "Raised +Y face missing")
        helper.assertTrue(level.getBlockState(origin.offset(18, 0, 0)).isAir, "Unequal face relief lost")
        helper.assertTrue(level.getBlockState(origin.offset(16, 16, 0)).isAir, "Convex edge exterior is blocked")
        helper.assertTrue(!level.getBlockState(origin.offset(15, 15, 0)).isAir, "Convex edge was carved into a trench")
        val tunnel = origin.offset(-8, -8, -8)
        val previous = level.getBlockState(tunnel)
        level.removeBlock(tunnel, false)
        try {
            CubePlanetLab(helper.level.server).prepare()
            helper.assertTrue(level.getBlockState(tunnel).isAir, "Re-entry refilled an excavation")
        } finally {
            level.setBlock(tunnel, previous, 2)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 220)
    fun cubePlayerCrossesEdge(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
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
            helper.assertTrue(lab.leave(player), "Cube leave failed")
            helper.assertTrue(!CubePlayerPhysics.active(player), "Cube locomotion leaked into ordinary world")
        } finally {
            player.discard()
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
    fun cubeFramePreservesMotionAndBlocksUnsafeTurns(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        val level = player.serverLevel()
        try {
            player.setPos(CubePlayerPhysics.CENTRE.add(23.5, 20.0 - CubePlayerPhysics.HALF_HEIGHT, 0.5))
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
            val outside = helper.level.sharedSpawnPos
            helper.assertTrue(!helper.level.getBlockState(outside).`is`(Blocks.ORANGE_CONCRETE), "Cube fixture touched the ordinary world")
        } finally {
            lab.leave(player)
            player.discard()
        }
        helper.succeed()
    }
}
