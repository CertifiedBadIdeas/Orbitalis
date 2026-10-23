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
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity

@PrefixGameTestTemplate(false)
object FlightOwnershipGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "flight_ownership")
    fun flightControlOutsideOrbitalis(helper: GameTestHelper) {
        val root =
            helper.level.server.commands.dispatcher.root
                .getChild("orbitalis")
        helper.assertTrue(root?.getChild("flight")?.getChild("autopilot") == null, "Orbitalis must not register an autopilot")
        helper.assertTrue(SableFlightGravity.isManaged(helper.level.server.overworld()), "Removing control disabled surface physics")
        helper.succeed()
    }
}
