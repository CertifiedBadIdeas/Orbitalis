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
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.joml.Quaterniond
import java.util.UUID
import kotlin.math.abs

@PrefixGameTestTemplate(false)
object LaunchBookmarkGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun launchBookmarkRoundTripAndValidation(helper: GameTestHelper) {
        val registries = helper.level.registryAccess()
        val pilot = UUID.randomUUID()
        val ship = UUID.randomUUID()
        val data = LaunchSites()
        val site = LaunchSites.Site(ship, Vector3(15.0, 70.0, -10.0), Quaterniond().rotateY(0.2))
        data.put(pilot, site)
        val tag = data.save(CompoundTag(), registries)
        val restored = LaunchSites.load(tag, registries).require(pilot, ship)
        helper.assertTrue(
            (restored.position - site.position).length() < 1e-12 &&
                abs(restored.attitude.dot(site.attitude)) > 0.999999,
            "Launch bookmark did not round-trip",
        )

        fun rejected(
            reason: String,
            action: () -> kotlin.Unit,
        ) {
            try {
                action()
                helper.fail(reason)
            } catch (_: IllegalArgumentException) {
            }
        }
        helper.assertTrue(restored.surface == Level.OVERWORLD, "Bookmark changed surface")
        val legacy =
            tag.copy().also {
                it.putInt("version", 1)
                it.getList("sites", 10).getCompound(0).remove("surface")
            }
        rejected("Retired version-1 bookmark was reinterpreted") { LaunchSites.load(legacy, registries).require(pilot, ship) }
        val retired = tag.copy().also { it.getList("sites", 10).getCompound(0).putString("surface", "orbitalis:flight_test") }
        rejected("Retired version-2 bookmark was reinterpreted") { LaunchSites.load(retired, registries).require(pilot, ship) }
        val mixed = tag.copy().also { it.getList("sites", 10).add(retired.getList("sites", 10).getCompound(0).copy()) }
        helper.assertTrue(
            LaunchSites.load(mixed, registries).require(pilot, ship).surface == Level.OVERWORLD,
            "Retired bookmark discarded the active bookmark",
        )
        val ordinary =
            LaunchSites().also {
                it.put(
                    pilot,
                    LaunchSites.Site(ship, Vector3(15.0, -50.0, -10.0), site.attitude, Level.OVERWORLD),
                )
            }
        helper.assertTrue(
            LaunchSites.load(ordinary.save(CompoundTag(), registries), registries).require(pilot, ship).surface == Level.OVERWORLD,
            "Overworld bookmark lost surface",
        )
        val missingSurface = tag.copy().also { it.getList("sites", 10).getCompound(0).remove("surface") }
        rejected("Missing bookmark surface accepted") { LaunchSites.load(missingSurface, registries) }
        rejected("Bookmark authorized a different ship") { LaunchSites.load(tag, registries).require(pilot, UUID.randomUUID()) }
        for (version in listOf(0, 99)) {
            val corrupt = tag.copy().also { it.putInt("version", version) }
            rejected("Unsupported bookmark version accepted") { LaunchSites.load(corrupt, registries) }
        }
        for (field in listOf("x", "y", "qw")) {
            val corrupt = tag.copy()
            corrupt.getList("sites", 10).getCompound(0).putDouble(field, Double.NaN)
            rejected("Malformed launch bookmark accepted") { LaunchSites.load(corrupt, registries) }
        }
        val missing = tag.copy()
        missing.getList("sites", 10).getCompound(0).remove("qy")
        rejected("Incomplete bookmark accepted") { LaunchSites.load(missing, registries) }
        val duplicate = tag.copy()
        val entries = duplicate.getList("sites", 10)
        entries.add(entries.getCompound(0).copy())
        rejected("Duplicate launch bookmark accepted") { LaunchSites.load(duplicate, registries) }
        helper.succeed()
    }
}
