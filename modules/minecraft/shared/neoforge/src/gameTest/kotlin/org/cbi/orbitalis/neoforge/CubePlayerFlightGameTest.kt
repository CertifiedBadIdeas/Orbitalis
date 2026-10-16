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

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.lab.CubePlanetLab
import org.cbi.orbitalis.minecraft.lab.CubePlayerMode
import org.cbi.orbitalis.minecraft.lab.CubePlayerModeData
import org.cbi.orbitalis.minecraft.lab.CubePlayerPhysics

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
}
