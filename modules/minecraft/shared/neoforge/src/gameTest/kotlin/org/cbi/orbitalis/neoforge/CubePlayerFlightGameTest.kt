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
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.GliderFlight
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.lab.CubePlanetLab
import org.cbi.orbitalis.minecraft.lab.CubePlayerFrame
import org.cbi.orbitalis.minecraft.lab.CubePlayerLocomotion
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
    fun cubeJumpMatchesVanillaOnAllFaces(helper: GameTestHelper) {
        val nativePilot = GameTestPlayers.create(helper, locallyControlled = true)
        val pilot = GameTestPlayers.create(helper, locallyControlled = true)
        val reference = GameTestPlayers.controller(nativePilot)
        val lab = CubePlanetLab.forServer(helper.level.server)
        val floor = helper.absolutePos(BlockPos(1, 32, 1))
        for (dx in -8..1) {
            for (dz in -1..1) {
                helper.level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3)
            }
        }
        try {
            for (face in Direction.entries) {
                helper.assertTrue(lab.enter(pilot, face), "Jump entry failed on $face")
                GameTestPlayers.acknowledgeTeleport(pilot)
                val actor = GameTestPlayers.controller(pilot)
                try {
                    actor.abilities.flying = false
                    actor.deltaMovement = Vec3.ZERO
                    repeat(20) { actor.travel(Vec3.ZERO) }
                    helper.assertTrue(actor.onGround(), "Jump fixture did not settle on $face")
                    val start = CubePlayerPhysics.centre(actor)
                    val normal = CubePlayerPhysics.normal(actor)
                    val a = SableFlightGravity.acceleration(actor.level(), Vector3(start.x, start.y, start.z))
                    helper.assertTrue(
                        CubePlayerPhysics.gravity(actor).distanceTo(Vec3(a.x, a.y, a.z)) < 1e-9,
                        "Player and native ship gravity differ on $face",
                    )
                    reference.setPos(Vec3.atLowerCornerOf(floor).add(.5, 1.0, .5))
                    reference.deltaMovement = Vec3.ZERO
                    reference.setOnGround(true)
                    reference.abilities.flying = false
                    val nativeStart = reference.y
                    reference.jumpFromGround()
                    actor.jumpFromGround()
                    var peak = 0.0
                    var landing = 0
                    for (tick in 1..20) {
                        reference.travel(Vec3.ZERO)
                        actor.travel(Vec3.ZERO)
                        val expectedHeight = reference.y - nativeStart
                        val height = CubePlayerPhysics.centre(actor).subtract(start).dot(normal)
                        peak = maxOf(peak, height)
                        helper.assertTrue(
                            abs(height - expectedHeight) < 0.0001,
                            "Jump path differs on $face tick=$tick: $height vs $expectedHeight",
                        )
                        helper.assertTrue(actor.onGround() == reference.onGround(), "Jump landing timing differs on $face tick=$tick")
                        if (actor.onGround() && landing == 0) landing = tick
                    }
                    helper.assertTrue(peak in 1.24..1.27 && landing == 12, "Not a native jump on $face: peak=$peak landing=$landing")
                    for (player in listOf(reference, actor)) {
                        player.getAttribute(Attributes.JUMP_STRENGTH)!!.baseValue = .6
                        player.addEffect(MobEffectInstance(MobEffects.JUMP, 100, 1))
                        player.isSprinting = true
                        player.yRot = 45f
                    }
                    val initial = Vec3(.07, -.1, .03)
                    reference.deltaMovement = initial
                    actor.deltaMovement = CubePlayerPhysics.rotate(initial, actor)
                    reference.jumpFromGround()
                    actor.jumpFromGround()
                    helper.assertTrue(
                        CubePlayerPhysics.local(actor.deltaMovement, actor).distanceTo(reference.deltaMovement) < 1e-6,
                        "Native boosted sprint jump impulse differs on $face",
                    )
                    reference.removeAllEffects()
                    reference.getAttribute(Attributes.JUMP_STRENGTH)!!.baseValue = .42
                    reference.isSprinting = false
                    checkSprintJumps(helper, reference, actor, start, floor, face)
                    println(
                        "ORBITALIS_CUBE_NATIVE_JUMP face=$face peak=$peak landingTick=$landing boostedSprint=true sharedShipGravity=true",
                    )
                } finally {
                    actor.discard()
                }
            }
        } finally {
            lab.leave(pilot)
            pilot.discard()
            nativePilot.discard()
            reference.discard()
        }
        helper.succeed()
    }

    private fun checkSprintJumps(
        helper: GameTestHelper,
        reference: net.minecraft.world.entity.player.Player,
        actor: net.minecraft.world.entity.player.Player,
        start: Vec3,
        floor: BlockPos,
        face: Direction,
    ) {
        fun reset() {
            reference.setPos(Vec3.atLowerCornerOf(floor).add(.5, 1.0, .5))
            actor.setPos(start.subtract(0.0, CubePlayerPhysics.HALF_HEIGHT, 0.0))
            reference.setOnGround(true)
            actor.setOnGround(true)
            // Refresh vanilla's cached support after operator positioning, as an actual movement tick does.
            reference.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, -.001, 0.0))
        }
        for (player in listOf(reference, actor)) {
            player.removeAllEffects()
            player.getAttribute(Attributes.JUMP_STRENGTH)!!.baseValue = .42
            player.isSprinting = true
            // Keep native Z=-10 fixed: the +X face's relief starts at Z=-7.
            player.yRot = 90f
            player.speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat()
            player.deltaMovement = Vec3.ZERO
        }
        reset()
        repeat(2) {
            reference.travel(Vec3.ZERO)
            actor.travel(Vec3.ZERO)
        }
        val input = Vec3(0.0, 0.0, .98f.toDouble())
        repeat(12) {
            val before = CubePlayerPhysics.centre(actor)
            val nativeBefore = reference.position()
            reference.travel(input)
            actor.travel(input)
            val motion = CubePlayerPhysics.local(CubePlayerPhysics.centre(actor).subtract(before), actor)
            val expected = reference.position().subtract(nativeBefore)
            helper.assertTrue(motion.distanceTo(expected) < 0.0001, "Sprint run differs on $face: $motion vs $expected")
        }
        var maximum = 0.0
        repeat(8) { jump ->
            reset()
            reference.jumpFromGround()
            actor.jumpFromGround()
            repeat(12) { tick ->
                val before = CubePlayerPhysics.centre(actor)
                val nativeBefore = reference.position()
                reference.travel(input)
                actor.travel(input)
                val motion = CubePlayerPhysics.local(CubePlayerPhysics.centre(actor).subtract(before), actor)
                val expected = reference.position().subtract(nativeBefore)
                maximum = maxOf(maximum, kotlin.math.hypot(motion.x, motion.z))
                helper.assertTrue(
                    motion.distanceTo(expected) < 0.0001,
                    "Sprint jump differs on $face jump=$jump tick=$tick: $motion vs $expected",
                )
                helper.assertTrue(actor.onGround() == reference.onGround(), "Sprint landing differs on $face jump=$jump tick=$tick")
            }
        }
        // Resolve a slippery support block in the chart's native coordinates, not global Y below the network anchor.
        reset()
        val support = CubePlayerPhysics.supportPosition(actor, .5f)
        val previous = actor.level().getBlockState(support)
        helper.assertTrue(!previous.isAir, "Native support query missed the floor on $face")
        val patch = (-1..1).flatMap { dx -> (-1..1).map { dz -> support.offset(dx, 0, dz) } }
        val originals = patch.associateWith { actor.level().getBlockState(it) }
        patch.forEach { actor.level().setBlock(it, Blocks.ICE.defaultBlockState(), 3) }
        for (dx in -1..1) for (dz in -1..1) helper.level.setBlock(floor.offset(dx, 0, dz), Blocks.ICE.defaultBlockState(), 3)
        try {
            helper.assertTrue(actor.level().getBlockState(support).`is`(Blocks.ICE), "Ice fixture write failed on $face")
            helper.assertTrue(actor.blockPosBelowThatAffectsMyMovement == support, "Native support query was replaced on $face")
            val iceFriction = (actor as CubePlayerLocomotion).orbitalisSupportFriction()
            val nativeIceFriction = (reference as CubePlayerLocomotion).orbitalisSupportFriction()
            helper.assertTrue(iceFriction == nativeIceFriction, "Ice friction differs on $face: $iceFriction vs $nativeIceFriction")
            // Establish native resting state after the operator reset (which clears clipped Y velocity).
            reference.deltaMovement = Vec3.ZERO
            repeat(2) { reference.travel(Vec3.ZERO) }
            // Keep vanilla's resting inward velocity; the cube consumes its equivalent contact probe.
            reference.deltaMovement = Vec3(0.0, reference.deltaMovement.y, .1)
            actor.deltaMovement = CubePlayerPhysics.rotate(Vec3(0.0, 0.0, .1), actor)
            repeat(3) {
                reference.travel(Vec3.ZERO)
                actor.travel(Vec3.ZERO)
                val actual = CubePlayerPhysics.local(actor.deltaMovement, actor)
                helper.assertTrue(
                    abs(actual.z - reference.deltaMovement.z) < 0.0001,
                    "Native ice drag differs on $face: $actual vs ${reference.deltaMovement}; " +
                        "ground=${actor.onGround()}/${reference.onGround()} friction=${actor.orbitalisSupportFriction()}/" +
                        "${reference.orbitalisSupportFriction()} pos=${actor.blockPosBelowThatAffectsMyMovement}/" +
                        "${reference.blockPosBelowThatAffectsMyMovement}",
                )
            }
        } finally {
            originals.forEach { (pos, state) -> actor.level().setBlock(pos, state, 3) }
            for (dx in -1..1) for (dz in -1..1) helper.level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3)
        }
        println("ORBITALIS_CUBE_SPRINT_JUMP face=$face jumps=8 maxTravelPerTick=$maximum nativeMatch=true iceDrag=true")
    }

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
