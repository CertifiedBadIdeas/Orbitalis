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

import net.minecraft.core.HolderLookup
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.ShipDestinationPreload
import org.cbi.orbitalis.minecraft.surface.FlatOverworld

@PrefixGameTestTemplate(false)
object ScaledTravelGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "scaled_surface")
    fun legacyDefaultBorderExpansion(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val settings = level.worldBorder.createSettings()
        try {
            for (custom in listOf(false, true)) {
                val folder =
                    java.nio.file.Files
                        .createTempDirectory(
                            level.server.getWorldPath(LevelResource.ROOT),
                            "border-upgrade-",
                        ).toFile()
                val storage = DimensionDataStorage(folder, level.server.fixerUpper, level.registryAccess())
                storage.set(
                    FlatOverworld.DATA_NAME,
                    object : SavedData() {
                        override fun save(
                            tag: CompoundTag,
                            registries: HolderLookup.Provider,
                        ): CompoundTag = tag.apply { putInt("Version", 1) }
                    }.apply { setDirty() },
                )
                storage.save()
                net.neoforged.neoforge.common.IOUtilities
                    .waitUntilIOWorkerComplete()
                level.worldBorder.setCenter(if (custom) 64.0 else 0.0, 0.0)
                level.worldBorder.size = if (custom) 1536.0 else 2000.0
                val fresh = DimensionDataStorage(folder, level.server.fixerUpper, level.registryAccess())
                FlatOverworld.initialize(level, fresh)
                helper.assertTrue(level.worldBorder.size == if (custom) 1536.0 else 10000.0, "Border migration lost native authority")
                helper.assertTrue(level.worldBorder.centerX == if (custom) 64.0 else 0.0, "Border migration moved customized centre")
            }
        } finally {
            level.worldBorder.applySettings(settings)
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "scaled_surface")
    fun timedDestinationPreparation(helper: GameTestHelper) {
        val level = helper.level.server.overworld()
        val boundary = FlatPlanetSpace.SEA_LEVEL + FlatPlanetSpace.ASCENT_HEIGHT
        ShipDestinationPreload().use { preload ->
            helper.assertTrue(
                !preload.approach(level, Vector3(0.0, boundary - 100.0, 0.0), Vector3(0.0, 10.0, 0.0)),
                "Ten-second arrival started preparation before its time budget",
            )
        }
        ShipDestinationPreload().use { preload ->
            helper.assertTrue(
                preload.approach(level, Vector3(0.0, boundary - 100.0, 0.0), Vector3(100.0, 100.0, 0.0)),
                "One-second arrival did not start destination preparation",
            )
            helper.assertTrue(preload.active, "Preparation did not retain a native ticket")
        }
        ShipDestinationPreload().use { preload ->
            helper.assertTrue(
                !preload.approach(level, Vector3(4900.0, boundary - 100.0, 0.0), Vector3(100.0, 100.0, 0.0)),
                "Forecast outside the open footprint loaded a destination",
            )
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "scaled_surface")
    fun orbitalSpaceHasNoVoidFloor(helper: GameTestHelper) {
        val space = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val orbitItem = ItemEntity(space, 0.0, -6000.0, 0.0, ItemStack(Items.DIAMOND))
        orbitItem.setNoGravity(true)
        space.addFreshEntity(orbitItem)
        orbitItem.tick()
        helper.assertTrue(!orbitItem.isRemoved, "Space removed an entity below the vanilla terrain void floor")
        val surface = helper.level.server.overworld()
        val surfaceItem = ItemEntity(surface, 0.0, -6000.0, 0.0, ItemStack(Items.DIAMOND))
        surface.addFreshEntity(surfaceItem)
        surfaceItem.tick()
        helper.assertTrue(surfaceItem.isRemoved, "Space void exception leaked into Overworld")
        orbitItem.hurt(space.damageSources().generic(), 100.0f)
        helper.assertTrue(orbitItem.isRemoved, "Space void exception suppressed ordinary entity damage")
        helper.succeed()
    }
}
