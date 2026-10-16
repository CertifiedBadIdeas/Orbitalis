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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CubicFace
import org.cbi.orbitalis.core.planet.PlanetCell
import org.cbi.orbitalis.minecraft.integration.sable.SableCubePlanet
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetChunks
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetNeighbours
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetFluids
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

/** Actual native fluid ticks, native liquid states and one authority for each physical cell. */
@PrefixGameTestTemplate(false)
object PlanetFluidGameTests {
    private fun owner(p: BlockPos): CubicFace = CubicFace.entries.maxBy { PlanetCell(p.x, p.y, p.z).centre().dot(it.outward) }

    private class Scene(
        val helper: GameTestHelper,
    ) {
        val level = checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION))
        val planet = SableCubePlanet(level)
        val old = linkedMapOf<BlockPos, BlockState>()
        var cold: Pair<CubicFace, ChunkPos>? = null

        init {
            planet.prepare()
            helper.onEachTick { level.resetEmptyTime() }
            helper.testInfo.addListener(
                object : GameTestListener {
                    override fun testStructureLoaded(test: GameTestInfo) {}

                    override fun testPassed(
                        test: GameTestInfo,
                        runner: GameTestRunner,
                    ) = close()

                    override fun testFailed(
                        test: GameTestInfo,
                        runner: GameTestRunner,
                    ) = close()

                    override fun testAddedForRerun(
                        test: GameTestInfo,
                        retry: GameTestInfo,
                        runner: GameTestRunner,
                    ) {}
                },
            )
            // Preparation only: fluid queries themselves may never materialize a missing column.
            for (face in CubicFace.entries) {
                val body = planet.body(Direction.valueOf(face.name))
                val positions =
                    listOf(-40, 40).flatMap { x ->
                        listOf(-40, 40).flatMap { y ->
                            listOf(-40, 40).map { z ->
                                SablePlanetNeighbours.nativePosition(body, PlanetCell(x, y, z))
                            }
                        }
                    }
                for (x in (positions.minOf { it.x } shr 4)..(positions.maxOf { it.x } shr 4)) {
                    for (z in (positions.minOf { it.z } shr 4)..(positions.maxOf { it.z } shr 4)) {
                        val key = ChunkPos(x, z)
                        if (body.plot.getChunkHolder(body.plot.toLocal(key)) == null) body.plot.newEmptyChunk(key)
                    }
                }
            }
            PlanetNeighbours.enable(level, true)
        }

        fun address(
            p: BlockPos,
            face: CubicFace = owner(p),
        ): BlockPos = SablePlanetNeighbours.nativePosition(planet.body(Direction.valueOf(face.name)), PlanetCell(p.x, p.y, p.z))

        fun write(
            native: BlockPos,
            state: BlockState,
            flags: Int,
        ) {
            check(level.getBlockEntity(native) == null) { "Fluid fixture would overwrite a block entity" }
            old.putIfAbsent(native, level.getBlockState(native))
            level.setBlock(native, state, flags)
        }

        fun set(
            p: BlockPos,
            state: BlockState,
            face: CubicFace = owner(p),
        ) = write(address(p, face), state, 3)

        fun box(
            min: BlockPos = BlockPos(11, 11, -1),
            max: BlockPos = BlockPos(19, 19, 1),
            solid: Boolean = false,
        ) {
            for (x in min.x..max.x) {
                for (y in min.y..max.y) {
                    for (z in min.z..max.z) {
                        val p = BlockPos(x, y, z)
                        for (face in CubicFace.entries) write(address(p, face), Blocks.AIR.defaultBlockState(), 2)
                        if (solid || x == min.x || x == max.x || y == min.y || y == max.y || z == min.z || z == max.z) {
                            write(address(p), Blocks.BEDROCK.defaultBlockState(), 2)
                        }
                    }
                }
            }
        }

        fun clearFluids() {
            for (p in old.keys.toList()) if (!level.getFluidState(p).isEmpty) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2)
        }

        fun water(
            p: BlockPos,
            face: CubicFace = owner(p),
        ): Boolean = level.getFluidState(address(p, face)).type.isSame(Fluids.WATER)

        fun dry(): Boolean = old.keys.all { level.getFluidState(it).isEmpty }

        fun unique() {
            val occupied = mutableSetOf<BlockPos>()
            for (p in old.keys) {
                if (level.getFluidState(p).isEmpty) continue
                val cell = checkNotNull(PlanetFluids.frame(level, p)).physical
                helper.assertTrue(occupied.add(cell), "Duplicate liquid authority at physical cell $cell")
            }
        }

        fun restoreCold() {
            cold?.let { (f, key) -> check(SablePlanetChunks.restore(planet.body(Direction.valueOf(f.name)), key)) }
            cold = null
        }

        fun reload() {
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            val previous = planet.bodies.toList()
            val liquids = old.keys.associateWith { level.getFluidState(it) }
            container.holdingChunkMap.saveAll()
            for (body in previous) {
                if (!body.isRemoved) {
                    container.holdingChunkMap.moveToUnloaded(
                        body,
                        ChunkPos(BlockPos.containing(PlanetLabCoordinates.CENTRE)),
                    )
                }
            }
            container.holdingChunkMap.processChanges()
            helper.assertTrue(planet.bodies.map { it.uniqueId } == previous.map { it.uniqueId }, "Reload changed native chart identities")
            helper.assertTrue(
                previous.all { it.isRemoved } && planet.bodies.none { it in previous },
                "Reload did not replace native chart objects",
            )
            helper.assertTrue(liquids.all { (p, s) -> level.getFluidState(p) == s }, "Native reload changed liquid states")
        }

        fun close() {
            restoreCold()
            PlanetNeighbours.enable(level, false)
            for ((p, state) in old) level.setBlock(p, state, 2)
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_falling_contact", timeoutTicks = 100)
    fun fallingLavaMakesStoneAcrossGravitySeam(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box(BlockPos(13, 12, -1), BlockPos(17, 18, 1), true)
        val source = BlockPos(15, 15, 0)
        val target = BlockPos(15, 14, 0)
        s.set(source, Blocks.AIR.defaultBlockState())
        s.set(target, Blocks.AIR.defaultBlockState())
        s.set(target, Blocks.WATER.defaultBlockState(), CubicFace.EAST)
        s.set(source, Blocks.LAVA.defaultBlockState())
        helper.runAfterDelay(65) {
            helper.assertTrue(
                s.level.getBlockState(s.address(target, CubicFace.EAST)).`is`(Blocks.STONE),
                "Falling lava lost its incoming downward direction at the gravity seam",
            )
            println("ORBITALIS_PLANET_FALLING_CONTACT nativeStone=true gravitySeam=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_all_faces", timeoutTicks = 200)
    fun foreignStoredWaterFallsTowardAllFaces(helper: GameTestHelper) {
        val s = Scene(helper)
        val samples =
            CubicFace.entries.mapIndexed { index, face ->
                val n = face.outward

                fun cell(height: Int) = BlockPos((n.x * height).toInt(), (n.y * height).toInt(), (n.z * height).toInt())
                val source = cell(18)
                val target = cell(17)
                val floor = cell(15)
                val min = BlockPos(minOf(source.x, floor.x) - 1, minOf(source.y, floor.y) - 1, minOf(source.z, floor.z) - 1)
                val max = BlockPos(maxOf(source.x, floor.x) + 1, maxOf(source.y, floor.y) + 1, maxOf(source.z, floor.z) + 1)
                s.box(min, max, true)
                for (height in 16..18) s.set(cell(height), Blocks.AIR.defaultBlockState())
                val storage = CubicFace.entries[(index + 1) % CubicFace.entries.size]
                s.set(source, Blocks.WATER.defaultBlockState(), storage)
                Triple(source, target, storage)
            }
        helper.runAfterDelay(35) {
            for ((source, target, storage) in samples) {
                helper.assertTrue(s.water(target), "Foreign-stored water did not fall toward ${owner(source)}")
                s.set(source, Blocks.AIR.defaultBlockState(), storage)
            }
            s.unique()
            helper.runAfterDelay(120) {
                helper.assertTrue(s.dry(), "All-face flow survived source removal")
                println("ORBITALIS_PLANET_WATER_FACES faces=6 foreignStorage=true nativeTicks=true decay=true")
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_water", timeoutTicks = 450)
    fun waterFallsAcrossPhysicalFaceEdge(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box()
        val source = BlockPos(15, 18, 0)
        val target = BlockPos(15, 14, 0)
        // Without planet addressing, Sable rejects an unsupported UP storage column.
        PlanetNeighbours.enable(s.level, false)
        s.set(source, Blocks.WATER.defaultBlockState())
        helper.runAfterDelay(25) {
            helper.assertTrue(!s.water(target) && !s.water(target, CubicFace.UP), "Ordinary Sable containment guard was bypassed")
            s.clearFluids()
            PlanetNeighbours.enable(s.level, true)
            s.set(source, Blocks.WATER.defaultBlockState())
            helper.runAfterDelay(90) {
                helper.assertTrue(
                    owner(target) == CubicFace.EAST && s.water(target),
                    "Water did not reach EAST chart through native fluid ticks",
                )
                s.unique()
                s.set(source, Blocks.AIR.defaultBlockState())
                helper.runAfterDelay(220) {
                    helper.assertTrue(s.dry(), "Water flow survived removal of its only source")
                    println("ORBITALIS_PLANET_WATER nativeControl=true edge=true sourceRemoved=true nativeFluidTicks=true")
                    helper.succeed()
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_lava", timeoutTicks = 2000)
    fun lavaFallsAcrossPhysicalFaceEdge(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box()
        val source = BlockPos(15, 18, 0)
        val target = BlockPos(15, 14, 0)
        s.set(source, Blocks.LAVA.defaultBlockState())
        helper.runAfterDelay(500) {
            helper.assertTrue(
                s.level
                    .getFluidState(s.address(target))
                    .type
                    .isSame(Fluids.LAVA),
                "Lava did not reach foreign native chart",
            )
            s.unique()
            s.set(source, Blocks.AIR.defaultBlockState())
            helper.runAfterDelay(1200) {
                helper.assertTrue(s.dry(), "Lava flow survived source removal")
                println("ORBITALIS_PLANET_LAVA edge=true sourceRemoved=true nativeDelays=true")
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_corner", timeoutTicks = 220)
    fun waterTurnsAtThreeFaceCorner(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box(BlockPos(11, 11, 11), BlockPos(19, 19, 19))
        s.set(BlockPos(14, 18, 15), Blocks.WATER.defaultBlockState())
        helper.runAfterDelay(120) {
            val south = BlockPos(14, 14, 15)
            val east = BlockPos(14, 13, 14)
            helper.assertTrue(
                owner(south) == CubicFace.SOUTH && owner(east) == CubicFace.EAST,
                "Corner fixture misses two destination charts",
            )
            helper.assertTrue(s.water(south) && s.water(east), "Water failed to traverse UP/SOUTH/EAST corner")
            s.unique()
            println("ORBITALIS_PLANET_WATER_CORNER faces=3 uniquePhysicalCells=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_tunnel", timeoutTicks = 220)
    fun waterUsesPhysicalGravityInForeignTunnel(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box(solid = true)
        val path =
            listOf(
                BlockPos(15, 18, 0),
                BlockPos(15, 17, 0),
                BlockPos(15, 16, 0),
                BlockPos(15, 15, 0),
                BlockPos(15, 14, 0),
                BlockPos(14, 14, 0),
                BlockPos(14, 13, 0),
                BlockPos(13, 13, 0),
                BlockPos(13, 12, 0),
                BlockPos(12, 12, 0),
            )
        for (p in path) s.set(p, Blocks.AIR.defaultBlockState())
        val source = path.first()
        s.set(source, Blocks.WATER.defaultBlockState(), CubicFace.EAST)
        helper.runAfterDelay(7) {
            helper.assertTrue(s.water(path[1]), "Foreign-stored source fell along its storage Y instead of physical gravity")
            helper.assertTrue(
                PlanetFluids.down(s.level, s.address(source, CubicFace.EAST)) == Direction.EAST,
                "Wrong down in foreign storage frame",
            )
        }
        helper.runAfterDelay(120) {
            helper.assertTrue(s.water(BlockPos(14, 13, 0)), "Water did not continue through folded tunnel")
            s.unique()
            println("ORBITALIS_PLANET_WATER_TUNNEL foreignStorage=true gravityIndependent=true nativeFlow=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_rules", timeoutTicks = 150)
    fun nativeFluidSourcesAndContactsCrossCharts(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box(BlockPos(10, 13, -1), BlockPos(16, 16, 1), true)
        val a = BlockPos(12, 14, 0)
        val middle = BlockPos(13, 14, 0)
        val b = BlockPos(14, 14, 0)
        for (p in listOf(a, middle, b)) s.set(p, Blocks.AIR.defaultBlockState())
        s.set(a, Blocks.WATER.defaultBlockState())
        s.set(b, Blocks.WATER.defaultBlockState(), CubicFace.EAST)
        helper.runAfterDelay(35) {
            helper.assertTrue(
                s.level.getFluidState(s.address(middle)).isSource,
                "Native water source conversion failed across storage charts",
            )
            s.clearFluids()
            s.set(middle, Blocks.LAVA.defaultBlockState())
            s.set(b, Blocks.WATER.defaultBlockState(), CubicFace.EAST)
            helper.assertTrue(
                s.level.getBlockState(s.address(middle)).`is`(Blocks.OBSIDIAN),
                "Foreign water failed native source-lava contact",
            )
            s.set(middle, Fluids.FLOWING_LAVA.getFlowing(4, false).createLegacyBlock())
            helper.assertTrue(
                s.level.getBlockState(s.address(middle)).`is`(Blocks.COBBLESTONE),
                "Foreign water failed native flowing-lava contact",
            )
            println("ORBITALIS_PLANET_FLUID_RULES sourceConversion=true obsidian=true cobblestone=true nativeRegistry=true")
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_shapes", timeoutTicks = 150)
    fun waterRespectsRotatedSlabFaces(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box(BlockPos(10, 13, -2), BlockPos(16, 16, 2), true)
        val source = BlockPos(12, 14, 0)
        val target = BlockPos(13, 14, 0)
        s.set(source, Blocks.AIR.defaultBlockState())
        s.set(target, Blocks.AIR.defaultBlockState())
        s.set(target, Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), CubicFace.EAST)
        // Native slabs accept source water, not FLOWING_WATER. Two open sources permit native conversion.
        s.set(target.north(), Blocks.WATER.defaultBlockState())
        s.set(source, Blocks.WATER.defaultBlockState())
        helper.runAfterDelay(35) {
            helper.assertTrue(
                !s.level.getBlockState(s.address(target, CubicFace.EAST)).getValue(BlockStateProperties.WATERLOGGED),
                "Water crossed a physically sealed rotated slab face",
            )
            s.set(target, Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), CubicFace.EAST)
            helper.runAfterDelay(35) {
                helper.assertTrue(
                    s.level.getBlockState(s.address(target, CubicFace.EAST)).getValue(BlockStateProperties.WATERLOGGED),
                    "Water did not enter the open rotated slab face",
                )
                println("ORBITALIS_PLANET_FLUID_SHAPES nativeWaterlogging=true rotatedClosedFace=true rotatedOpenFace=true")
                helper.succeed()
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_fluid_reload", timeoutTicks = 500)
    fun coldFluidTicksResumeAndSurviveNativeReload(helper: GameTestHelper) {
        val s = Scene(helper)
        s.box()
        val source = BlockPos(15, 18, 0)
        val target = BlockPos(15, 14, 0)
        val body = s.planet.body(Direction.WEST)
        val key = body.plot.toLocal(ChunkPos(s.address(source, CubicFace.WEST)))
        helper.assertTrue(SablePlanetChunks.evict(body, key), "Could not evict candidate fluid column")
        s.cold = CubicFace.WEST to key
        s.set(source, Blocks.WATER.defaultBlockState())
        helper.runAfterDelay(40) {
            helper.assertTrue(
                s.level.getFluidState(s.address(source)).isSource && !s.water(target),
                "Cold candidate column did not defer native flow",
            )
            helper.assertTrue(body.plot.getChunkHolder(key) == null, "Fluid query loaded a cold candidate column")
            s.restoreCold()
            helper.runAfterDelay(90) {
                helper.assertTrue(s.water(target), "Native fluid retry did not resume after candidate restoration")
                s.unique()
                s.reload()
                s.set(source, Blocks.AIR.defaultBlockState())
                helper.runAfterDelay(220) {
                    helper.assertTrue(s.dry(), "Restored native fluid ticks did not drain source-free flow")
                    println(
                        "ORBITALIS_PLANET_FLUID_RELOAD coldDeferred=true noQueryLoad=true nativeRetry=true identities=true states=true nativeDecay=true",
                    )
                    helper.succeed()
                }
            }
        }
    }
}
