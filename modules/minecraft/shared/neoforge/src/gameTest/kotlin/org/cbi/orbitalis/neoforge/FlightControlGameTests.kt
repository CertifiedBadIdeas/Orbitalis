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
import net.neoforged.fml.ModList
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.control.FlightEngines
import org.cbi.orbitalis.minecraft.navigation.FlightPlanHooks

@PrefixGameTestTemplate(false)
object FlightControlGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "control")
    fun flightControlWithoutPropulsion(helper: GameTestHelper) {
        helper.assertTrue(!ModList.get().isLoaded("createpropulsion"), "Absence scenario unexpectedly includes Propulsion")
        helper.assertTrue(FlightEngines.backend == null, "An engine backend was installed without its mod")
        helper.assertTrue(FlightPlanHooks.execute == null, "Map execution was enabled without an engine backend")
        val root =
            helper.level.server.commands.dispatcher.root
                .getChild("orbitalis")
        helper.assertTrue(
            root?.getChild("flight")?.getChild("autopilot")?.getChild("list") != null,
            "Ship directory commands are unavailable without engines",
        )
        var rejected = false
        try {
            FlightEngines.requireBackend()
        } catch (_: IllegalStateException) {
            rejected = true
        }
        helper.assertTrue(rejected, "Missing engine backend silently accepted flight execution")
        helper.succeed()
    }
}
