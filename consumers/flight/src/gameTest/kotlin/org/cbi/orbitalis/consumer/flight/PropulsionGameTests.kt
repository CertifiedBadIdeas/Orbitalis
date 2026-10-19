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

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.gametest.framework.GlobalTestReporter
import net.minecraft.gametest.framework.JUnitLikeTestReporter
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Unit
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.control.AutopilotFlightLoading
import org.cbi.orbitalis.minecraft.flight.control.FlightBody
import org.cbi.orbitalis.minecraft.flight.control.LaunchSites
import org.cbi.orbitalis.minecraft.integration.propulsion.AutopilotSession
import org.joml.Quaterniond
import org.joml.Vector3d
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal fun onFinished(
    helper: GameTestHelper,
    cleanup: () -> kotlin.Unit,
) {
    helper.testInfo.addListener(
        object : GameTestListener {
            override fun testStructureLoaded(test: GameTestInfo) {}

            override fun testPassed(
                test: GameTestInfo,
                runner: GameTestRunner,
            ) = cleanup()

            override fun testFailed(
                test: GameTestInfo,
                runner: GameTestRunner,
            ) {
                test.error?.printStackTrace()
                cleanup()
            }

            override fun testAddedForRerun(
                test: GameTestInfo,
                retry: GameTestInfo,
                runner: GameTestRunner,
            ) {}
        },
    )
}

@EventBusSubscriber(modid = FlightConsumerMod.ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
object PropulsionGameTests {
    @JvmStatic
    @SubscribeEvent
    fun register(event: RegisterGameTestsEvent) {
        GlobalTestReporter.replaceWith(JUnitLikeTestReporter(File(System.getProperty("orbitalis.consumerGameTestReport"))))
        val suite = System.getProperty("orbitalis.consumerGameTestSuite", "all")
        require(suite in setOf("all", "flat", "rocket", "rcs", "maneuver", "shuttle")) { "Unknown consumer GameTest suite: $suite" }
        if (suite == "shuttle") {
            event.register(ShuttleGameTests::class.java)
            return
        }
        if (suite == "rcs") {
            event.register(RocketGameTests::class.java.getDeclaredMethod("nativeRocketRcsFlip", GameTestHelper::class.java))
            return
        }
        if (suite == "maneuver") {
            event.register(ManeuverGameTests::class.java)
            return
        }
        if (suite == "rocket") {
            event.register(RocketGameTests::class.java)
            return
        }
        if (suite == "all") event.register(RocketGameTests::class.java)
        event.register(LaunchBookmarkGameTests::class.java)
        if (suite == "flat") {
            event.register(FlatMissionGameTests::class.java)
            return
        }
        event.register(ManeuverGameTests::class.java)
        event.register(PropulsionGameTests::class.java)
        event.register(ShuttleGameTests::class.java)
        event.register(FlatMissionGameTests::class.java)
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    fun flightToolsAvailableByDefault(helper: GameTestHelper) {
        val server = helper.level.server
        for (name in listOf("setup", "build", "shuttle", "setup_rocket", "build_rocket", "rocket")) {
            helper.assertTrue(
                server.functions.get(ResourceLocation.parse("orbitalis_flight_test:$name")).isPresent,
                "Built-in flight function missing or failed to parse: $name",
            )
        }
        val flight =
            server.commands.dispatcher.root
                .getChild("orbitalis")
                .getChild("flight")
        val autopilot = flight.getChild("autopilot")
        helper.assertTrue(autopilot != null, "Consumer autopilot commands missing")
        for (name in listOf("hold", "altitude", "orbit", "land", "status", "stop", "list", "execute")) {
            helper.assertTrue(autopilot.getChild(name) != null, "Consumer autopilot command missing: $name")
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 300, batch = "autopilot_commands")
    fun uncrewedAutopilotCommands(helper: GameTestHelper) {
        val server = helper.level.server
        val pilot = InertPilot(helper)
        val rigs =
            listOf(
                Rig(helper, 9000.0, dimension = org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE, y = 10000.0),
                Rig(helper, 9300.0, dimension = org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace.SPACE, y = 10000.0),
            )
        val messages = mutableListOf<String>()
        val source =
            pilot.player.createCommandSourceStack().withPermission(4).withSource(
                object : net.minecraft.commands.CommandSource {
                    override fun sendSystemMessage(message: net.minecraft.network.chat.Component) {
                        messages.add(message.string)
                    }

                    override fun acceptsSuccess() = true

                    override fun acceptsFailure() = true

                    override fun shouldInformAdmins() = false
                },
            )

        fun command(text: String) = server.commands.dispatcher.execute("orbitalis flight autopilot $text", source)
        onFinished(helper) {
            rigs.forEach { runCatching { command("stop ${it.body.uniqueId}") } }
            pilot.close()
        }
        helper.runAfterDelay(30) {
            val sites = LaunchSites.get(server)
            rigs.forEach {
                sites.put(
                    pilot.player.uuid,
                    LaunchSites.Site(
                        it.body.uniqueId,
                        org.cbi.orbitalis.core.planet
                            .Vector3(0.0, 70.0, 0.0),
                        org.joml.Quaterniond(),
                    ),
                )
            }
            val unknown = java.util.UUID.randomUUID()
            sites.put(
                pilot.player.uuid,
                LaunchSites.Site(
                    unknown,
                    org.cbi.orbitalis.core.planet
                        .Vector3(0.0, 70.0, 0.0),
                    org.joml.Quaterniond(),
                ),
            )
            val container = checkNotNull(SubLevelContainer.getContainer(rigs[0].level))
            val tickets = container.collectForceLoadTickets().values.sumOf { it.size }
            container.addForceLoadTicket(rigs[0].body, AutopilotFlightLoading.TYPE, java.util.UUID.randomUUID())
            AutopilotFlightLoading.discardOrphans(server)
            helper.assertTrue(
                container.collectForceLoadTickets().values.sumOf {
                    it.size
                } == tickets,
                "Interrupted session ticket survived cleanup or another owner's ticket was removed",
            )
            helper.assertTrue(
                command("list") >= 3 &&
                    messages.any {
                        it.contains(unknown.toString()) && it.contains("not loaded")
                    },
                "Ship directory lost loaded/remembered UUIDs",
            )
            for (rig in rigs) helper.assertTrue(command("land ${rig.body.uniqueId}") == 1, "Remote landing command failed")
            messages.clear()
            rigs.forEach { command("status ${it.body.uniqueId}") }
            helper.assertTrue(messages.count { it.contains("PLANNING") } == 2, "One ship's guidance replaced another's")
            command("stop ${rigs[0].body.uniqueId}")
            messages.clear()
            command("status ${rigs[1].body.uniqueId}")
            helper.assertTrue(messages.any { it.contains("PLANNING") }, "Addressed stop cancelled another ship")
            val beforeMissing = container.collectForceLoadTickets().values.sumOf { it.size }
            try {
                command("land $unknown")
                helper.fail("Landing silently loaded an unavailable ship")
            } catch (_: com.mojang.brigadier.exceptions.CommandSyntaxException) {
            }
            helper.assertTrue(
                container.getSubLevel(unknown) == null && container.collectForceLoadTickets().values.sumOf {
                    it.size
                } == beforeMissing,
                "Unavailable UUID lookup added native load tickets",
            )
            helper.assertTrue(beforeMissing == tickets + 1, "Active guidance did not retain exactly its remaining ship")
            container.removeForceLoadTicket(rigs[1].body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            helper.runAfterDelay(80) {
                helper.assertTrue(
                    !rigs[1].body.isRemoved && container.getSubLevel(rigs[1].body.uniqueId) === rigs[1].body,
                    "Uncrewed guided ship unloaded without fixture ticket",
                )
                messages.clear()
                command("status ${rigs[1].body.uniqueId}")
                helper.assertTrue(
                    messages.none {
                        it.contains("not engaged")
                    } && messages.any { it.contains("mission", ignoreCase = true) },
                    "Guidance stopped after distant pilot removal",
                )
                command("stop ${rigs[1].body.uniqueId}")
                helper.assertTrue(
                    container.collectForceLoadTickets().values.sumOf {
                        it.size
                    } == tickets - 1,
                    "Stopping guidance leaked its native ticket or removed another owner's ticket",
                )
                helper.succeed()
            }
        }
    }

    internal class Rig(
        helper: GameTestHelper,
        x: Double,
        faces: Array<Direction> = Direction.values(),
        dimension: ResourceKey<Level> = Level.OVERWORLD,
        y: Double = 240.0,
    ) : AutoCloseable {
        val level = checkNotNull(helper.level.server.getLevel(dimension))
        val body: ServerSubLevel
        val engines = mutableListOf<CreativeThrusterBlockEntity>()
        var session: AutopilotSession? = null

        init {
            val container = checkNotNull(SubLevelContainer.getContainer(level))
            val pose = Pose3d()
            pose.position().set(x, y, 0.0)
            body = container.allocateNewSubLevel(pose) as ServerSubLevel
            onFinished(helper, ::close)
            body.plot.newEmptyChunk(body.plot.centerChunk)
            val accessor = body.plot.embeddedLevelAccessor
            // Native connectivity must see one actual ship, not 24 disconnected engines.
            for (xb in -2..2) {
                for (yb in -2..2) {
                    for (zb in -2..2) {
                        helper.assertTrue(
                            accessor.setBlock(BlockPos(xb, yb, zb), Blocks.OAK_PLANKS.defaultBlockState(), 3),
                            "Could not place connected core",
                        )
                    }
                }
            }
            // All engines stay within one plot chunk, with unobstructed outward exhaust.
            val block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:creative_thruster"))
            helper.assertTrue(block !== Blocks.AIR, "Pinned creative engine is missing")
            for (face in faces) {
                for (a in listOf(-2, 2)) {
                    for (b in listOf(-2, 2)) {
                        val p =
                            when (face.axis) {
                                Direction.Axis.X -> BlockPos(face.stepX * 3, a, b)
                                Direction.Axis.Y -> BlockPos(a, face.stepY * 3, b)
                                Direction.Axis.Z -> BlockPos(a, b, face.stepZ * 3)
                            }
                        helper.assertTrue(
                            accessor.setBlock(p, block.defaultBlockState().setValue(AbstractThrusterBlock.FACING, face.opposite), 3),
                            "Could not place engine",
                        )
                        engines.add(accessor.getBlockEntity(p) as CreativeThrusterBlockEntity)
                    }
                }
            }
            body.updateLastPose()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        }

        fun configure() {
            // Arrange the vehicle through native configuration; never alter config during control.
            engines.clear()
            for (holder in body.plot.loadedChunks) {
                for (be in holder.chunk.blockEntities.values) {
                    if (be is CreativeThrusterBlockEntity) engines.add(be)
                }
            }
            engines.forEach { it.setThrustConfig(9) }
        }

        override fun close() {
            session?.close()
            if (!body.isRemoved) {
                val container = checkNotNull(SubLevelContainer.getContainer(level))
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 1000, batch = "propulsion_hold")
    fun nativePropulsionHoldAndCutoff(helper: GameTestHelper) {
        val rig = Rig(helper, 700.0)
        val physics = SubLevelPhysicsSystem.require(rig.level)

        class Sequence : Runnable {
            var tick = 0
            lateinit var target: Vector3
            lateinit var attitude: Quaterniond
            var peakOutput = 0.0

            override fun run() {
                rig.level.resetEmptyTime()
                tick++
                if (tick == 10) {
                    rig.configure()
                    val start = FlightBody.position(rig.body)
                    target = Vector3(start.x, start.y + 12, start.z)
                    attitude = Quaterniond(rig.body.logicalPose().orientation())
                    helper.assertTrue(rig.engines.size == 24, "Native connectivity split the engine rig")
                    println("ORBITALIS_AUTOPILOT rig mass=${rig.body.massTracker.mass} engines=${rig.engines.size}")
                    rig.session = AutopilotSession(rig.body, target)
                    // A one-time disturbance; all subsequent correction comes from real engines.
                    val handle = physics.getPhysicsHandle(rig.body)
                    handle.applyLinearImpulse(Vector3d(rig.body.massTracker.mass * 2, 0.0, 0.0))
                    handle.applyAngularImpulse(Vector3d(0.0, rig.body.massTracker.mass * 0.15, 0.0))
                }
                if (tick in 10 until 800) {
                    checkNotNull(rig.session).update()
                    for (e in rig.engines) peakOutput = max(peakOutput, e.currentThrust.toDouble())
                }
                if (tick % 100 ==
                    0
                ) {
                    println(
                        "ORBITALIS_AUTOPILOT tick=$tick ${rig.session!!.status()} v=${physics.getPhysicsHandle(
                            rig.body,
                        ).getLinearVelocity(Vector3d())} omega=${physics.getPhysicsHandle(rig.body).getAngularVelocity(Vector3d())}",
                    )
                }
                if (tick == 799) {
                    val handle = physics.getPhysicsHandle(rig.body)
                    helper.assertTrue(peakOutput > 0, "No native engine output occurred")
                    helper.assertTrue(
                        (FlightBody.position(rig.body) - target).length() < 1.0,
                        "Powered ascent/hold did not reach target: ${rig.session!!.status()}",
                    )
                    helper.assertTrue(handle.getLinearVelocity(Vector3d()).length() < 0.4, "Hold did not settle velocity")
                    helper.assertTrue(
                        handle.getAngularVelocity(Vector3d()).length() < 0.04,
                        "Native differential thrust did not settle rotation",
                    )
                    val angle = Quaterniond(rig.body.logicalPose().orientation()).conjugate().mul(attitude).angle()
                    helper.assertTrue(min(angle, PI * 2 - angle) < 0.08, "Attitude did not recover")
                    rig.session!!.close()
                    for (e in rig.engines) {
                        val saved = e.saveWithFullMetadata(rig.level.registryAccess())
                        helper.assertTrue(
                            saved.getFloat("DigitalInput") == 0.0f && saved.getInt("ControlMode") == 0,
                            "Stop left an engine latched",
                        )
                    }
                }
                if (tick == 830) {
                    helper.assertTrue(rig.engines.all { it.currentThrust == 0.0f }, "Native engine output did not fade to zero")
                    helper.succeed()
                    return
                }
                helper.runAfterDelay(1) { run() }
            }
        }
        helper.runAfterDelay(1, Sequence())
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 80, batch = "propulsion_preflight")
    fun preflightRejectsUnsafeEngines(helper: GameTestHelper) {
        val rig = Rig(helper, -700.0)
        helper.runAfterDelay(10) {
            rig.configure()
            val engine = rig.engines.first()
            engine.setRedstoneInput(15)
            val rejected =
                try {
                    rig.session = AutopilotSession(rig.body, FlightBody.position(rig.body))
                    false
                } catch (expected: IllegalArgumentException) {
                    expected.message.orEmpty().contains("inputs off")
                }
            helper.assertTrue(rejected, "Active manual input was not rejected")
            helper.assertTrue(
                rig.engines.all { it.saveWithFullMetadata(rig.level.registryAccess()).getInt("ControlMode") == 0 },
                "Preflight failure took partial engine ownership",
            )
            engine.setRedstoneInput(0)
            engine.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL)
            engine.setDigitalInput(0.75f)
            engine.loadWithComponents(engine.saveWithFullMetadata(rig.level.registryAccess()), rig.level.registryAccess())
            helper.assertTrue(
                engine.saveWithFullMetadata(rig.level.registryAccess()).getInt("ControlMode") == 0 && engine.power == 0.0f,
                "Pinned native reload retained active peripheral ownership",
            )
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 80, batch = "propulsion_authority")
    fun rejectsMissingLiftAuthority(helper: GameTestHelper) {
        val rig = Rig(helper, 600.0, arrayOf(Direction.EAST))
        helper.runAfterDelay(10) {
            rig.configure()
            val rejected =
                try {
                    rig.session = AutopilotSession(rig.body, FlightBody.position(rig.body))
                    false
                } catch (expected: IllegalArgumentException) {
                    expected.message.orEmpty().contains("cannot balance gravity")
                }
            helper.assertTrue(rejected, "A ship with only horizontal engines passed hover preflight")
            helper.assertTrue(
                rig.engines.all {
                    it.power == 0.0f &&
                        it.saveWithFullMetadata(rig.level.registryAccess()).getInt("ControlMode") == 0
                },
                "Missing authority rejection acquired an engine",
            )
            helper.succeed()
        }
    }
}
