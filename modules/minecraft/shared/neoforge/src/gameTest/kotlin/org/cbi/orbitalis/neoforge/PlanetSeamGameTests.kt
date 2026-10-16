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

import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DropperBlock
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.level.block.RedstoneLampBlock
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CubicFace
import org.cbi.orbitalis.core.planet.CubicPlanetVolume
import org.cbi.orbitalis.core.planet.PlanetCell
import org.cbi.orbitalis.minecraft.integration.sable.SableCubePlanet
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetChunks
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetNeighbours
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

@PrefixGameTestTemplate(false)
object PlanetSeamGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "planet_seams", timeoutTicks = 220)
    fun planetSeamNeighboursAndInventories(helper: GameTestHelper) {
        val level = checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION))
        // A playerless ServerLevel stops native block-entity ticking after 300 idle ticks.
        // Keep this isolated fixture active for its native hopper scenario; do not manually tick it.
        level.resetEmptyTime()
        val planet = SableCubePlanet(level)
        planet.prepare()
        val volume = CubicPlanetVolume(16)
        val bodies = CubicFace.entries.associateWith { planet.body(Direction.valueOf(it.name)) }

        data class Link(
            val source: BlockPos,
            val target: BlockPos,
            val direction: Direction,
            val targetDirection: Direction,
            val destination: ServerSubLevel,
        )
        val links = mutableListOf<Link>()
        val old = linkedMapOf<BlockPos, BlockState>()
        var cold: Pair<ServerSubLevel, ChunkPos>? = null

        fun remember(position: BlockPos) {
            check(level.getBlockEntity(position) == null) { "Seam fixture would overwrite a block entity at $position" }
            old.putIfAbsent(position, level.getBlockState(position))
        }

        fun cleanup() {
            cold?.let { (body, key) -> SablePlanetChunks.restore(body, key) }
            cold = null
            PlanetNeighbours.enable(level, false)
            old.forEach { (position, state) ->
                (level.getBlockEntity(position) as? Container)?.clearContent()
                level.setBlock(position, state, 3)
            }
        }
        helper.testInfo.addListener(
            object : net.minecraft.gametest.framework.GameTestListener {
                override fun testStructureLoaded(test: net.minecraft.gametest.framework.GameTestInfo) {}

                override fun testPassed(
                    test: net.minecraft.gametest.framework.GameTestInfo,
                    runner: net.minecraft.gametest.framework.GameTestRunner,
                ) = cleanup()

                override fun testFailed(
                    test: net.minecraft.gametest.framework.GameTestInfo,
                    runner: net.minecraft.gametest.framework.GameTestRunner,
                ) = cleanup()

                override fun testAddedForRerun(
                    test: net.minecraft.gametest.framework.GameTestInfo,
                    retry: net.minecraft.gametest.framework.GameTestInfo,
                    runner: net.minecraft.gametest.framework.GameTestRunner,
                ) {
                }
            },
        )
        PlanetNeighbours.reset(level)
        helper.assertTrue(PlanetNeighbours.active(level), "Planet seams are not enabled by default")
        helper.assertTrue(!PlanetNeighbours.active(helper.level), "Planet seams changed the ordinary test dimension")
        val commands = level.server.commands
        val source =
            level.server
                .createCommandSourceStack()
                .withLevel(level)
                .withSuppressedOutput()
        commands.performPrefixedCommand(source, "orbitalis planet seams false")
        helper.assertTrue(!PlanetNeighbours.active(level), "Explicit seam disable did not override the default")
        commands.performPrefixedCommand(source, "orbitalis planet seams true")
        helper.assertTrue(PlanetNeighbours.active(level), "Explicit seam enable failed")
        PlanetNeighbours.reset(level)
        for (a in CubicFace.entries) {
            for (b in CubicFace.entries) {
                if (a.outward.dot(b.outward) != 0.0) continue
                val third = if (a.ordinal < b.ordinal) -6 else 6
                val av = a.outward * 14.5
                val bv = b.outward * (if (b.ordinal < a.ordinal) 13.5 else 14.5)
                val p = av + bv
                val cell =
                    PlanetCell(
                        if (a.outward.x == 0.0 && b.outward.x == 0.0) third else kotlin.math.floor(p.x).toInt(),
                        if (a.outward.y == 0.0 && b.outward.y == 0.0) third else kotlin.math.floor(p.y).toInt(),
                        if (a.outward.z == 0.0 && b.outward.z == 0.0) third else kotlin.math.floor(p.z).toInt(),
                    )
                val targetCell = PlanetCell(cell.x + b.outward.x.toInt(), cell.y + b.outward.y.toInt(), cell.z + b.outward.z.toInt())
                helper.assertTrue(
                    volume.owner(cell) == a && volume.owner(targetCell) == b,
                    "Bad directed seam fixture $a -> $b: $cell $targetCell",
                )
                val direction =
                    Direction.fromDelta(
                        b.outward.dot(a.right).toInt(),
                        b.outward.dot(a.outward).toInt(),
                        b.outward.dot(a.forward).toInt(),
                    )!!
                val source = SablePlanetNeighbours.nativePosition(bodies.getValue(a), cell)
                val target = SablePlanetNeighbours.nativePosition(bodies.getValue(b), targetCell)
                val edge = checkNotNull(PlanetNeighbours.neighbour(level, source, direction))
                helper.assertTrue(edge.ready && edge.position == target, "Wrong seam destination $a -> $b: $edge expected $target")
                val reverse = checkNotNull(PlanetNeighbours.neighbour(level, target, edge.direction.opposite))
                helper.assertTrue(
                    reverse.ready && reverse.position == source && reverse.direction == direction.opposite,
                    "Non-reciprocal seam $a -> $b",
                )
                remember(source)
                remember(target)
                links.add(Link(source, target, direction, edge.direction, bodies.getValue(b)))
            }
        }
        helper.assertTrue(links.size == 24 && old.size == 48, "Directed seam cases overlap")
        // All eight three-face corners, centre sign changes and interior tie planes.
        var interior = 0
        for (x in listOf(-16, -1, 0, 15)) {
            for (y in listOf(-16, -1, 0, 15)) {
                for (z in listOf(-16, -1, 0, 15)) {
                    val cell = PlanetCell(x, y, z)
                    val a = volume.owner(cell)
                    val source = SablePlanetNeighbours.nativePosition(bodies.getValue(a), cell)
                    for (d in Direction.entries) {
                        val step = a.right * d.stepX.toDouble() + a.outward * d.stepY.toDouble() + a.forward * d.stepZ.toDouble()
                        val targetCell = PlanetCell(x + step.x.toInt(), y + step.y.toInt(), z + step.z.toInt())
                        if (!volume.contains(targetCell) || volume.owner(targetCell) == a) continue
                        val edge = checkNotNull(PlanetNeighbours.neighbour(level, source, d))
                        helper.assertTrue(
                            edge.ready &&
                                edge.position ==
                                SablePlanetNeighbours.nativePosition(
                                    bodies.getValue(volume.owner(targetCell)),
                                    targetCell,
                                ),
                            "Interior/corner mismatch $cell $d",
                        )
                        interior++
                    }
                }
            }
        }
        // Verify that visual/native proximity alone does not connect the two charts.
        val baseline = links.first()
        PlanetNeighbours.enable(level, false)
        level.setBlock(baseline.target, Blocks.REDSTONE_LAMP.defaultBlockState(), 3)
        level.setBlock(baseline.source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3)
        helper.assertTrue(!level.getBlockState(baseline.target).getValue(RedstoneLampBlock.LIT), "Disabled seam hooks leaked")
        level.setBlock(baseline.source, Blocks.AIR.defaultBlockState(), 3)
        PlanetNeighbours.enable(level, true)
        // Neither side manually calls neighbourChanged: use normal block placement and removal.
        for (link in links) {
            level.setBlock(link.target, Blocks.REDSTONE_LAMP.defaultBlockState(), 3)
            level.setBlock(link.source, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3)
            helper.assertTrue(
                level.getBlockState(link.target).getValue(RedstoneLampBlock.LIT),
                "Lamp did not receive a seam notification: $link",
            )
            level.setBlock(link.source, Blocks.AIR.defaultBlockState(), 3)
        }
        helper.runAfterDelay(6) {
            for (link in links) {
                helper.assertTrue(
                    !level.getBlockState(link.target).getValue(RedstoneLampBlock.LIT),
                    "Lamp retained stale power across seam: $link",
                )
                level.setBlock(link.target, Blocks.BARREL.defaultBlockState(), 3)
                val block =
                    if (link.direction ==
                        Direction.UP
                    ) {
                        Blocks.DROPPER.defaultBlockState().setValue(DropperBlock.FACING, link.direction)
                    } else {
                        Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, link.direction)
                    }
                level.setBlock(link.source, block, 3)
                (level.getBlockEntity(link.source) as Container).setItem(0, ItemStack(Items.DIAMOND, 9))
                if (link.direction == Direction.UP) level.scheduleTick(link.source, Blocks.DROPPER, 1)
            }
            helper.runAfterDelay(50) {
                fun count(position: BlockPos): Int {
                    val inventory = level.getBlockEntity(position) as Container
                    return (0 until inventory.containerSize).sumOf { inventory.getItem(it).count }
                }
                for (link in links) {
                    helper.assertTrue(
                        count(link.target) > 0 && count(link.source) + count(link.target) == 9,
                        "Native inventory did not transfer/conserve items: $link source=${count(link.source)} target=${count(link.target)}",
                    )
                }
                val link = links.first { it.direction != Direction.UP }
                val local = link.destination.plot.toLocal(ChunkPos(link.target))
                val before = count(link.target)
                helper.assertTrue(SablePlanetChunks.evict(link.destination, local), "Could not unload destination chunk")
                cold = link.destination to local
                helper.assertTrue(
                    PlanetNeighbours.neighbour(level, link.source, link.direction)?.ready == false,
                    "Cold destination was accepted as resident",
                )
                val sourceBefore = count(link.source)
                helper.assertTrue(sourceBefore > 0, "Cold test needs a pending native transfer")
                helper.runAfterDelay(12) {
                    helper.assertTrue(count(link.source) == sourceBefore, "Native transfer continued into a cold destination")
                    helper.assertTrue(SablePlanetChunks.restore(link.destination, local), "Could not restore native destination chunk")
                    cold = null
                    helper.assertTrue(count(link.target) == before, "Native destination inventory changed while cold")
                    helper.runAfterDelay(12) {
                        helper.assertTrue(count(link.source) + count(link.target) == 9, "Items duplicated after reconnect")
                        helper.assertTrue(count(link.source) < sourceBefore || sourceBefore == 0, "Native transfer did not resume")

                        // Authored blocks above the reference cube: top and side access differ.
                        // A furnace accepts raw iron through native +Y, but rejects it from a side.
                        data class Port(
                            val source: BlockPos,
                            val target: BlockPos,
                            val direction: Direction,
                            val accepts: Boolean,
                        )
                        val ports =
                            listOf(
                                Triple(PlanetCell(19, 18, 3), PlanetCell(18, 18, 3), Direction.WEST),
                                Triple(PlanetCell(18, 19, 5), PlanetCell(18, 18, 5), Direction.DOWN),
                            ).mapIndexed { index, (sourceCell, targetCell, direction) ->
                                // The experiment refuses unknown foreign columns instead of assuming air.
                                // Materialize only the fixture columns, using native chunk allocation.
                                for (body in bodies.values) {
                                    for (cell in listOf(sourceCell, targetCell)) {
                                        val native = SablePlanetNeighbours.nativePosition(body, cell)
                                        val global = ChunkPos(native)
                                        if (body.plot.getChunkHolder(body.plot.toLocal(global)) == null) body.plot.newEmptyChunk(global)
                                    }
                                }
                                val source = SablePlanetNeighbours.nativePosition(bodies.getValue(CubicFace.UP), sourceCell)
                                val target = SablePlanetNeighbours.nativePosition(bodies.getValue(CubicFace.EAST), targetCell)
                                remember(source)
                                remember(target)
                                level.setBlock(target, Blocks.FURNACE.defaultBlockState(), 3)
                                level.setBlock(source, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, direction), 3)
                                (level.getBlockEntity(source) as Container).setItem(0, ItemStack(Items.RAW_IRON, 9))
                                Port(source, target, direction, index == 0)
                            }
                        helper.runAfterDelay(20) {
                            for (port in ports) {
                                val sourceCount = count(port.source)
                                val targetCount = count(port.target)
                                helper.assertTrue(sourceCount + targetCount == 9, "Mapped furnace lost items: $port")
                                helper.assertTrue(
                                    (targetCount > 0) == port.accepts,
                                    "Wrong native furnace port: $port received=$targetCount",
                                )
                                if (port.accepts) {
                                    helper.assertTrue(
                                        (level.getBlockEntity(port.target) as Container).getItem(0).`is`(Items.RAW_IRON),
                                        "Input reached the wrong native slot",
                                    )
                                }
                            }
                            println(
                                "ORBITALIS_PLANET_SEAMS directedEdges=24 interiorChecks=$interior nativeLamps=true " +
                                    "nativeInventories=true coldReconnect=true sidedFurnace=true",
                            )
                            helper.succeed()
                        }
                    }
                }
            }
        }
    }
}
