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

import io.netty.buffer.Unpooled
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.FriendlyByteBuf
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.clouds.EarthCloudState
import org.cbi.orbitalis.minecraft.clouds.EarthCloudStatePayload

@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object EarthCloudStateGameTests {
    @JvmStatic @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        if (System.getProperty("orbitalis.gameTestSuite", "all") in
            setOf("all", "settings")
        ) {
            event.register(EarthCloudStateGameTests::class.java)
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "settings")
    fun nativeEarthCloudWeatherSnapshot(helper: GameTestHelper) {
        val server = helper.level.server
        val earth = server.overworld()
        val oldRain = earth.getRainLevel(1f)
        val oldThunder = earth.getThunderLevel(1f)
        try {
            earth.setRainLevel(0.75f)
            earth.setThunderLevel(0.5f)
            val snapshot = EarthCloudEvents.snapshot(server)
            helper.assertTrue(snapshot.seed == EarthCloudState.derivedSeed(earth.seed), "Cloud identity must come from the Overworld")
            helper.assertTrue(
                snapshot.rain == earth.getRainLevel(1f) && snapshot.thunder == earth.getThunderLevel(1f),
                "Snapshot must read actual native Earth weather",
            )
            helper.assertTrue(
                snapshot.baseHeight == 192.0 && snapshot.weatherTick == earth.gameTime,
                "Snapshot must retain Earth base and epoch",
            )
            val space =
                org.cbi.orbitalis.minecraft.flight.CelestialFlight
                    .levels(server)
                    .first { it !== earth }
            space.setRainLevel(0f)
            space.setThunderLevel(0f)
            helper.assertTrue(EarthCloudEvents.snapshot(server) == snapshot, "Recipient world weather must not replace Earth's weather")
            val buffer = FriendlyByteBuf(Unpooled.buffer())
            try {
                EarthCloudStatePayload.CODEC.encode(buffer, EarthCloudStatePayload(snapshot))
                helper.assertTrue(
                    EarthCloudStatePayload.CODEC.decode(buffer).snapshot == snapshot,
                    "Native Earth cloud packet must round-trip",
                )
            } finally {
                buffer.release()
            }
            helper.succeed()
        } finally {
            earth.setRainLevel(oldRain)
            earth.setThunderLevel(oldThunder)
        }
    }
}
