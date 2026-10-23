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

import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.gametest.framework.GlobalTestReporter
import net.minecraft.gametest.framework.JUnitLikeTestReporter
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.io.File

internal fun onFinished(
    helper: GameTestHelper,
    cleanup: () -> kotlin.Unit,
) {
    helper.testInfo.addListener(
        object : GameTestListener {
            override fun testStructureLoaded(test: GameTestInfo) {}

            override fun testPassed(
                test: GameTestInfo,
                runner: GameTestRunner,
            ) = cleanup()

            override fun testFailed(
                test: GameTestInfo,
                runner: GameTestRunner,
            ) {
                test.error?.printStackTrace()
                cleanup()
            }

            override fun testAddedForRerun(
                test: GameTestInfo,
                retry: GameTestInfo,
                runner: GameTestRunner,
            ) {}
        },
    )
}

@EventBusSubscriber(modid = FlightConsumerMod.ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
object PropulsionGameTests {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        GlobalTestReporter.replaceWith(JUnitLikeTestReporter(File(System.getProperty("orbitalis.consumerGameTestReport"))))
        val suite = System.getProperty("orbitalis.consumerGameTestSuite", "all")
        require(suite in setOf("all", "expedition")) { "Unknown consumer GameTest suite: $suite" }
        event.register(ExpeditionSupplyGameTests::class.java)
        event.register(ShipChunkGameTests::class.java)
        if (suite == "all") event.register(PropulsionGameTests::class.java)
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun flightToolsAvailableByDefault(helper: GameTestHelper) {
        val server = helper.level.server
        for (name in listOf("setup", "build", "shuttle", "setup_rocket", "build_rocket", "rocket")) {
            helper.assertTrue(
                server.functions.get(ResourceLocation.parse("orbitalis_flight_test:$name")).isPresent,
                "Built-in flight function missing or failed to parse: $name",
            )
        }
        helper.assertTrue(
            server.commands.dispatcher.root
                .getChild("orbitalis")
                ?.getChild("flight")
                ?.getChild("autopilot") == null,
            "Flight fixture must not install a controller",
        )
        helper.succeed()
    }
}
