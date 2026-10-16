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

import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.GliderFlight
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.lab.CubePlanetLab
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerMode
import org.cbi.orbitalis.minecraft.lab.CubePlayerModeData
import org.cbi.orbitalis.minecraft.lab.CubePlayerOrientation
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics
import org.cbi.orbitalis.minecraft.lab.CubePlayerVisualFrame
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@PrefixGameTestTemplate(false)
object CubePlayerFlightGameTest {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeModeSurvivesAltitudeAndReload(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val restored = GameTestPlayers.create(helper)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        try {
            for (radius in listOf(63.9, 64.1, 100.0, 500.0)) {
                player.setPos(CubePlayerPhysics.CENTRE.add(radius, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
                helper.assertTrue(CubePlayerPhysics.active(player), "Planet mode silently stopped at radius $radius")
            }
            player.abilities.flying = false
            for (up in Direction.entries) {
                player.setPos(CubePlayerPhysics.CENTRE.add(Vec3.atLowerCornerOf(up.normal).scale(120.0)).add(0.0, -0.9, 0.0))
                CubePlayerPhysics.refresh(player)
                player.deltaMovement = Vec3.ZERO
                player.setOnGround(false)
                player.aiStep()
                val first = player.deltaMovement
                player.aiStep()
                helper.assertTrue(
                    player.deltaMovement.dot(CubePlayerPhysics.gravity(player)) > 0.0,
                    "Free fall accelerated away from the planet on $up",
                )
                helper.assertTrue(
                    player.deltaMovement.length() > first.length() * 1.8,
                    "Native tick erased weak gravity velocity instead of accumulating it on $up",
                )
            }
            val saved = player.saveWithoutId(CompoundTag())
            helper.assertTrue(CubePlayerModeData.read(saved), "Player save omitted scene membership")
            restored.readAdditionalSaveData(saved)
            helper.assertTrue((restored as CubePlayerMode).orbitalisCubeEnrolled(), "Reload lost scene membership")
            saved.getCompound(CubePlayerModeData.KEY).putInt("version", 2)
            restored.readAdditionalSaveData(saved)
            helper.assertTrue(!restored.orbitalisCubeEnrolled(), "Unknown mode version was enabled")
            helper.assertTrue(!CubePlayerModeData.read(CompoundTag()), "Missing mode data was enabled")
            saved.getCompound(CubePlayerModeData.KEY).putInt("version", 1)
            saved.getCompound(CubePlayerModeData.KEY).putByte("enabled", 2)
            helper.assertTrue(!CubePlayerModeData.read(saved), "Malformed boolean was enabled")
            val health = player.health
            player.setPos(CubePlayerPhysics.CENTRE.add(0.0, -400.0, 0.0))
            player.baseTick()
            helper.assertTrue(player.health == health && player.isAlive, "Global-Y void killed a planet-scene player")
            helper.assertTrue(lab.leave(player), "Explicit scene leave failed")
            helper.assertTrue(
                !CubePlayerPhysics.active(player) && !(player as CubePlayerMode).orbitalisCubeEnrolled(),
                "Scene membership leaked after leave",
            )
            GameTestPlayers.acknowledgeTeleport(player)
            player.setPos(0.0, -400.0, 0.0)
            val beforeVoid = player.health
            player.baseTick()
            helper.assertTrue(player.health < beforeVoid, "Planet void exemption leaked into the Overworld")
        } finally {
            lab.leave(player)
            player.discard()
            restored.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeContinuousFrameFollowsGravity(helper: GameTestHelper) {
        val interpolated = CubePlayerVisualFrame(Quaternionf())
        val firstTarget = Quaternionf().rotationZ(0.0001f)
        interpolated.tick(firstTarget, 0.0)
        for (sample in 0..10) {
            val partial = sample / 10f
            val actual = interpolated.sample(partial).transform(Vector3f(0f, 1f, 0f))
            val expected = Quaternionf().rotationZ(0.0001f * partial).transform(Vector3f(0f, 1f, 0f))
            helper.assertTrue(actual.distance(expected) < 1e-7, "Render frame held or jumped between tick endpoints at $partial")
            helper.assertTrue(
                actual.distance(interpolated.sample(partial).transform(Vector3f(0f, 1f, 0f))) < 1e-8,
                "Camera/model sampling advanced the shared visual frame",
            )
        }
        interpolated.tick(Quaternionf().rotationZ(0.0002f), 0.0)
        helper.assertTrue(
            interpolated.sample(0f).transform(Vector3f(0f, 1f, 0f)).distance(firstTarget.transform(Vector3f(0f, 1f, 0f))) < 1e-7,
            "Consecutive flight ticks had a render discontinuity",
        )
        val from = CubePlayerPhysics.rotation(Direction.EAST)
        val to = CubePlayerPhysics.rotation(Direction.SOUTH)
        val rebase = CubePlayerOrientation.yawShift(from, to)
        val chartTurn = CubePlayerVisualFrame(from)
        chartTurn.tick(to, rebase)
        val rebasedForward = Quaternionf().rotationY(-rebase.toFloat()).transform(Vector3f(0f, 0f, 1f))
        helper.assertTrue(
            chartTurn.sample(0f).transform(rebasedForward).distance(from.transform(Vector3f(0f, 0f, 1f))) < 1e-6,
            "Interpolation lost heading continuity when rebasing the chart",
        )
        val tinyTurn = Quaternionf().rotationZ(0.0001f)
        val eased = CubePlayerOrientation.ease(Quaternionf(), tinyTurn, 0.0, 0.05)
        helper.assertTrue(
            eased.transform(Vector3f(0f, 1f, 0f)).distance(tinyTurn.transform(Vector3f(0f, 1f, 0f))) < 1e-6,
            "Sub-degree flight orientation was discarded by float dot-product rounding",
        )
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        try {
            player.setPos(CubePlayerPhysics.CENTRE.add(80.0, 30.0 - CubePlayerPhysics.HALF_HEIGHT, 20.0))
            (player as CubePlayerFrame).orbitalisSetUp(null)
            player.yRot = 37f
            CubePlayerPhysics.refresh(player)
            helper.assertTrue(player.yRot == 37f, "Fresh or reloaded flight frame rebased already canonical saved yaw")
            for (step in 0..200) {
                val p = Vec3(19.0 + step / 4.0, 10.0, 3.0)
                player.setPos(CubePlayerPhysics.CENTRE.add(p).add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0))
                val centre = CubePlayerPhysics.centre(player)
                val velocity = Vec3(0.12, -0.03, 0.07)
                player.deltaMovement = velocity
                CubePlayerPhysics.refresh(player)
                helper.assertTrue(
                    CubePlayerPhysics.normal(player).dot(CubePlayerPhysics.gravity(player).normalize()) < -0.999999,
                    "Up diverged from force through the altitude blend: $step",
                )
                helper.assertTrue(
                    player.deltaMovement == velocity && CubePlayerPhysics.centre(player) == centre,
                    "Altitude frame changed inertial motion",
                )
            }
            for (step in 0..360) {
                val angle = step * Math.PI / 180
                player.setPos(CubePlayerPhysics.CENTRE.add(90 * cos(angle), 90 * sin(angle) - CubePlayerPhysics.HALF_HEIGHT, 10.0))
                val oldUp = CubePlayerPhysics.normal(player)
                val before = CubePlayerPhysics.view(player, 1f)
                val nextUp = CubePlayerPhysics.gravity(player).normalize().scale(-1.0)
                val turn =
                    Quaternionf().rotationTo(
                        Vector3f(oldUp.x.toFloat(), oldUp.y.toFloat(), oldUp.z.toFloat()),
                        Vector3f(nextUp.x.toFloat(), nextUp.y.toFloat(), nextUp.z.toFloat()),
                    )
                val expected = turn.transform(Vector3f(before.x.toFloat(), before.y.toFloat(), before.z.toFloat()))
                CubePlayerPhysics.refresh(player)
                val after = CubePlayerPhysics.view(player, 1f)
                helper.assertTrue(
                    after.distanceTo(Vec3(expected.x.toDouble(), expected.y.toDouble(), expected.z.toDouble())) < 1e-5,
                    "Continuous heading transport twisted at $step",
                )
                helper.assertTrue(
                    CubePlayerPhysics.normal(player).distanceTo(nextUp) < 1e-6,
                    "Far frame diverged at $step: ${CubePlayerPhysics.normal(player)} vs $nextUp",
                )
                helper.assertTrue(player.level().noCollision(player, player.boundingBox), "Conservative body envelope intersected terrain")
            }
            // Resize out of a glide only when the standing body can actually fit.
            player.setPos(CubePlayerPhysics.CENTRE.add(19.5, 18.5 - CubePlayerPhysics.HALF_HEIGHT, 0.5))
            player.startFallFlying()
            CubePlayerPhysics.refresh(player)
            helper.assertTrue(CubePlayerPhysics.halfHeight(player) == 0.3, "Glide did not shrink the body")
            val obstruction =
                net.minecraft.core.BlockPos
                    .containing(CubePlayerPhysics.CENTRE)
                    .offset(20, 18, 0)
            player.level().setBlock(
                obstruction,
                net.minecraft.world.level.block.Blocks.GLASS
                    .defaultBlockState(),
                2,
            )
            try {
                player.stopFallFlying()
                CubePlayerPhysics.refresh(player)
                helper.assertTrue(
                    CubePlayerPhysics.halfHeight(player) == 0.3 && player.level().noCollision(player, player.boundingBox.deflate(1e-5)),
                    "Body expansion intersected an overhang",
                )
            } finally {
                player.level().removeBlock(obstruction, false)
            }
            CubePlayerPhysics.refresh(player)
            helper.assertTrue(CubePlayerPhysics.halfHeight(player) == 0.9, "Clear body expansion remained blocked")
            val yaw = player.yRot
            repeat(1000) { CubePlayerPhysics.refresh(player) }
            helper.assertTrue(abs(player.yRot - yaw) < 1e-5, "Stationary frame accumulated yaw drift")
        } finally {
            lab.leave(player)
            player.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun cubeElytraUsesLocalGravity(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper, locallyControlled = true)
        val lab = CubePlanetLab.forServer(helper.level.server)
        helper.assertTrue(lab.enter(player), "Cube entry failed")
        GameTestPlayers.acknowledgeTeleport(player)
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
        player.abilities.flying = false
        try {
            for (up in Direction.entries) {
                player.stopFallFlying()
                (player as CubePlayerFrame).orbitalisSetUp(up)
                player.setPos(
                    CubePlayerPhysics.CENTRE.add(Vec3.atLowerCornerOf(up.normal).scale(60.0)).add(0.0, -CubePlayerPhysics.HALF_HEIGHT, 0.0),
                )
                CubePlayerPhysics.refresh(player)
                player.yRot = 0f
                player.xRot = 0f
                player.setOnGround(false)
                helper.assertTrue(player.tryToStartFallFlying(), "Vanilla elytra activation failed on $up")
                helper.assertTrue(
                    player.lookAngle.distanceTo(CubePlayerPhysics.view(player, 1f)) < 1e-8,
                    "Native look API retained global axes on $up",
                )
                player.deltaMovement = Vec3.ZERO
                val rocket = FireworkRocketEntity(player.level(), ItemStack(Items.FIREWORK_ROCKET), player)
                try {
                    rocket.tick()
                    helper.assertTrue(
                        player.deltaMovement.normalize().distanceTo(player.lookAngle.normalize()) < 1e-6 &&
                            player.deltaMovement.length() > 0.1,
                        "Native rocket thrust diverged from gravity-aligned look on $up",
                    )
                } finally {
                    rocket.discard()
                }
                val initial = Vec3(0.0, -0.1, 0.6)
                player.deltaMovement = CubePlayerPhysics.rotate(initial, player)
                val force = CubePlayerPhysics.local(CubePlayerPhysics.gravity(player).scale(1.0 / 400.0), player)
                val expected = GliderFlight.step(Vector3(initial.x, initial.y, initial.z), 0.0, 0.0, Vector3(force.x, force.y, force.z))
                val before = CubePlayerPhysics.centre(player)
                val world = CubePlayerPhysics.rotate(Vec3(expected.x, expected.y, expected.z), player)
                player.travel(Vec3.ZERO)
                helper.assertTrue(player.isFallFlying, "Custom movement cancelled gliding on $up")
                helper.assertTrue(
                    CubePlayerPhysics.centre(player).subtract(before).distanceTo(world) < 1e-6,
                    "Glider displacement used a global-Y rule on $up",
                )
                helper.assertTrue(CubePlayerPhysics.halfHeight(player) == 0.3, "Glider retained the standing collider")
                helper.assertTrue(CubePlayerPhysics.eyeOffset(player) < 0.2, "Glider eye used standing height")
                repeat(180) {
                    player.travel(Vec3.ZERO)
                    helper.assertTrue(CubePlayerPhysics.active(player) && player.isAlive, "Glider lost planet mode on $up")
                    helper.assertTrue(player.level().noCollision(player, player.boundingBox.deflate(1e-5)), "Glider entered terrain on $up")
                }
            }
        } finally {
            player.stopFallFlying()
            lab.leave(player)
            player.discard()
        }
        helper.succeed()
    }
}
