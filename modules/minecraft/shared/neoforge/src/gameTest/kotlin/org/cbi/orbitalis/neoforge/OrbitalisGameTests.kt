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

import net.minecraft.gametest.framework.GlobalTestReporter
import net.minecraft.gametest.framework.JUnitLikeTestReporter
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.Orbitalis
import java.io.File

@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object OrbitalisGameTests {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        System.getProperty("orbitalis.gameTestReport")?.let { path ->
            GlobalTestReporter.replaceWith(JUnitLikeTestReporter(File(path)))
        }
        val suite = System.getProperty("orbitalis.gameTestSuite", "all")
        require(suite in setOf("all", "surface", "transfer", "flat", "travel", "navigation", "sparse", "atlas", "terrain")) {
            "Unknown GameTest suite: $suite"
        }
        if (suite == "all" || suite == "terrain") event.register(TerrainGameTests::class.java)
        if (suite == "terrain") return
        if (suite == "atlas") {
            event.register(SurfaceAtlasGameTests::class.java)
            return
        }
        if (suite == "all") event.register(SurfaceAtlasGameTests::class.java)
        if (suite == "sparse") {
            event.register(SparseSpaceGameTests::class.java)
            event.register(VacuumStorageGameTests::class.java)
            return
        }
        if (suite == "navigation") {
            event.register(TrajectoryGameTests::class.java)
            return
        }
        if (suite == "travel") {
            event.register(AutomaticTravelGameTests::class.java)
            return
        }
        if (suite == "flat") {
            event.register(FlatGravityGameTests::class.java)
            return
        }
        if (suite != "transfer") {
            event.register(SurfaceGameTests::class.java)
            event.register(ScaledTravelGameTests::class.java)
            event.register(PlayerGravityGameTests::class.java)
        }
        if (suite == "surface") return
        if (ModList.get().isLoaded("sable")) {
            if (suite == "all") {
                event.register(SparseSpaceGameTests::class.java)
                event.register(VacuumStorageGameTests::class.java)
                event.register(FlatGravityGameTests::class.java)
                event.register(TrajectoryGameTests::class.java)
            }
            event.register(AutomaticTravelGameTests::class.java)
            event.register(SableTransferGameTests::class.java)
        }
    }
}
