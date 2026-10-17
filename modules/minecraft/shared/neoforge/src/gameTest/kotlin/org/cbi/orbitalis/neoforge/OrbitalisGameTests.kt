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
        require(suite in setOf("all", "surface", "transfer", "flat")) { "Unknown GameTest suite: $suite" }
        if (suite == "flat") {
            event.register(FlatGravityGameTests::class.java)
            return
        }
        if (suite != "transfer") event.register(SurfaceGameTests::class.java)
        if (suite == "surface") return
        if (ModList.get().isLoaded("sable")) {
            if (suite == "all") {
                event.register(SableFlightGameTests::class.java)
                event.register(FlatGravityGameTests::class.java)
            }
            event.register(SableTransferGameTests::class.java)
        }
    }
}
