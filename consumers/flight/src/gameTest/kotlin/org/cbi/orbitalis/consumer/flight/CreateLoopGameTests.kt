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

import com.simibubi.create.content.fluids.FluidPropagator
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import com.simibubi.create.infrastructure.config.AllConfigs
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CubicFace
import org.cbi.orbitalis.core.planet.CubicPlanetVolume
import org.cbi.orbitalis.core.planet.PlanetCell
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetNeighbours
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

/** A nonplanar closed route, with all blocks remaining native Create block entities. */
@PrefixGameTestTemplate(false)
object CreateLoopGameTests {
    private fun block(name: String): BlockState =
        BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name)).defaultBlockState()

    private fun cell(p: BlockPos) = PlanetCell(p.x, p.y, p.z)

    private fun direction(delta: BlockPos) = checkNotNull(Direction.fromDelta(delta.x, delta.y, delta.z))

    private val volume = CubicPlanetVolume(16)
    private val corners =
        listOf(
            BlockPos(0, 12, -8),
            BlockPos(12, 12, -8),
            BlockPos(12, -14, -8),
            BlockPos(12, -14, 14),
            BlockPos(-16, -14, 14),
            BlockPos(-16, 12, 14),
            BlockPos(-16, 12, -16),
            BlockPos(0, 12, -16),
        )
    private val ring =
        buildList {
            for (i in corners.indices) {
                val start = corners[i]
                val end = corners[(i + 1) % corners.size]
                val distance = start.distManhattan(end)
                val step = direction(BlockPos((end.x - start.x) / distance, (end.y - start.y) / distance, (end.z - start.z) / distance))
                for (n in 0 until distance) add(start.relative(step, n))
            }
        }

    private fun previous(p: BlockPos) = ring[(ring.indexOf(p) + ring.size - 1) % ring.size]

    private fun next(p: BlockPos) = ring[(ring.indexOf(p) + 1) % ring.size]

    private fun face(p: BlockPos): CubicFace = if (p.x == 12 && p.y in 7..11 && p.z == -8) CubicFace.UP else volume.owner(cell(p))

    private fun local(
        face: CubicFace,
        d: Direction,
    ): Direction {
        val vector =
            org.cbi.orbitalis.core.planet
                .Vector3(d.stepX.toDouble(), d.stepY.toDouble(), d.stepZ.toDouble())
        return checkNotNull(
            Direction.fromDelta(vector.dot(face.right).toInt(), vector.dot(face.outward).toInt(), vector.dot(face.forward).toInt()),
        )
    }

    private fun local(
        p: BlockPos,
        d: Direction,
    ) = local(face(p), d)

    private class Scene(
        val helper: GameTestHelper,
        val extra: List<BlockPos>,
    ) {
        val delegate = FaceFrameScene(checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)))
        val level get() = delegate.level
        val planet get() = delegate.planet

        init {
            delegate.prepare()
            helper.onEachTick { level.resetEmptyTime() }
            onFinished(helper) {
                val container = checkNotNull(SubLevelContainer.getContainer(level))
                container.holdingChunkMap.processChanges()
                PlanetNeighbours.enable(level, false)
                delegate.close()
            }
            // Excavation is only fixture preparation. There is no secondary block authority.
            for (p in (ring + extra).distinct()) {
                for (face in CubicFace.entries) {
                    val body = planet.body(Direction.valueOf(face.name))
                    val native = SablePlanetNeighbours.nativePosition(body, cell(p))
                    val global = ChunkPos(native)
                    if (body.plot.getChunkHolder(body.plot.toLocal(global)) == null) body.plot.newEmptyChunk(global)
                    level.removeBlock(native, false)
                }
            }
            PlanetNeighbours.enable(level, true)
        }

        fun address(p: BlockPos): BlockPos = SablePlanetNeighbours.nativePosition(planet.body(Direction.valueOf(face(p).name)), cell(p))

        fun put(
            p: BlockPos,
            state: BlockState,
        ) {
            check(level.setBlock(address(p), state, 3) || level.getBlockState(address(p)) == state)
        }

        fun remove(p: BlockPos) {
            level.removeBlock(address(p), false)
        }

        fun topology() {
            check(ring.toSet().size == ring.size)
            helper.assertTrue(ring.map(::face).toSet().size == 6, "The closed route must visit all six storage charts")
            helper.assertTrue(
                (7..11).all {
                    face(BlockPos(12, it, -8)) != volume.owner(cell(BlockPos(12, it, -8)))
                },
                "Missing five-block foreign continuation",
            )
            var basis = listOf(Direction.EAST, Direction.UP, Direction.SOUTH)
            var reference = address(ring.first())
            val initial = reference
            var transitions = 0
            for (p in ring) {
                val q = next(p)
                val source = address(p)
                val target = address(q)
                val d = local(p, direction(q.subtract(p)))
                val e = PlanetNeighbours.neighbour(level, source, d)
                helper.assertTrue(
                    if (e == null) source.relative(d) == target else e.ready && e.position == target,
                    "Missing physical edge $p/${face(p)} -> $q/${face(q)}: $e",
                )
                val reverseDirection = local(q, direction(p.subtract(q)))
                val reverse = PlanetNeighbours.neighbour(level, target, reverseDirection)
                helper.assertTrue(
                    if (reverse ==
                        null
                    ) {
                        target.relative(reverseDirection) == source
                    } else {
                        reverse.ready && reverse.position == source
                    },
                    "Non-reciprocal physical edge $p -> $q",
                )
                val relation = PlanetNeighbours.relation(level, source, target)
                if (relation != null) {
                    transitions++

                    fun mapped(v: BlockPos): BlockPos {
                        val x = relation.toTarget(Direction.EAST).normal
                        val y = relation.toTarget(Direction.UP).normal
                        val z = relation.toTarget(Direction.SOUTH).normal
                        return BlockPos(
                            x.x * v.x + y.x * v.y + z.x * v.z,
                            x.y * v.x + y.y * v.y + z.y * v.z,
                            x.z * v.x + y.z * v.y + z.z * v.z,
                        )
                    }
                    reference = target.offset(mapped(reference.subtract(source).subtract(relation.offset)))
                    basis = basis.map(relation::toTarget)
                }
            }
            helper.assertTrue(
                transitions >= 6 && reference == initial && basis == listOf(Direction.EAST, Direction.UP, Direction.SOUTH),
                "Closed coordinate transform changed the reference point or axes: $reference $basis transitions=$transitions",
            )
        }

        fun reload() {
            val before = delegate.bodies.map { it.uniqueId }
            val addresses = (ring + extra).associateWith(::address)
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            container.holdingChunkMap.saveAll()
            try {
                for (body in delegate.bodies.toList()) {
                    if (body.isRemoved) continue
                    container.holdingChunkMap.moveToUnloaded(
                        body,
                        ChunkPos(BlockPos.containing(PlanetLabCoordinates.CENTRE)),
                    )
                }
            } finally {
                // Drain the native ready queue as a group. Calling loadHoldingSubLevel directly
                // leaves the holding chunk's ready entries queued for a second load next tick.
                container.holdingChunkMap.processChanges()
            }
            helper.assertTrue(delegate.bodies.map { it.uniqueId } == before, "Native reload replaced chart identities")
            helper.assertTrue(addresses.all { (p, native) -> address(p) == native }, "Native reload changed persisted node addresses")
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "create_closed_rotation", timeoutTicks = 500)
    fun nativeRotationClosesThroughSixFaces(helper: GameTestHelper) {
        val motor = BlockPos(3, 12, -7)
        val drive = BlockPos(4, 12, -7)
        val branch = listOf(BlockPos(8, 12, -9)) + (9..14).map { BlockPos(it, 12, -9) }
        val scene = Scene(helper, listOf(motor, drive) + branch)
        val level = scene.level

        fun axis(p: BlockPos): Direction.Axis =
            if (p in corners) {
                val incoming = direction(previous(p).subtract(p)).axis
                val outgoing = direction(next(p).subtract(p)).axis
                val world = Direction.Axis.entries.single { it != incoming && it != outgoing }
                local(p, Direction.get(Direction.AxisDirection.POSITIVE, world)).axis
            } else {
                local(p, direction(next(p).subtract(p))).axis
            }

        fun state(p: BlockPos): BlockState {
            val name =
                if (p in corners) {
                    "gearbox"
                } else if (p == BlockPos(4, 12, -8) || p == BlockPos(8, 12, -8)) {
                    "cogwheel"
                } else {
                    "shaft"
                }
            return block(name).setValue(BlockStateProperties.AXIS, axis(p))
        }
        for (p in ring) scene.put(p, state(p))
        scene.put(drive, block("cogwheel").setValue(BlockStateProperties.AXIS, local(drive, Direction.EAST).axis))
        for ((i, p) in branch.withIndex()) {
            scene.put(
                p,
                block(
                    if (i ==
                        0
                    ) {
                        "cogwheel"
                    } else {
                        "shaft"
                    },
                ).setValue(BlockStateProperties.AXIS, local(p, Direction.EAST).axis),
            )
        }
        val motorState = block("creative_motor").setValue(BlockStateProperties.FACING, local(motor, Direction.EAST))
        scene.put(motor, motorState)
        scene.topology()

        fun body(p: BlockPos) = level.getBlockEntity(scene.address(p)) as KineticBlockEntity

        fun speed(value: Int) {
            (body(motor) as CreativeMotorBlockEntity).apply {
                generatedSpeed.setValue(value)
                updateGeneratedRotation()
            }
        }
        val all = ring + branch + listOf(drive, motor)

        fun checkRunning(
            value: Int,
            missing: BlockPos? = null,
        ) {
            val source = body(motor)
            helper.assertTrue(source.speed == value.toFloat(), "Native motor did not generate requested speed")
            for (p in all.filter { it != missing }) {
                val be = body(p)
                helper.assertTrue(
                    be.speed != 0f && be.network == source.network,
                    "Disconnected native loop node $p/${face(p)} speed=${be.speed} network=${be.network} source=${be.source}",
                )
                if (p in ring && p !in corners) {
                    val d = local(p, direction(next(p).subtract(p)))
                    val expected = -value * if (d.axisDirection == Direction.AxisDirection.POSITIVE) 1 else -1
                    helper.assertTrue(
                        be.speed == expected.toFloat(),
                        "Loop shaft has wrong signed speed at $p: ${be.speed}, expected $expected",
                    )
                }
            }
            val members = source.orCreateNetwork.members.keys
            helper.assertTrue(
                members.size == all.size - (
                    if (missing ==
                        null
                    ) {
                        0
                    } else {
                        1
                    }
                ) && members.all { !it.isRemoved },
                "Native loop retains duplicate or removed members: ${members.size}",
            )
        }
        val cut = BlockPos(12, 4, -8)
        helper.runAfterDelay(15) {
            speed(32)
            helper.runAfterDelay(30) {
                checkRunning(32)
                speed(-32)
                helper.runAfterDelay(25) {
                    checkRunning(-32)
                    scene.remove(cut)
                    helper.runAfterDelay(25) {
                        checkRunning(-32, cut)
                        scene.put(cut, state(cut))
                        helper.runAfterDelay(25) {
                            scene.topology()
                            checkRunning(-32)
                            scene.remove(motor)
                            helper.runAfterDelay(30) {
                                for (p in all.filter { it != motor }) {
                                    helper.assertTrue(
                                        body(p).speed == 0f,
                                        "Source-free closed loop keeps rotating at $p",
                                    )
                                }
                                scene.put(motor, motorState)
                                helper.runAfterDelay(10) {
                                    speed(64)
                                    helper.runAfterDelay(25) {
                                        checkRunning(64)
                                        scene.reload()
                                        helper.runAfterDelay(80) {
                                            scene.topology()
                                            checkRunning(64)
                                            println(
                                                "ORBITALIS_CREATE_CLOSED_ROTATION faces=6 ringNodes=${ring.size} branch=true deepExtension=5 reciprocal=true coordinateCycle=true signedSpeed=true reversed=true alternatePath=true sourceRemoved=true reconnected=true nativeReload=true",
                                            )
                                            helper.succeed()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "create_closed_fluid", timeoutTicks = 3000)
    fun nativeFluidLoopClosesThroughSixFaces(helper: GameTestHelper) {
        // The authored circuit is longer than Create's default 16-block pressure range. Change the
        // native test configuration, not the pressure algorithm, and restore it after this scenario.
        val range = AllConfigs.server().fluids.mechanicalPumpRange
        val originalRange = range.get()
        range.set(512)
        onFinished(helper) { range.set(originalRange) }
        val source = BlockPos(4, 12, -6)
        val pump = BlockPos(4, 12, -7)
        val cog = BlockPos(5, 12, -7)
        val motor = BlockPos(5, 12, -6)
        val target = BlockPos(0, -15, 14)
        val inlet = BlockPos(4, 12, -8)
        val outlet = BlockPos(0, -14, 14)
        val scene = Scene(helper, listOf(source, pump, cog, motor, target))
        val level = scene.level

        fun port(d: Direction) =
            when (d) {
                Direction.DOWN -> BlockStateProperties.DOWN
                Direction.UP -> BlockStateProperties.UP
                Direction.NORTH -> BlockStateProperties.NORTH
                Direction.SOUTH -> BlockStateProperties.SOUTH
                Direction.WEST -> BlockStateProperties.WEST
                Direction.EAST -> BlockStateProperties.EAST
            }

        fun pipe(p: BlockPos): BlockState {
            var state = block("fluid_pipe")
            for (d in Direction.entries) state = state.setValue(port(d), false)
            val neighbours =
                listOf(previous(p), next(p)) +
                    if (p == inlet) {
                        listOf(pump)
                    } else if (p == outlet) {
                        listOf(target)
                    } else {
                        emptyList()
                    }
            for (q in neighbours) state = state.setValue(port(local(p, direction(q.subtract(p)))), true)
            return state
        }
        for (p in ring) scene.put(p, pipe(p))
        scene.put(source, block("fluid_tank"))
        scene.put(target, block("fluid_tank"))
        scene.put(pump, block("mechanical_pump").setValue(BlockStateProperties.FACING, local(pump, Direction.NORTH)))
        scene.put(cog, block("cogwheel").setValue(BlockStateProperties.AXIS, local(cog, Direction.NORTH).axis))
        scene.put(motor, block("creative_motor").setValue(BlockStateProperties.FACING, local(motor, Direction.NORTH)))
        // These are native authored connection states. Interactive cross-chart placement/rendering
        // remains separate evidence; no proxy pipe or replacement fluid graph is used here.
        for (p in ring) scene.put(p, pipe(p))
        scene.topology()

        fun tank(p: BlockPos): IFluidHandler =
            checkNotNull(level.getCapability(Capabilities.FluidHandler.BLOCK, scene.address(p), Direction.UP))

        fun drive(value: Int) {
            (level.getBlockEntity(scene.address(motor)) as CreativeMotorBlockEntity).apply {
                generatedSpeed.setValue(value)
                updateGeneratedRotation()
            }
        }

        fun total(): Int = tank(source).getFluidInTank(0).amount + tank(target).getFluidInTank(0).amount

        val pressuredFaces = mutableSetOf<CubicFace>()

        fun transferred(stage: String) {
            for (f in CubicFace.entries) {
                if (ring.filter { face(it) == f }.any {
                        FluidPropagator.getPipe(level, scene.address(it))?.hasAnyPressure() == true
                    }
                ) {
                    pressuredFaces.add(f)
                }
            }
            helper.assertTrue(total() == 4000, "Fluid loop lost/duplicated water stage=$stage total=${total()}")
            helper.assertTrue(tank(target).getFluidInTank(0).amount > 0, "No fluid through closed/remaining route stage=$stage")
        }

        fun refill() {
            val returned = tank(target).drain(4000, IFluidHandler.FluidAction.EXECUTE)
            helper.assertTrue(
                tank(source).fill(returned, IFluidHandler.FluidAction.EXECUTE) == returned.amount,
                "Native tank reset lost fluid",
            )
            helper.assertTrue(total() == 4000, "Native tank reset changed total")
        }
        val cutEast = BlockPos(12, 4, -8)
        val cutWest = BlockPos(-16, 4, 14)
        var filled = false
        helper.onEachTick {
            if (filled) helper.assertTrue(total() == 4000, "Fluid conservation failed between stages total=${total()}")
        }
        helper.runAfterDelay(15) {
            for (p in ring) {
                val actual = level.getBlockState(scene.address(p))
                helper.assertTrue(
                    actual == pipe(p),
                    "Pipe state changed before pressure at $p/${face(p)} expected=${pipe(p)} actual=$actual",
                )
            }
            helper.assertTrue(
                tank(source).fill(FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000,
                "Could not fill source tank",
            )
            drive(128)
            filled = true
            helper.runAfterDelay(400) {
                transferred("closed")
                drive(0)
                helper.runAfterDelay(25) {
                    refill()
                    scene.put(cutEast, Blocks.STONE.defaultBlockState())
                    drive(128)
                    helper.runAfterDelay(400) {
                        transferred("north-west-route")
                        drive(0)
                        helper.runAfterDelay(25) {
                            refill()
                            scene.put(cutEast, pipe(cutEast))
                            scene.put(cutWest, Blocks.STONE.defaultBlockState())
                            drive(128)
                            helper.runAfterDelay(400) {
                                transferred("east-down-route")
                                helper.assertTrue(
                                    pressuredFaces.size == 6,
                                    "Alternate native routes did not cover all six chart frames: $pressuredFaces",
                                )
                                drive(0)
                                helper.runAfterDelay(25) {
                                    refill()
                                    scene.put(cutEast, Blocks.STONE.defaultBlockState())
                                    drive(128)
                                    helper.runAfterDelay(180) {
                                        helper.assertTrue(
                                            total() == 4000 && tank(target).getFluidInTank(0).isEmpty,
                                            "Disconnected sink still received fluid",
                                        )
                                        scene.put(cutEast, pipe(cutEast))
                                        scene.put(cutWest, pipe(cutWest))
                                        helper.runAfterDelay(400) {
                                            transferred("reconnected")
                                            drive(0)
                                            helper.runAfterDelay(25) {
                                                refill()
                                                drive(128)
                                                scene.reload()
                                                helper.runAfterDelay(400) {
                                                    scene.topology()
                                                    transferred("native-reload")
                                                    println(
                                                        "ORBITALIS_CREATE_CLOSED_FLUID faces=6 ringNodes=${ring.size} branches=2 deepExtension=5 nativePumpRange=512 nativePressure=true conserved=true alternatePaths=2 isolatedSink=true reconnected=true nativeReload=true",
                                                    )
                                                    helper.succeed()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
