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

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GlobalTestReporter
import net.minecraft.gametest.framework.JUnitLikeTestReporter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.core.planet.CubeFace
import org.cbi.orbitalis.minecraft.lab.PlanetLab
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.lab.PlanetLabServices
import java.io.File

@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object PlanetLabGameTests {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        System.getProperty("orbitalis.gameTestReport")?.let { path ->
            GlobalTestReporter.replaceWith(JUnitLikeTestReporter(File(path)))
        }
        val suite = System.getProperty("orbitalis.gameTestSuite", "all")
        require(suite == "all" || suite == "cube") { "Unknown GameTest suite: $suite" }
        event.register(CubePlanetGameTest::class.java)
        if (suite == "cube") return
        event.register(PlanetLabGameTests::class.java)
        if (ModList.get().isLoaded("sable")) {
            event.register(SableLabGameTests::class.java)
            event.register(SableFlightGameTests::class.java)
            event.register(SableTransferGameTests::class.java)
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun dimensionLoadsAsVoid(helper: GameTestHelper) {
        val level = helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)
        helper.assertTrue(level != null, "Planet lab dimension was not loaded from resources")
        helper.assertTrue(level!!.getBlockState(BlockPos(4096, 64, 0)).isAir, "Lab generated terrain outside finite pads")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun padsAreBoundedAndPreserveEdits(helper: GameTestHelper) {
        val lab = PlanetLab(helper.level.server)
        val level = lab.level()
        val face = CubeFace.NEGATIVE_X
        val centre = PlanetLabCoordinates.chartCentre(face)
        lab.prepareChart(level, face)
        val edge = centre.offset(PlanetLabCoordinates.HALF_WIDTH, 0, PlanetLabCoordinates.HALF_WIDTH)
        helper.assertTrue(!level.getBlockState(edge).isAir, "Finite pad is missing its corner")
        helper.assertTrue(level.getBlockState(edge.east()).isAir, "Pad wrote beyond its declared boundary")
        level.setBlock(centre, Blocks.GOLD_BLOCK.defaultBlockState(), 2)
        helper.assertTrue(lab.prepareChart(level, face) == 0, "Second preparation changed an already populated pad")
        helper.assertTrue(level.getBlockState(centre).`is`(Blocks.GOLD_BLOCK), "Pad preparation overwrote an existing block")
        try {
            lab.prepareChart(helper.level, face)
            helper.fail("Pad preparation accepted the ordinary test dimension")
        } catch (_: IllegalArgumentException) {
            // Required guard: no ordinary-world mutation.
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun commandsEnterSampleAndReturn(helper: GameTestHelper) {
        val player = GameTestPlayers.create(helper)
        val originalLevel = player.level().dimension()
        val originalPosition = player.position()
        val originalMode = player.gameMode.gameModeForPlayer
        val dispatcher = helper.level.server.commands.dispatcher
        try {
            helper.assertTrue(
                dispatcher.execute("orbitalis lab enter positive_z", player.createCommandSourceStack().withPermission(2)) == 1,
                "Enter command failed",
            )
            helper.assertTrue(player.level().dimension() == PlanetLabCoordinates.DIMENSION, "Player did not enter the laboratory")
            val surface = PlanetLabCoordinates.locate(player.position())
            helper.assertTrue(surface != null && surface.point.face == CubeFace.POSITIVE_Z, "Player was not placed on the requested chart")
            helper.assertTrue(
                surface!!.point.u == 0.0 && surface.point.v == 0.0 && surface.point.altitudeMetres == 0.0,
                "Chart anchor is offset from the player spawn",
            )
            helper.assertTrue(
                dispatcher.execute("orbitalis lab sample", player.createCommandSourceStack().withPermission(2)) == 1,
                "Sample command failed in the laboratory",
            )
            helper.assertTrue(
                dispatcher.execute("orbitalis lab leave", player.createCommandSourceStack().withPermission(2)) == 1,
                "Leave command failed",
            )
            helper.assertTrue(
                player.level().dimension() == originalLevel && player.position().distanceTo(originalPosition) < 1e-8,
                "Return point was not preserved",
            )
            helper.assertTrue(player.gameMode.gameModeForPlayer == originalMode, "Laboratory changed the player's game mode")
        } finally {
            player.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun blockedEntryDoesNotMovePlayer(helper: GameTestHelper) {
        val lab = PlanetLab(helper.level.server)
        val level = lab.level()
        val face = CubeFace.NEGATIVE_Z
        val obstruction = PlanetLabCoordinates.chartCentre(face).above()
        val player = GameTestPlayers.create(helper)
        val originalLevel = player.level().dimension()
        val originalPosition = player.position()
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 2)
        try {
            helper.assertTrue(!lab.enter(player, face), "Entry ignored an obstructed destination")
            helper.assertTrue(
                player.level().dimension() == originalLevel && player.position() == originalPosition,
                "Rejected entry moved the player",
            )
        } finally {
            level.removeBlock(obstruction, false)
            player.discard()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun labRequiresOperatorPermission(helper: GameTestHelper) {
        val server = helper.level.server
        try {
            server.commands.dispatcher.execute("orbitalis lab stop", server.createCommandSourceStack().withPermission(0))
            helper.fail("Unprivileged source was able to execute a lab command")
        } catch (_: CommandSyntaxException) {
            // The command tree must reject the source before invoking a handler.
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun chartAdapterRespectsBoundsAndPhysicalUnits(helper: GameTestHelper) {
        val centre = PlanetLabCoordinates.chartCentre(CubeFace.POSITIVE_X)
        val position = Vec3(centre.x + 0.5, centre.y + 1.0, centre.z + 0.5)
        val sample = PlanetLabCoordinates.locate(position, Vec3(0.1, 0.0, 0.0))!!
        val state = PlanetLabCoordinates.planet.toCartesian(sample.point, sample.velocity)
        helper.assertTrue(
            kotlin.math.abs(state.velocity.length() - 2.0) < 1e-8,
            "Per-tick Minecraft velocity was not converted to metres per second",
        )
        helper.assertTrue(
            PlanetLabCoordinates.locate(position.add(17.0, 0.0, 0.0)) == null,
            "A position outside the finite chart was silently mapped",
        )
        helper.assertTrue(
            PlanetLabCoordinates.locate(position.add(0.0, -16.0, 0.0)) == null,
            "Singular planet-centre altitude was accepted",
        )
        helper.assertTrue(PlanetLabCoordinates.locate(Vec3(Double.NaN, 0.0, 0.0)) == null, "Nonfinite position was accepted")
        helper.assertTrue(PlanetLabCoordinates.locate(position, Vec3(Double.NaN, 0.0, 0.0)) == null, "Nonfinite velocity was accepted")
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun orbitAdvancesAndRuntimeCanBeDiscarded(helper: GameTestHelper) {
        val server = helper.level.server
        val lab = PlanetLabServices.forServer(server)
        lab.startOrbit()
        val initial = lab.orbitState!!
        helper.runAfterDelay(2) {
            helper.assertTrue(
                lab.elapsedSeconds > 0.0 && (lab.orbitState!!.position - initial.position).length() > 0.0,
                "Orbit did not advance through the NeoForge server tick event",
            )
            lab.stopOrbit()
            helper.assertTrue(lab.orbitState == null && lab.elapsedSeconds == 0.0, "Stopping the orbit retained simulation state")
            PlanetLabServices.forget(server)
            helper.assertTrue(PlanetLabServices.forServer(server) !== lab, "Server runtime survived explicit disposal")
            helper.succeed()
        }
    }
}
