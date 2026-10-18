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

import com.mojang.serialization.Dynamic
import com.mojang.serialization.Lifecycle
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.storage.PrimaryLevelData
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings
import org.cbi.orbitalis.minecraft.world.WorldSystemSettingsOwner

@PrefixGameTestTemplate(false)
object WorldSystemSettingsGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "settings")
    fun nativeWorldCreationSettings(helper: GameTestHelper) {
        val server = helper.level.server
        val settings = server.worldData.levelSettings.copy()
        val owner = settings as Any as WorldSystemSettingsOwner
        val selected = WorldSystemSettings(1000).encode()
        owner.orbitalisWorldSettings = selected
        for (copy in listOf(
            settings.copy(),
            settings.withGameType(GameType.CREATIVE),
            settings.withDifficulty(Difficulty.HARD),
            settings.withDataConfiguration(settings.dataConfiguration),
        )) {
            helper.assertTrue(
                (copy as Any as WorldSystemSettingsOwner).orbitalisWorldSettings == selected,
                "Native settings copy lost selected width",
            )
        }
        val options = WorldOptions(24L, true, false)

        fun save(value: LevelSettings) =
            PrimaryLevelData(value, options, PrimaryLevelData.SpecialWorldProperty.NONE, Lifecycle.stable())
                .createTag(server.registryAccess(), null)
        val root = save(settings)
        helper.assertTrue(root.get(WorldSystemSettings.KEY) == selected, "PrimaryLevelData did not save creation settings")
        val path = server.getWorldPath(LevelResource.ROOT).resolve("orbitalis-world-settings-test.nbt")
        NbtIo.writeCompressed(root, path)
        val loaded = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
        val parsed = LevelSettings.parse(Dynamic(NbtOps.INSTANCE, loaded), settings.dataConfiguration)
        helper.assertTrue(
            (parsed as Any as WorldSystemSettingsOwner).orbitalisWorldSettings == selected,
            "Compressed disk and native parse changed width",
        )
        loaded.remove(WorldSystemSettings.KEY)
        val legacy = LevelSettings.parse(Dynamic(NbtOps.INSTANCE, loaded), settings.dataConfiguration)
        helper.assertTrue((legacy as Any as WorldSystemSettingsOwner).orbitalisWorldSettings == null, "Legacy save became a new selection")
        helper.assertTrue(!save(legacy.copy()).contains(WorldSystemSettings.KEY), "Saving legacy data introduced new dimensions")
        val future =
            selected.copy().apply {
                putInt("Version", 77)
                putString("Opaque", "retain")
            }
        for (opaque in listOf(future, StringTag.valueOf("malformed-but-preserved"))) {
            owner.orbitalisWorldSettings = opaque
            helper.assertTrue(
                save(settings.copy()).get(WorldSystemSettings.KEY) == opaque,
                "Opaque future/malformed record was overwritten",
            )
        }
        helper.succeed()
    }
}
