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
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.lighting.SkyLighting
import org.cbi.orbitalis.minecraft.lighting.SkyLightingData
import org.cbi.orbitalis.minecraft.lighting.SkyLightingPayload

@PrefixGameTestTemplate(false)
object SkyLightingGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "settings")
    fun nativeSkyLightingSettings(helper: GameTestHelper) {
        val server = helper.level.server
        val level = server.overworld()
        val data = SkyLightingData.get(server)
        helper.assertTrue(!data.cosmic, "Absent sky settings must default to vanilla")
        val source = server.createCommandSourceStack().withPermission(4)
        val dispatcher = server.commands.dispatcher
        dispatcher.execute("orbitalis sky cosmic", source)
        helper.assertTrue(data.cosmic && data.isDirty, "Operator command must update saved world setting")
        val registries = server.registryAccess()
        val path = server.getWorldPath(LevelResource.ROOT).resolve("sky-lighting-test.nbt")
        NbtIo.writeCompressed(data.save(CompoundTag(), registries), path)
        helper.assertTrue(
            SkyLightingData.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), registries).cosmic,
            "Cosmic choice must survive disk roundtrip",
        )
        helper.assertTrue(SkyLighting.physicalOverworld(level), "Cosmic selection must be active on server")
        val before = level.getTimeOfDay(0f)
        val oldTime = level.dayTime
        level.setDayTime(oldTime + 12000L)
        helper.assertTrue(kotlin.math.abs(before - level.getTimeOfDay(0f)) < 1e-6, "Native time must not move physical sky")
        level.setDayTime(oldTime)
        helper.assertTrue(
            !SkyLighting.physicalOverworld(server.getLevel(org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE)!!),
            "Overworld switch must not select space mode",
        )
        dispatcher.execute("orbitalis sky vanilla", source)
        helper.assertTrue(!data.cosmic && !SkyLighting.physicalOverworld(level), "Vanilla mode must restore native time")
        helper.assertTrue(
            level.getTimeOfDay(0f) == level.dimensionType().timeOfDay(level.dayTime),
            "Vanilla sky must retain native day angle",
        )
        for (bad in listOf(
            CompoundTag(),
            CompoundTag().apply {
                putInt("Version", 77)
                putString("Overworld", "cosmic")
                putString("Opaque", "retain")
            },
            CompoundTag().apply {
                putInt("Version", 1)
                putString("Overworld", "unknown")
            },
        )) {
            val loaded = SkyLightingData.load(bad, registries)
            helper.assertTrue(
                !loaded.writable && !loaded.cosmic && loaded.save(CompoundTag(), registries) == bad,
                "Future/malformed settings must be preserved and fall back to vanilla",
            )
            var rejected = false
            try {
                loaded.select(true)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Unreadable settings must refuse mutation")
        }
        for (cosmic in listOf(false, true)) {
            val buf = FriendlyByteBuf(Unpooled.buffer())
            try {
                SkyLightingPayload.CODEC.encode(buf, SkyLightingPayload(cosmic))
                helper.assertTrue(SkyLightingPayload.CODEC.decode(buf).cosmic == cosmic, "Sky packet roundtrip failed")
            } finally {
                buf.release()
            }
        }
        val invalid = FriendlyByteBuf(Unpooled.buffer()).writeByte(2)
        try {
            var rejected = false
            try {
                SkyLightingPayload.CODEC.decode(invalid)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            helper.assertTrue(rejected, "Unknown sky mode packet must be rejected")
        } finally {
            invalid.release()
        }
        val previousGuard = org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation.canAccelerate
        try {
            org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation.canAccelerate = { false }
            helper.assertTrue(
                dispatcher.execute("orbitalis time add 1", source) == 0 && !server.tickRateManager().isSprinting,
                "Loaded ships must prevent acceleration",
            )
            org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation.canAccelerate = { true }
            helper.assertTrue(
                dispatcher.execute("orbitalis time set sunrise", source) == 1 && server.tickRateManager().isSprinting,
                "Forward sunrise must start a real native tick sprint",
            )
            dispatcher.execute("orbitalis time stop", source)
            helper.assertTrue(!server.tickRateManager().isSprinting, "Stop must end the sprint")
        } finally {
            server.tickRateManager().stopSprinting()
            org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation.canAccelerate = previousGuard
        }
        helper.succeed()
    }
}
