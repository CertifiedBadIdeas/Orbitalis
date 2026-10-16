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

import com.simibubi.create.content.fluids.pump.PumpBlockEntity
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.CubicFace
import org.cbi.orbitalis.core.planet.CubicPlanetVolume
import org.cbi.orbitalis.core.planet.PlanetCell
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetChunks
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetNeighbours
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours

@PrefixGameTestTemplate(false)
object CreateSeamGameTests {
    private fun block(name: String) =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .get(
                net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath("create", name),
            ).defaultBlockState()

    private fun scene(helper: GameTestHelper): FaceFrameScene {
        val scene = FaceFrameScene(checkNotNull(helper.level.server.getLevel(PlanetLabCoordinates.DIMENSION)))
        helper.onEachTick { scene.level.resetEmptyTime() }
        scene.prepare()
        PlanetNeighbours.enable(scene.level, true)
        onFinished(helper) {
            PlanetNeighbours.enable(scene.level, false)
            scene.close()
        }
        return scene
    }

    private fun native(
        scene: FaceFrameScene,
        face: Direction,
        x: Int,
        y: Int,
        z: Int,
    ): BlockPos = SablePlanetNeighbours.nativePosition(scene.planet.body(face), PlanetCell(x, y, z))

    private fun materialize(
        scene: FaceFrameScene,
        cells: List<PlanetCell>,
        excavate: Boolean = false,
    ) {
        for (face in Direction.entries) {
            val body = scene.planet.body(face)
            for (cell in cells) {
                val position = SablePlanetNeighbours.nativePosition(body, cell)
                val global = ChunkPos(position)
                if (body.plot.getChunkHolder(body.plot.toLocal(global)) == null) body.plot.newEmptyChunk(global)
                if (excavate) scene.level.removeBlock(position, false)
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "create_shaft_seam", timeoutTicks = 180)
    fun nativeRotationCrossesFaceSeam(helper: GameTestHelper) {
        val scene = scene(helper)
        val level = scene.level

        data class Drive(
            val source: BlockPos,
            val target: BlockPos,
            val facing: Direction,
            val axis: Direction.Axis,
            val sign: Int,
            val targetBlock: String = "shaft",
        )
        val cases =
            mutableListOf(
                Drive(native(scene, Direction.UP, -4, 15, 3), native(scene, Direction.UP, -3, 15, 3), Direction.EAST, Direction.Axis.X, 1),
            )
        val volume = CubicPlanetVolume(16)
        for (a in CubicFace.entries) {
            for (b in CubicFace.entries) {
                if (a.outward.dot(b.outward) != 0.0) continue
                val third = a.outward.cross(b.outward)
                val center = a.outward * 14.5 + b.outward * (if (a.ordinal < b.ordinal) 14.5 else 13.5) + third * (-9.5 + 3 * a.ordinal)

                fun cell(v: org.cbi.orbitalis.core.planet.Vector3) =
                    PlanetCell(kotlin.math.floor(v.x).toInt(), kotlin.math.floor(v.y).toInt(), kotlin.math.floor(v.z).toInt())
                val sourceCell = cell(center)
                val targetCell = cell(center + b.outward)
                check(volume.owner(sourceCell) == a && volume.owner(targetCell) == b)
                val source = SablePlanetNeighbours.nativePosition(scene.planet.body(Direction.valueOf(a.name)), sourceCell)
                val target = SablePlanetNeighbours.nativePosition(scene.planet.body(Direction.valueOf(b.name)), targetCell)
                val direction =
                    checkNotNull(
                        Direction.fromDelta(
                            b.outward.dot(a.right).toInt(),
                            b.outward.dot(a.outward).toInt(),
                            b.outward.dot(a.forward).toInt(),
                        ),
                    )
                val relation = checkNotNull(PlanetNeighbours.relation(level, source, target))
                val mapped = relation.toTarget(direction)
                cases += Drive(source, target, direction, mapped.axis, if (direction.axisDirection == mapped.axisDirection) 1 else -1)
            }
        }
        // UP storage continues five blocks into the geometric EAST sector before meeting an EAST shaft.
        materialize(scene, (9..15).map { PlanetCell(it, 9, -8) }, true)
        for (x in 10..14) {
            level.setBlock(
                native(scene, Direction.UP, x, 9, -8),
                block("shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                3,
            )
        }
        cases += Drive(native(scene, Direction.UP, 9, 9, -8), native(scene, Direction.EAST, 15, 9, -8), Direction.EAST, Direction.Axis.Y, 1)
        // An authored connection away from the physical cube edge, above both faces.
        materialize(scene, (18..22).map { PlanetCell(it, 20, -6) })
        for (x in 19..21) {
            level.setBlock(
                native(scene, Direction.UP, x, 20, -6),
                block("shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                3,
            )
        }
        cases +=
            Drive(native(scene, Direction.UP, 18, 20, -6), native(scene, Direction.EAST, 22, 20, -6), Direction.EAST, Direction.Axis.Y, 1)
        level.setBlock(native(scene, Direction.UP, 14, 14, 4), block("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3)
        cases +=
            Drive(
                native(scene, Direction.UP, 14, 14, 3),
                native(scene, Direction.EAST, 15, 14, 4),
                Direction.SOUTH,
                Direction.Axis.Z,
                -1,
                "cogwheel",
            )
        level.setBlock(native(scene, Direction.EAST, 15, 14, 8), block("gearbox").setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3)
        cases +=
            Drive(native(scene, Direction.UP, 14, 14, 8), native(scene, Direction.EAST, 15, 13, 8), Direction.EAST, Direction.Axis.X, 1)
        check(cases.flatMap { listOf(it.source, it.target) }.distinct().size == cases.size * 2)
        for (c in cases) {
            level.setBlock(c.target, block(c.targetBlock).setValue(BlockStateProperties.AXIS, c.axis), 3)
            level.setBlock(c.source, block("creative_motor").setValue(BlockStateProperties.FACING, c.facing), 3)
        }

        fun drive(speed: Int) {
            for (c in cases) {
                val motor = level.getBlockEntity(c.source) as CreativeMotorBlockEntity
                motor.generatedSpeed.setValue(speed)
                motor.updateGeneratedRotation()
            }
        }

        fun checkSpeed(speed: Int) {
            for ((index, c) in cases.withIndex()) {
                val motor = level.getBlockEntity(c.source) as KineticBlockEntity
                val shaft = level.getBlockEntity(c.target) as KineticBlockEntity
                helper.assertTrue(
                    motor.speed == KineticBlockEntity.convertToDirection(speed.toFloat(), c.facing) && shaft.speed == motor.speed * c.sign,
                    "Native shaft failed case=$index expected=$speed sign=${c.sign} motor=${motor.speed} shaft=${shaft.speed} source=${shaft.source}",
                )
                helper.assertTrue(motor.network == shaft.network, "Shaft has a separate native network case=$index")
            }
        }
        helper.runAfterDelay(10) {
            drive(32)
            helper.runAfterDelay(20) {
                checkSpeed(32)
                drive(-32)
                helper.runAfterDelay(20) {
                    checkSpeed(-32)
                    for (c in cases) level.removeBlock(c.source, false)
                    helper.runAfterDelay(20) {
                        for (c in cases) {
                            helper.assertTrue(
                                (level.getBlockEntity(c.target) as KineticBlockEntity).speed == 0f,
                                "Disconnected shaft retained rotation",
                            )
                        }
                        for (c in cases) {
                            level.setBlock(
                                c.source,
                                block("creative_motor").setValue(BlockStateProperties.FACING, c.facing),
                                3,
                            )
                        }
                        helper.runAfterDelay(10) {
                            drive(32)
                            helper.runAfterDelay(20) {
                                checkSpeed(32)
                                println(
                                    "ORBITALIS_CREATE_ROTATION directedSeams=24 deepExtension=5 smallCogs=true gearbox=true awayFromEdge=true sameFace=true signs=true reversed=true reconnected=true nativeNetwork=true",
                                )
                                helper.succeed()
                            }
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "create_fluid_seam", timeoutTicks = 600)
    fun nativePumpMovesFluidAcrossFaceSeam(helper: GameTestHelper) {
        val scene = scene(helper)
        val level = scene.level

        data class Circuit(
            val source: BlockPos,
            val pump: BlockPos,
            val first: BlockPos,
            val second: BlockPos,
            val target: BlockPos,
            val secondAxis: Direction.Axis,
        )
        val circuits =
            listOf(
                Circuit(
                    native(scene, Direction.UP, -4, 15, 3),
                    native(scene, Direction.UP, -3, 15, 3),
                    native(scene, Direction.UP, -2, 15, 3),
                    native(scene, Direction.UP, -1, 15, 3),
                    native(scene, Direction.UP, 0, 15, 3),
                    Direction.Axis.X,
                ),
                Circuit(
                    native(scene, Direction.UP, 12, 14, -6),
                    native(scene, Direction.UP, 13, 14, -6),
                    native(scene, Direction.UP, 14, 14, -6),
                    native(scene, Direction.EAST, 15, 14, -6),
                    native(scene, Direction.EAST, 16, 14, -6),
                    Direction.Axis.Y,
                ),
                Circuit(
                    native(scene, Direction.UP, 18, 20, 6),
                    native(scene, Direction.UP, 19, 20, 6),
                    native(scene, Direction.UP, 20, 20, 6),
                    native(scene, Direction.EAST, 21, 20, 6),
                    native(scene, Direction.EAST, 22, 20, 6),
                    Direction.Axis.Y,
                ),
                Circuit(
                    native(scene, Direction.UP, 12, 8, 6),
                    native(scene, Direction.UP, 13, 8, 6),
                    native(scene, Direction.UP, 14, 8, 6),
                    native(scene, Direction.EAST, 15, 8, 6),
                    native(scene, Direction.EAST, 16, 8, 6),
                    Direction.Axis.Y,
                ),
            )
        materialize(scene, (18..22).map { PlanetCell(it, 20, 6) })
        materialize(scene, (12..16).flatMap { x -> listOf(PlanetCell(x, 8, 6), PlanetCell(x, 8, 7)) }, true)
        for (c in circuits) {
            level.setBlock(c.source, block("fluid_tank"), 3)
            level.setBlock(c.target, block("fluid_tank"), 3)
            level.setBlock(c.first, block("glass_fluid_pipe").setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3)
            level.setBlock(c.second, block("glass_fluid_pipe").setValue(BlockStateProperties.AXIS, c.secondAxis), 3)
            level.setBlock(c.pump, block("mechanical_pump").setValue(BlockStateProperties.FACING, Direction.EAST), 3)
            level.setBlock(c.pump.south(), block("cogwheel").setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3)
            level.setBlock(c.pump.south().west(), block("creative_motor").setValue(BlockStateProperties.FACING, Direction.EAST), 3)
        }

        fun tank(p: BlockPos): IFluidHandler = checkNotNull(level.getCapability(Capabilities.FluidHandler.BLOCK, p, Direction.UP))
        helper.runAfterDelay(10) {
            for (c in circuits) {
                helper.assertTrue(
                    tank(c.source).fill(FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE) == 4000,
                    "Source tank fill failed",
                )
                val motor = level.getBlockEntity(c.pump.south().west()) as CreativeMotorBlockEntity
                motor.generatedSpeed.setValue(64)
                motor.updateGeneratedRotation()
            }

            fun checkConserved(): List<Int> =
                circuits.mapIndexed { index, c ->
                    val source = tank(c.source).getFluidInTank(0).amount
                    val target = tank(c.target).getFluidInTank(0).amount
                    helper.assertTrue(source + target == 4000, "Fluid loss case=$index source=$source target=$target")
                    target
                }
            helper.runAfterDelay(100) {
                val forward = checkConserved()
                for ((i, amount) in forward.withIndex()) {
                    helper.assertTrue(
                        amount > 0,
                        "Native pump did not transfer fluid case=$i source=${tank(
                            circuits[i].source,
                        ).getFluidInTank(0).amount} pump=${(level.getBlockEntity(circuits[i].pump) as PumpBlockEntity).speed}",
                    )
                }
                for (c in circuits) {
                    level.setBlock(
                        c.pump,
                        block("mechanical_pump").setValue(BlockStateProperties.FACING, Direction.WEST),
                        3,
                    )
                }
                helper.runAfterDelay(100) {
                    val reverse = checkConserved()
                    for (i in reverse.indices) {
                        helper.assertTrue(
                            reverse[i] < forward[i],
                            "Reversed native pump did not return fluid case=$i",
                        )
                    }
                    for (c in circuits) {
                        // Close the broken port with stone so this checks disconnection, not native open-end spilling.
                        level.setBlock(
                            c.second,
                            net.minecraft.world.level.block.Blocks.STONE
                                .defaultBlockState(),
                            3,
                        )
                        level.setBlock(c.pump, block("mechanical_pump").setValue(BlockStateProperties.FACING, Direction.EAST), 3)
                    }
                    helper.runAfterDelay(30) {
                        val disconnected = checkConserved()
                        helper.runAfterDelay(40) {
                            helper.assertTrue(checkConserved() == disconnected, "Blocked pipe continued transferring")
                            for (c in circuits) {
                                level.setBlock(
                                    c.second,
                                    block("glass_fluid_pipe").setValue(BlockStateProperties.AXIS, c.secondAxis),
                                    3,
                                )
                            }
                            helper.runAfterDelay(100) {
                                val reconnected = checkConserved()
                                for (i in reverse.indices) {
                                    helper.assertTrue(
                                        reconnected[i] > disconnected[i],
                                        "Reconnected pipe did not transfer case=$i",
                                    )
                                }
                                val c = circuits[1]
                                val body = scene.planet.body(Direction.EAST)
                                val key = body.plot.toLocal(ChunkPos(c.second))
                                val beforeSource = tank(c.source).getFluidInTank(0).amount
                                val beforeTarget = tank(c.target).getFluidInTank(0).amount
                                level.setBlock(c.pump, block("mechanical_pump").setValue(BlockStateProperties.FACING, Direction.WEST), 3)
                                helper.assertTrue(SablePlanetChunks.evict(body, key), "Could not evict Create pipe/tank column")
                                helper.runAfterDelay(30) {
                                    try {
                                        helper.assertTrue(
                                            tank(c.source).getFluidInTank(0).amount == beforeSource,
                                            "Cold endpoint still supplied fluid",
                                        )
                                    } finally {
                                        helper.assertTrue(SablePlanetChunks.restore(body, key), "Could not restore Create pipe/tank column")
                                    }
                                    helper.assertTrue(
                                        tank(c.target).getFluidInTank(0).amount == beforeTarget,
                                        "Cold native tank inventory changed",
                                    )
                                    helper.runAfterDelay(100) {
                                        checkConserved()
                                        helper.assertTrue(
                                            tank(c.source).getFluidInTank(0).amount > beforeSource,
                                            "Pipe did not reconnect after cold restore",
                                        )
                                        println(
                                            "ORBITALIS_CREATE_FLUID deepExtension=true awayFromEdge=true sameFace=true crossFace=true conserved=true reversed=true reconnected=true coldRestored=true nativePressure=true",
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
