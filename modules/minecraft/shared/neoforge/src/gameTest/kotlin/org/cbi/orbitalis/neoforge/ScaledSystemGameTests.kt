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

import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.moon.LunarChunkGenerator
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData
import org.cbi.orbitalis.minecraft.surface.FlatOverworld
import org.cbi.orbitalis.minecraft.world.WorldSystemSettings
import org.cbi.orbitalis.minecraft.world.WorldSystemSettingsOwner

/** Separate fresh-save suite: other native suites intentionally exercise established legacy geometry. */
@PrefixGameTestTemplate(false)
object ScaledSystemGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "scaled", timeoutTicks = 200)
    fun nativeSmallCelestialSystem(helper: GameTestHelper) {
        val server = helper.level.server
        val earth = server.overworld()
        helper.assertTrue(WorldSystemSettings.get(server)?.earthWidth == 1000, "Creation width was not selected before level creation")
        val bounds = FlatOverworld.footprint(earth)
        helper.assertTrue(
            bounds.minX == -500 && bounds.maxX == 500 && bounds.minZ == -500 && bounds.maxZ == 500,
            "Native finite generation did not capture the selected extent: $bounds",
        )
        val profile = CelestialFlight.sample(server).frames.profile
        helper.assertTrue(
            profile.selectedEarthWidth == 1000 && profile.earthGravity.body.halfWidth == 500.0 &&
                profile.moonGravity.halfWidth == 136.5,
            "Physical profile differs from creation geometry",
        )
        helper.assertTrue(
            profile.surfaceGravity == 11.0 && profile.moonGravity.surfaceAcceleration == 11.0 * 0.165,
            "Selected system does not use the agreed surface gravities",
        )
        helper.assertTrue(
            kotlin.math.abs(profile.moonOrbit.period - SolarSystemProfile.LUNAR_PERIOD_SECONDS) < 1e-7 &&
                kotlin.math.abs(profile.earthOrbit.period - SolarSystemProfile.YEAR_SECONDS) < 1e-6,
            "Scaling changed calendar periods",
        )

        fun solid(
            level: net.minecraft.server.level.ServerLevel,
            x: Int,
            z: Int,
        ): Boolean {
            val chunk = level.getChunkAt(BlockPos(x, 0, z))
            return (level.minBuildHeight until level.maxBuildHeight).any { y -> !chunk.getBlockState(BlockPos(x, y, z)).isAir }
        }
        helper.assertTrue(solid(earth, 490, 0), "Selected Earth lost its interior terrain")
        helper.assertTrue(!solid(earth, 512, 0) && !solid(earth, -513, 0), "Terrain generation escaped selected Earth bounds")
        val moon = checkNotNull(server.getLevel(MoonWorld.KEY))
        val terrain = (moon.chunkSource.generator as LunarChunkGenerator).terrain
        helper.assertTrue(
            terrain.halfSize == 136.5 && solid(moon, 0, 0) && !solid(moon, 137, 0) && !solid(moon, -137, 0),
            "Native Moon generation does not follow its selected physical extent",
        )
        val data =
            org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation
                .data(server)!!
        val restored = OrbitalObjectData.load(data.save(CompoundTag(), server.registryAccess()))
        helper.assertTrue(restored.readable && restored.profile == profile, "Profile save/load lost selected width or spin")
        helper.assertTrue(
            SolarSystemProfile(11.0).earthGravity.body.halfWidth == 5000.0 &&
                SolarSystemProfile(11.0).moonGravity.halfWidth == 1400.0,
            "Selected geometry mutated legacy defaults",
        )
        helper.succeed()
    }
}
