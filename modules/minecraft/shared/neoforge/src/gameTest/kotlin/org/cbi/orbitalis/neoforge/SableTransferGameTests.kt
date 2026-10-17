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
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.TickingBlockEntity
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferJournal
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPhase
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferRecord
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferTeleportState
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferTickGate
import org.joml.Vector3d
import java.nio.file.Files
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.util.Unit as MinecraftUnit

@PrefixGameTestTemplate(false)
object SableTransferGameTests {
    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "retired_journal", timeoutTicks = 200)
    fun retiredDiagnosticJournalHandling(helper: GameTestHelper) {
        val future =
            java.util.concurrent.CompletableFuture.supplyAsync {
                val folder = Files.createTempDirectory("orbitalis-retired-journal-")
                val ship = UUID.randomUUID()
                val file = folder.resolve("$ship.nbt")
                val pilot =
                    CompoundTag().apply {
                        for (key in listOf("x", "y", "z", "vx", "vy", "vz", "yaw", "pitch")) putDouble(key, 0.0)
                    }
                val receipt =
                    CompoundTag().apply {
                        putUUID("uuid", ship)
                        put("pose", CompoundTag())
                    }
                val active =
                    ShipTransferRecord(
                        UUID.randomUUID(),
                        ship,
                        UUID.randomUUID(),
                        false,
                        receipt,
                        receipt,
                        pilot,
                        pilot,
                        CompoundTag(),
                        ShipTransferPhase.DONE,
                    )
                ShipTransferJournal(folder).use { journal ->
                    journal.write(active).join()
                    check(
                        journal
                            .read()
                            .join()
                            .single()
                            .ship == ship,
                    ) { "Active route receipt was lost" }
                    val retiredShip = UUID.randomUUID()
                    val retiredFile = folder.resolve("$retiredShip.nbt")
                    for (version in 1..3) {
                        val retired =
                            active.encode().apply {
                                putUUID("ship", retiredShip)
                                putInt("version", version)
                                putString("surface_dimension", "orbitalis:flight_test")
                                putString("orbital_dimension", "orbitalis:space_test")
                            }
                        NbtIo.writeCompressed(retired, retiredFile)
                        val before = Files.readAllBytes(retiredFile)
                        check(
                            journal
                                .read()
                                .join()
                                .single()
                                .ship == ship,
                        ) { "Retired receipt was replayed or active receipt discarded" }
                        check(before.contentEquals(Files.readAllBytes(retiredFile))) { "Retired receipt was overwritten" }
                        for (phase in listOf("PREPARED", "COMMITTED", "DONE")) {
                            val pending =
                                retired.copy().apply {
                                    putString("phase", phase)
                                    putBoolean("pilot_pending", true)
                                }
                            NbtIo.writeCompressed(pending, retiredFile)
                            val failure = runCatching { journal.read().join() }.exceptionOrNull()
                            check(
                                failure?.cause?.message?.contains("Unfinished retired diagnostic") == true,
                            ) { "Retired authority did not fail explicitly" }
                            check(Files.exists(retiredFile)) { "Retired authority was deleted" }
                        }
                    }
                    Files.delete(retiredFile)
                    val unknownLegacy =
                        active.encode().apply {
                            putInt("version", 2)
                            putString("orbital_dimension", "other:unknown")
                        }
                    NbtIo.writeCompressed(unknownLegacy, file)
                    check(runCatching { journal.read().join() }.isFailure) { "Unknown legacy route was ignored" }
                    val unknown = active.encode().apply { putString("surface_dimension", "minecraft:the_nether") }
                    NbtIo.writeCompressed(unknown, file)
                    check(runCatching { journal.read().join() }.isFailure) { "Unknown route was reinterpreted" }
                }
            }

        fun await() {
            if (!future.isDone) {
                helper.runAfterDelay(1) { await() }
                return
            }
            future.join()
            helper.succeed()
        }
        helper.runAfterDelay(1) { await() }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", batch = "flat_transfer", timeoutTicks = 600)
    fun overworldTransferRoundTripAndRecovery(helper: GameTestHelper) = transferScenario(helper, FlatPlanetSpace.SPACE, Level.OVERWORLD)

    private fun transferScenario(
        helper: GameTestHelper,
        route: ResourceKey<Level>,
        surfaceKey: ResourceKey<Level> = Level.OVERWORLD,
    ) {
        val server = helper.level.server
        val surface = checkNotNull(server.getLevel(surfaceKey))
        val orbit = checkNotNull(server.getLevel(route))
        val delta = 4096.0
        // The inert fake pilot is not in the player list; keep vanilla BE ticking active.
        surface.resetEmptyTime()
        orbit.resetEmptyTime()
        val surfaceContainer = SubLevelContainer.getContainer(surface)!!
        val orbitContainer = SubLevelContainer.getContainer(orbit)!!
        val folder = Files.createTempDirectory("orbitalis-transfer-gametest-")
        // Model delayed client loading explicitly; this fixture has no logged-in game client.
        val pilotSync =
            object : ShipTransferPilotSync {
                var lastBody: ServerSubLevel? = null
                var trackingTicks = 0
                var contactTicks = 0

                override fun hasShip(
                    player: ServerPlayer,
                    body: ServerSubLevel,
                ): Boolean {
                    if (lastBody !== body) {
                        lastBody = body
                        trackingTicks = 0
                        contactTicks = 0
                    }
                    helper.assertTrue(SubLevelPhysicsSystem.require(body.level).paused, "Ship resumed before client tracking")

                    helper.assertTrue(
                        (player.connection as ShipTransferTeleportState).orbitalisHasPendingTeleport(),
                        "Dimension travel did not request a vanilla teleport acknowledgment",
                    )
                    return ++trackingTicks > 3
                }

                override fun isReady(
                    player: ServerPlayer,
                    body: ServerSubLevel,
                ): Boolean {
                    helper.assertTrue(SubLevelPhysicsSystem.require(body.level).paused, "Ship resumed before teleport acknowledgment")
                    // Even an early plot-space movement report cannot release a pending teleport.
                    (player as EntityMovementExtension).`sable$setTrackingSubLevel`(body)
                    helper.assertTrue(!ShipTransferPilotSync.NATIVE.isReady(player, body), "Unacknowledged pilot was ready")
                    if (++contactTicks <= 3) return false
                    GameTestPlayers.acknowledgeTeleport(player)
                    helper.assertTrue(ShipTransferPilotSync.NATIVE.isReady(player, body), "Acknowledged attached pilot was not ready")
                    return true
                }
            }
        var manager = SableShipTransfer(server, folder, pilotSync)
        val player = GameTestPlayers.create(helper)
        val pose =
            Pose3d().also {
                val anchor = 500.0
                it.position().set(anchor, 600.0, anchor)
                it.orientation().rotateZ(0.1).rotateX(0.05)
            }
        val unavailable = surfaceContainer.occupancy.clone() as BitSet
        unavailable.or(orbitContainer.occupancy)
        val sourceSide = 1 shl surfaceContainer.logSideLength
        // Start in another row so the outbound relocation exercises both local X and Z.
        val sourceAddress = unavailable.nextClearBit(sourceSide)
        val source =
            surfaceContainer.allocateSubLevel(UUID.randomUUID(), sourceAddress % sourceSide, sourceAddress / sourceSide, pose)
                as ServerSubLevel
        val ship = source.uniqueId
        var sourceRemovedBeforeTravel = false
        surfaceContainer.addObserver(
            object : SubLevelObserver {
                override fun onSubLevelRemoved(
                    body: SubLevel,
                    reason: SubLevelRemovalReason,
                ) {
                    if (body === source && reason == SubLevelRemovalReason.REMOVED) {
                        helper.assertTrue(player.level() === surface, "Source removal packet followed dimension travel")
                        sourceRemovedBeforeTravel = true
                    }
                }
            },
        )
        source.plot.newEmptyChunk(source.plot.centerChunk)
        source.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.GOLD_BLOCK.defaultBlockState(), 3)
        source.plot.embeddedLevelAccessor.setBlock(BlockPos(0, 1, 0), Blocks.CHEST.defaultBlockState(), 3)

        fun chest(body: ServerSubLevel): ChestBlockEntity =
            body.plot.loadedChunks
                .flatMap {
                    it.chunk.blockEntities.values
                }.filterIsInstance<ChestBlockEntity>()
                .single()
        chest(source).setItem(0, ItemStack(Items.COBBLESTONE, 16))
        // Sable writes an unordered set of positions. Reordering must pass, while every
        // inventory/type/position change and duplicate must still fail closed.
        val chestTag = chest(source).saveWithFullMetadata(surface.registryAccess())
        val otherChest = chestTag.copy().also { it.putInt("x", it.getInt("x") + 1) }
        val entities =
            ListTag().also {
                it.add(chestTag)
                it.add(otherChest)
            }
        val reversed =
            ListTag().also {
                it.add(otherChest.copy())
                it.add(chestTag.copy())
            }
        SableShipSnapshot.verifyBlockEntities(entities, reversed)

        fun rejectedEntities(
            label: String,
            alter: (ListTag) -> Unit,
        ) {
            val changed = reversed.copy()
            alter(changed)
            helper.assertTrue(
                runCatching { SableShipSnapshot.verifyBlockEntities(entities, changed) }.isFailure,
                "Block entity comparison accepted $label",
            )
        }
        rejectedEntities("changed cargo") {
            it
                .getCompound(0)
                .getList("Items", 10)
                .getCompound(0)
                .putInt("count", 32)
        }
        rejectedEntities("changed type") { it.getCompound(0).putString("id", "minecraft:barrel") }
        rejectedEntities("changed position") { it.getCompound(0).putInt("y", 100) }
        rejectedEntities("missing block entity") { it.removeAt(0) }
        rejectedEntities("extra block entity") { it.add(otherChest.copy().also { tag -> tag.putInt("y", 100) }) }
        rejectedEntities("duplicate block entity") { it.add(otherChest.copy()) }
        rejectedEntities("connectivity flag on an unrelated block") { it.getCompound(0).putBoolean("UpdateConnectivity", true) }
        val engine =
            CompoundTag().also {
                it.putString("id", "createpropulsion:creative_thruster_block_entity")
                it.putInt("x", 0)
                it.putInt("y", 128)
                it.putInt("z", 0)
                it.putInt("currentTick", 42)
                it.putInt("Width", 1)
                it.putFloat("Thrust", 100f)
            }
        val engines = ListTag().also { it.add(engine) }
        val loadedEngines = engines.copy().also { it.getCompound(0).putBoolean("UpdateConnectivity", true) }
        SableShipSnapshot.verifyBlockEntities(engines, loadedEngines)
        SableShipSnapshot.verifyBlockEntities(loadedEngines, engines)
        for (field in listOf("currentTick", "Width", "Thrust", "UpdateConnectivity")) {
            val changed = loadedEngines.copy().also { it.getCompound(0).putInt(field, 2) }
            helper.assertTrue(
                runCatching { SableShipSnapshot.verifyBlockEntities(engines, changed) }.isFailure,
                "Block entity comparison accepted changed engine $field",
            )
        }
        var blockTicks = 0
        // A ticking payload reproduces an external engine's persisted per-tick counters without
        // importing Create/Propulsion into this runtime. Exercise the real Level ticker dispatch.
        surface.addBlockEntityTicker(
            object : TickingBlockEntity {
                override fun tick() {
                    blockTicks++
                    chest(source).persistentData.putInt("transfer_test_ticks", blockTicks)
                }

                override fun isRemoved(): Boolean = source.isRemoved

                override fun getPos(): BlockPos = chest(source).blockPos

                override fun getType(): String = "orbitalis:transfer_test_ticker"
            },
        )
        surfaceContainer.addForceLoadTicket(source, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
        var expectedAngle = pose.orientation()
        var journalWrite: CompletableFuture<Unit>? = null
        val journal = ShipTransferJournal(folder)

        fun waitUntil(
            condition: () -> Boolean,
            next: () -> Unit,
        ) {
            helper.runAfterDelay(1) {
                surface.resetEmptyTime()
                orbit.resetEmptyTime()
                manager.tick()
                helper.assertTrue(manager.error == null, "Transfer stopped: ${manager.error}")
                if (condition()) next() else waitUntil(condition, next)
            }
        }

        fun cargo(
            body: ServerSubLevel,
            count: Int = 16,
        ) {
            helper.assertTrue(
                chest(body).getItem(0).`is`(Items.COBBLESTONE) && chest(body).getItem(0).count == count,
                "Cargo changed during transfer/recovery",
            )
            helper.assertTrue(body.logicalPose().orientation().dot(expectedAngle) > 0.99999, "Orientation changed")
        }

        val foreign = mutableListOf<Pair<ServerLevel, ServerSubLevel>>()

        fun blocker(
            world: ServerLevel,
            x: Int,
            z: Int,
        ): ServerSubLevel {
            val container = checkNotNull(SubLevelContainer.getContainer(world))
            val pose = Pose3d().also { it.position().set(1804.0 + 32 * foreign.size, 1000.0, 1804.0) }
            val body = container.allocateSubLevel(UUID.randomUUID(), x, z, pose) as ServerSubLevel
            body.plot.newEmptyChunk(body.plot.centerChunk)
            body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), 3)
            chest(body).setItem(0, ItemStack(Items.DIAMOND, 7))
            body.updateMergedMassData(1.0f)
            body.updateBoundingBox()
            body.forceUpdateGlobalBounds()
            body.updateLastPose()
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
            foreign.add(world to body)
            return body
        }

        fun restoreForeign(
            world: ServerLevel,
            old: ServerSubLevel,
        ): ServerSubLevel {
            val container = checkNotNull(SubLevelContainer.getContainer(world))
            if (container.getSubLevel(old.uniqueId) == null) {
                val map = container.holdingChunkMap
                val holding = map.getHoldingSubLevel(old.uniqueId)
                if (holding != null) {
                    map.loadHoldingSubLevel(holding)
                } else {
                    val anchor = ChunkPos(BlockPos.containing(old.logicalPose().position().x, 0.0, old.logicalPose().position().z))
                    for (pointer in checkNotNull(map.storage.attemptLoadHoldingChunk(anchor)).subLevelPointers.toList()) {
                        if (map.storage.attemptLoadSubLevel(anchor, pointer)?.uuid() == old.uniqueId) {
                            map.snatchAndLoad(
                                GlobalSavedSubLevelPointer(anchor, pointer.storageIndex(), pointer.subLevelIndex()),
                                old.uniqueId,
                            )
                        }
                    }
                }
            }
            return container.getSubLevel(old.uniqueId) as ServerSubLevel
        }

        fun cleanup() {
            for ((world, old) in foreign) {
                val container = checkNotNull(SubLevelContainer.getContainer(world))
                runCatching { restoreForeign(world, old) }.getOrNull()?.let { body ->
                    container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                }
            }
            for (container in listOf(surfaceContainer, orbitContainer)) {
                (container.getSubLevel(ship) as? ServerSubLevel)?.let { body ->
                    container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                }
            }
            manager.close()
            journal.close()
            player.discard()
        }
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testPassed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) {}

                override fun testFailed(
                    test: GameTestInfo,
                    runner: GameTestRunner,
                ) {
                    System.err.println("Ship transfer GameTest failed: ${test.error}")
                    runCatching { cleanup() }.onFailure { it.printStackTrace() }
                    for (key in listOf(
                        Level.OVERWORLD,
                        FlatPlanetSpace.SPACE,
                    )) {
                        SubLevelPhysicsSystem.require(checkNotNull(server.getLevel(key))).paused = false
                    }
                }

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}
            },
        )
        helper.runAfterDelay(5) {
            waitUntil({ !manager.busy && blockTicks > 0 }) {
                val handle = SubLevelPhysicsSystem.require(surface).getPhysicsHandle(source)
                SubLevelPhysicsSystem.require(surface).pipeline.resetVelocity(source)
                handle.addLinearAndAngularVelocity(Vector3d(0.03, 0.04, -0.02), Vector3d(0.002, -0.003, 0.001))
                SubLevelPhysicsSystem.require(surface).pipeline.prePhysicsTicks()
                expectedAngle = org.joml.Quaterniond(source.logicalPose().orientation())
                val p = source.logicalPose().position()
                check(player.teleportTo(surface, p.x, p.y + 2.0, p.z, emptySet(), 32f, 12f))
                val startY = player.y
                val before = SableShipSnapshot.capture(source)
                val expectedChest =
                    source
                        .logicalPose()
                        .transformPosition(
                            Vector3d(
                                chest(source).blockPos.x.toDouble(),
                                chest(source).blockPos.y.toDouble(),
                                chest(source).blockPos.z.toDouble(),
                            ),
                        ).add(0.0, delta, 0.0)
                val plot = before.getCompound("plot")
                val occupied = blocker(orbit, plot.getInt("plot_x"), plot.getInt("plot_z"))
                val firstFree = orbitContainer.occupancy.nextClearBit(0)
                val side = 1 shl orbitContainer.logSideLength
                val unloaded = blocker(orbit, firstFree % side, firstFree / side)
                orbitContainer.removeForceLoadTicket(unloaded, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
                orbitContainer.holdingChunkMap.moveToUnloaded(
                    unloaded,
                    ChunkPos(BlockPos.containing(unloaded.logicalPose().position().x, 0.0, unloaded.logicalPose().position().z)),
                )
                helper.assertTrue(orbitContainer.getSubLevel(unloaded.uniqueId) == null, "Blocker did not unload")
                helper.assertTrue(
                    orbitContainer.getSubLevel(occupied.uniqueId) === occupied,
                    "Unloading fixture also removed loaded blocker; bounds=${occupied.boundingBox()} / ${unloaded.boundingBox()}",
                )
                val occupancy = orbitContainer.occupancy.clone() as BitSet
                try {
                    orbitContainer.occupancy.set(0, 1 shl (2 * orbitContainer.logSideLength))
                    helper.assertTrue(runCatching { manager.begin(player, source, route) }.isFailure, "Full allocation was accepted")
                    helper.assertTrue(surfaceContainer.getSubLevel(ship) === source && !manager.busy, "Full allocation changed source")
                } finally {
                    orbitContainer.occupancy.clear()
                    orbitContainer.occupancy.or(occupancy)
                }
                val ticksBeforeTransfer = blockTicks
                val tickerPosition = chest(source).blockPos
                manager.begin(player, source, route)
                helper.assertTrue(ShipTransferTickGate.blocks(surface, tickerPosition), "Source tick lease was not acquired")
                helper.assertTrue(!ShipTransferTickGate.blocks(surface, BlockPos.ZERO), "World terrain tickers were paused")
                waitUntil({ !manager.busy }) {
                    helper.assertTrue(sourceRemovedBeforeTravel, "Source tracking removal was not observed before travel")
                    helper.assertTrue(blockTicks == ticksBeforeTransfer, "Source block entities kept ticking during handoff")
                    helper.assertTrue(!ShipTransferTickGate.blocks(surface, tickerPosition), "Source tick lease leaked after completion")
                    val moved = orbitContainer.getSubLevel(ship) as? ServerSubLevel ?: error("Destination ship missing")
                    helper.assertTrue(surfaceContainer.getSubLevel(ship) == null, "Source ship remained after commit")
                    helper.assertTrue(
                        player.level() === orbit && kotlin.math.abs(player.y - (startY + delta)) < 1e-8,
                        "Pilot did not retain relative position",
                    )
                    helper.assertTrue(player.yRot == 32f && player.xRot == 12f, "Pilot view changed")
                    cargo(moved)
                    val actualChest =
                        moved.logicalPose().transformPosition(
                            Vector3d(
                                chest(moved).blockPos.x.toDouble(),
                                chest(moved).blockPos.y.toDouble(),
                                chest(moved).blockPos.z.toDouble(),
                            ),
                        )
                    helper.assertTrue(actualChest.distance(expectedChest) < 1e-6, "Plot relocation moved the physical blocks")
                    helper.assertTrue(
                        moved.plot.plotPos != occupied.plot.plotPos && moved.plot.plotPos != unloaded.plot.plotPos,
                        "Transfer reused a loaded or unloaded foreign plot",
                    )
                    helper.assertTrue(
                        orbitContainer.getSubLevel(occupied.uniqueId) === occupied,
                        "Loaded blocker was replaced; current=${orbitContainer.getSubLevel(
                            occupied.uniqueId,
                        )} removed=${occupied.isRemoved}",
                    )
                    val movedPlot = SableShipSnapshot.capture(moved).getCompound("plot")
                    if (!surfaceContainer.occupancy[surfaceContainer.getIndex(movedPlot.getInt("plot_x"), movedPlot.getInt("plot_z"))]) {
                        blocker(surface, movedPlot.getInt("plot_x"), movedPlot.getInt("plot_z"))
                    }
                    val linear = SubLevelPhysicsSystem.require(orbit).getPhysicsHandle(moved).getLinearVelocity(Vector3d())
                    helper.assertTrue(linear.distance(Vector3d(0.03, 0.04, -0.02)) < 1e-8, "Small linear velocity changed across handoff")
                    val angular = SubLevelPhysicsSystem.require(orbit).getPhysicsHandle(moved).getAngularVelocity(Vector3d())
                    helper.assertTrue(
                        angular.distance(Vector3d(0.002, -0.003, 0.001)) < 1e-5,
                        "Small angular velocity was dropped by native serialization",
                    )
                    manager.begin(player, moved)
                    waitUntil({ !manager.busy }) {
                        val returned = surfaceContainer.getSubLevel(ship) as? ServerSubLevel ?: error("Return ship missing")
                        helper.assertTrue(
                            orbitContainer.getSubLevel(ship) == null && player.level() === surface,
                            "Return duplicated ship or lost pilot",
                        )
                        cargo(returned)
                        // DONE snapshots must not rewind progress after an ordinary restart.
                        chest(returned).setItem(0, ItemStack(Items.COBBLESTONE, 32))
                        manager.close()
                        manager = SableShipTransfer(server, folder, pilotSync)
                        waitUntil({ !manager.busy }) {
                            cargo(returned, 32)
                            chest(returned).setItem(0, ItemStack(Items.COBBLESTONE, 16))
                            val snapshot = SableShipSnapshot.capture(returned)
                            val recoveryPlot = snapshot.getCompound("plot")
                            val recoveryX = recoveryPlot.getInt("plot_x")
                            val recoveryZ = recoveryPlot.getInt("plot_z")
                            if (!orbitContainer.occupancy[orbitContainer.getIndex(recoveryX, recoveryZ)]) {
                                blocker(orbit, recoveryX, recoveryZ)
                            }
                            val shifted =
                                SableShipSnapshot.forDestination(
                                    SableShipSnapshot.translated(snapshot, delta, route),
                                    surface,
                                    orbit,
                                )
                            helper.assertTrue(
                                shifted.getCompound("plot").getInt("plot_x") != recoveryX ||
                                    shifted.getCompound("plot").getInt("plot_z") != recoveryZ,
                                "Recovery fixture did not relocate the destination plot",
                            )
                            val pilotState = SableShipTransfer.playerState(player)
                            val orbitState = pilotState.copy().also { it.putDouble("y", it.getDouble("y") + delta) }
                            val record =
                                ShipTransferRecord(
                                    UUID.randomUUID(),
                                    ship,
                                    player.uuid,
                                    false,
                                    snapshot,
                                    shifted,
                                    pilotState,
                                    orbitState,
                                    CompoundTag(),
                                    ShipTransferPhase.PREPARED,
                                    orbitalDimension = route,
                                    surfaceDimension = surfaceKey,
                                )
                            // Codec coverage only: the native runtime here does not install Create/Simulated.
                            for (id in listOf("create:super_glue", "simulated:honey_glue")) {
                                val glue =
                                    CompoundTag().also {
                                        it.putString("id", id)
                                        it.putUUID("UUID", UUID.randomUUID())
                                        it.putString("opaque_payload", "preserve external glue data")
                                    }
                                val manifest = ListTag().also { it.add(glue) }
                                val withGlue =
                                    record.copy(
                                        source = snapshot.copy().also { it.put("orbitalis_attached", manifest.copy()) },
                                        destination = shifted.copy().also { it.put("orbitalis_attached", manifest.copy()) },
                                    )
                                val decoded = ShipTransferRecord.decode(withGlue.encode())
                                helper.assertTrue(
                                    decoded.source.getList("orbitalis_attached", 10) == manifest &&
                                        decoded.destination.getList("orbitalis_attached", 10) == manifest,
                                    "Journal changed $id metadata",
                                )
                            }

                            helper.assertTrue(ShipTransferRecord.decode(record.encode()).orbitalDimension == route, "Journal route changed")
                            helper.assertTrue(
                                ShipTransferRecord.decode(record.encode()).surfaceDimension == surfaceKey,
                                "Journal surface changed",
                            )
                            for (version in listOf(1, 2)) {
                                helper.assertTrue(
                                    runCatching {
                                        ShipTransferRecord.decode(
                                            record.encode().also { it.putInt("version", version) },
                                        )
                                    }.isFailure,
                                    "Retired journal was mapped onto the active route",
                                )
                            }
                            // Corrupt/unknown contracts must fail before any native mutation.
                            try {
                                ShipTransferRecord.decode(record.encode().also { it.putInt("version", 99) })
                                helper.fail("Unknown journal version accepted")
                            } catch (_: IllegalArgumentException) {
                            }
                            for (corrupt in listOf(
                                record.encode().also { it.putInt("version", 0) },
                                record.encode().also { it.remove("orbital_dimension") },
                                record.encode().also { it.remove("surface_dimension") },
                                record.encode().also { it.putString("surface_dimension", "minecraft:the_end") },
                                record.encode().also {
                                    it.putString(
                                        "surface_dimension",
                                        if (surfaceKey ==
                                            Level.OVERWORLD
                                        ) {
                                            "orbitalis:flight_test"
                                        } else {
                                            "minecraft:overworld"
                                        },
                                    )
                                },
                                record.encode().also { it.putString("orbital_dimension", "orbitalis:unknown") },
                                record.encode().also { it.getCompound("destination").putUUID("uuid", UUID.randomUUID()) },
                                record.encode().also { it.getCompound("source").getCompound("exact_linear").putDouble("x", Double.NaN) },
                                record.encode().also { it.remove("source_is_orbit") },
                                record.encode().also {
                                    val unsupported =
                                        CompoundTag().also { entity ->
                                            entity.putString("id", "minecraft:item")
                                            entity.putUUID("UUID", UUID.randomUUID())
                                        }
                                    it.getCompound("source").put("orbitalis_attached", ListTag().also { list -> list.add(unsupported) })
                                },
                            )) {
                                try {
                                    ShipTransferRecord.decode(corrupt)
                                    helper.fail("Invalid journal state accepted")
                                } catch (
                                    _: IllegalArgumentException,
                                ) {
                                }
                            }
                            journalWrite = journal.write(record)
                            waitUntil({ journalWrite!!.isDone }) {
                                journalWrite!!.join()
                                // A prepared copy is not authoritative, even if reconstruction happened.
                                SableShipSnapshot.load(orbit, shifted)
                                manager.close()
                                manager = SableShipTransfer(server, folder, pilotSync)
                                waitUntil({ !manager.busy }) {
                                    val rolledBack =
                                        surfaceContainer.getSubLevel(ship) as? ServerSubLevel ?: error("Prepared source was not restored")
                                    helper.assertTrue(orbitContainer.getSubLevel(ship) == null, "Prepared destination survived rollback")
                                    cargo(rolledBack)
                                    helper.assertTrue(
                                        SubLevelPhysicsSystem.require(surface).paused && SubLevelPhysicsSystem.require(orbit).paused,
                                        "Offline recovery did not retain its stable anchor",
                                    )
                                    manager.loggedIn(player)
                                    waitUntil({ !manager.busy }) {
                                        // Emulate a crash after native deletion but before the occupancy bitmap saved.
                                        surfaceContainer.removeSubLevel(rolledBack, SubLevelRemovalReason.REMOVED)
                                        val reserved = snapshot.getCompound("plot")
                                        surfaceContainer.occupancy.set(
                                            surfaceContainer.getIndex(reserved.getInt("plot_x"), reserved.getInt("plot_z")),
                                        )
                                        orbitContainer.occupancy.set(
                                            orbitContainer.getIndex(
                                                shifted.getCompound("plot").getInt("plot_x"),
                                                shifted.getCompound("plot").getInt("plot_z"),
                                            ),
                                        )
                                        journalWrite = journal.write(record.copy(phase = ShipTransferPhase.COMMITTED))
                                        waitUntil({ journalWrite!!.isDone }) {
                                            journalWrite!!.join()
                                            manager.close()
                                            manager = SableShipTransfer(server, folder, pilotSync)
                                            waitUntil({ !manager.busy }) {
                                                val recovered =
                                                    orbitContainer.getSubLevel(ship) as? ServerSubLevel
                                                        ?: error("Committed destination was not recovered")
                                                helper.assertTrue(
                                                    surfaceContainer.getSubLevel(ship) == null,
                                                    "Committed source remained after recovery",
                                                )
                                                cargo(recovered)
                                                manager.loggedIn(player)
                                                waitUntil({ !manager.busy }) {
                                                    helper.assertTrue(
                                                        player.level() === orbit,
                                                        "Offline pilot recovery did not follow ship authority",
                                                    )
                                                    val brokenStore =
                                                        ShipTransferJournal(Files.createTempFile("orbitalis-journal-io-failure-", ".file"))
                                                    val failedWrite = brokenStore.write(record)
                                                    waitUntil({ failedWrite.isDone }) {
                                                        helper.assertTrue(
                                                            failedWrite.isCompletedExceptionally,
                                                            "Invalid journal destination unexpectedly succeeded",
                                                        )
                                                        cargo(recovered)
                                                        brokenStore.close()
                                                        for ((world, old) in foreign) {
                                                            val body = restoreForeign(world, old)
                                                            helper.assertTrue(
                                                                chest(body).getItem(0).`is`(Items.DIAMOND) &&
                                                                    chest(body).getItem(0).count == 7,
                                                                "Foreign cargo changed",
                                                            )
                                                            helper.assertTrue(body.plot.plotPos == old.plot.plotPos, "Foreign plot moved")
                                                        }
                                                        cleanup()
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
}
