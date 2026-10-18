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
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.util.Unit
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.storage.DimensionDataStorage
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import org.cbi.orbitalis.core.orbit.OrbitalEvolution
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.integration.sable.SableFlightGravity
import org.cbi.orbitalis.minecraft.integration.sable.SableOrbitalObjects
import org.cbi.orbitalis.minecraft.orbit.OrbitalObjectData
import org.cbi.orbitalis.minecraft.orbit.OrbitalSimulation
import org.joml.Vector3d
import java.nio.file.Files
import java.util.UUID

@PrefixGameTestTemplate(false)
object OrbitalObjectGameTests {
    private fun evolved(
        level: net.minecraft.server.level.ServerLevel,
        initial: org.cbi.orbitalis.core.planet.CartesianState,
        epoch: Long,
        ticks: Long,
    ): org.cbi.orbitalis.core.planet.CartesianState {
        val sample =
            org.cbi.orbitalis.minecraft.flight.CelestialFlight
                .sample(level.server)
        return OrbitalEvolution.advanceTimed(initial, epoch, ticks) { p, tick ->
            sample.dynamics.acceleration(
                p,
                (tick - sample.frames.tick) / 20.0,
            )
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100, batch = "orbital_clock")
    fun celestialClockAndDaylight(helper: GameTestHelper) {
        val server = helper.level.server
        val data = checkNotNull(OrbitalSimulation.data(server))
        val profile = checkNotNull(OrbitalSimulation.profile(server))
        val epoch = data.clock
        val start =
            profile.system
                .sample(epoch)
                .frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
                .origin
        val day = server.overworld().dayTime
        val gate = OrbitalSimulation.mayAdvance
        try {
            net.minecraft.server.commands.TimeCommand
                .setTime(server.createCommandSourceStack(), 18000)
            helper.assertTrue(data.clock == epoch, "Time command advanced orbital time")
            helper.assertTrue(
                profile.system
                    .sample(data.clock)
                    .frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
                    .origin == start,
                "Daylight command moved the Moon",
            )
            OrbitalSimulation.mayAdvance = { false }
            OrbitalSimulation.tick(server)
            helper.assertTrue(data.clock == epoch, "Paused native/transfer clock advanced")
        } finally {
            OrbitalSimulation.mayAdvance = gate
            server.overworld().setDayTime(day)
        }
        helper.runAfterDelay(4) {
            helper.assertTrue(
                data.clock == epoch + 4,
                "Simulation did not advance exactly once per native server tick: ${data.clock - epoch}",
            )
            helper.assertTrue(
                profile.system
                    .sample(data.clock)
                    .frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
                    .origin != start,
                "Running simulation did not move the prescribed Moon",
            )
            helper.succeed()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100, batch = "orbital_metadata")
    fun celestialProfilePersistence(helper: GameTestHelper) {
        val registries = helper.level.registryAccess()
        val old =
            CompoundTag().apply {
                putInt("Version", 1)
                putLong("Clock", 500)
                put("Objects", ListTag())
            }
        val data = OrbitalObjectData.load(old)
        val profile = data.configureCelestials(11.0)
        repeat(10) { data.advanceClock() }
        val saved = data.save(CompoundTag(), registries)
        val restored = OrbitalObjectData.load(saved)
        helper.assertTrue(saved.getInt("Version") == 2 && restored.clock == 510L, "Legacy clock did not migrate in place")
        val expected =
            profile.system
                .sample(data.clock)
                .frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
                .origin
        helper.assertTrue(
            restored.readable &&
                restored.profile!!
                    .system
                    .sample(
                        restored.clock,
                    ).frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
                    .origin == expected,
            "Profile reload changed orbital phase",
        )
        val legacyProfile =
            saved.copy().apply {
                getCompound("Celestial").apply {
                    putInt("Version", 1)
                    remove("EarthRotates")
                    remove("EarthSpinEpoch")
                }
            }
        val legacy = OrbitalObjectData.load(legacyProfile)
        helper.assertTrue(legacy.readable && legacy.profile?.earthSpinEpochTick == null, "Legacy profile acquired a spin")
        val spinningTag =
            saved.copy().apply {
                getCompound("Celestial").apply {
                    putBoolean("EarthRotates", true)
                    putLong("EarthSpinEpoch", 500)
                }
            }
        val spinning = OrbitalObjectData.load(spinningTag)
        val rotatingProfile = checkNotNull(spinning.profile)
        helper.assertTrue(rotatingProfile.earthSpinEpochTick == 500L, "Spin phase epoch was lost")
        val turning = rotatingProfile.earthSurfaceFrame(6500).orientation
        helper.assertTrue((turning.xAxis - Vector3(0.0, 1.0, 0.0)).length() < 1e-9, "Restored spin has the wrong phase")
        val spinReload = OrbitalObjectData.load(spinning.save(CompoundTag(), registries))
        helper.assertTrue(spinReload.profile == rotatingProfile, "Spin profile did not roundtrip through native NBT")
        checkSpinPackets(helper, rotatingProfile, spinning.clock)
        val folder = Files.createTempDirectory("orbitalis-celestial-clock-").toFile()
        try {
            val storage = DimensionDataStorage(folder, helper.level.server.fixerUpper, registries)
            storage.set(OrbitalObjectData.NAME, spinning)
            spinning.setDirty()
            storage.save()
            net.neoforged.neoforge.common.IOUtilities
                .waitUntilIOWorkerComplete()
            val fresh =
                DimensionDataStorage(folder, helper.level.server.fixerUpper, registries)
                    .computeIfAbsent(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME)
            helper.assertTrue(fresh.profile == rotatingProfile, "Fresh disk read changed Earth spin epoch")
            helper.assertTrue(
                fresh.clock == data.clock &&
                    fresh.profile!!
                        .system
                        .sample(fresh.clock)
                        .frame(SolarSystemProfile.MOON, SolarSystemProfile.EARTH)
                        .origin == expected,
                "Fresh disk read changed clock or celestial phase",
            )
        } finally {
            folder.deleteRecursively()
        }
        for (bad in listOf(
            saved.copy().apply { getCompound("Celestial").putInt("Version", 99) },
            saved.copy().apply { getCompound("Celestial").putDouble("Gravity", Double.NaN) },
            saved.copy().apply { getCompound("Celestial").putLong("EpochTick", 511) },
            saved.copy().apply { getCompound("Celestial").remove("MoonPhase") },
            saved.copy().apply { getCompound("Celestial").remove("EarthRotates") },
            saved.copy().apply { getCompound("Celestial").putByte("EarthRotates", 2) },
            spinningTag.copy().apply { getCompound("Celestial").remove("EarthSpinEpoch") },
            spinningTag.copy().apply { getCompound("Celestial").putLong("EarthSpinEpoch", -1) },
            spinningTag.copy().apply { getCompound("Celestial").putLong("EarthSpinEpoch", 511) },
        )) {
            val unknown = OrbitalObjectData.load(bad)
            unknown.advanceClock()
            helper.assertTrue(
                !unknown.readable && unknown.save(CompoundTag(), registries) == bad,
                "Malformed/future celestial metadata was overwritten",
            )
        }
        helper.succeed()
    }

    private fun checkSpinPackets(
        helper: GameTestHelper,
        profile: SolarSystemProfile,
        tick: Long,
    ) {
        val buffer =
            net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled
                    .buffer(),
            )
        try {
            val gravity =
                org.cbi.orbitalis.minecraft.flight
                    .PlayerGravityPayload(profile.surfaceGravity, tick, profile)
            org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                .encode(buffer, gravity)
            helper.assertTrue(
                org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                    .decode(buffer) == gravity,
                "Gravity packet lost the spinning surface phase",
            )
            buffer.readerIndex(0)
            // Profile epoch is the final long; reject future surface epochs at receipt.
            buffer.setLong(buffer.writerIndex() - 8, tick + 1)
            helper.assertTrue(
                runCatching {
                    org.cbi.orbitalis.minecraft.flight.PlayerGravityPayload.CODEC
                        .decode(buffer)
                }.isFailure,
                "Gravity packet accepted a future spin epoch",
            )
            buffer.clear()
            val map =
                org.cbi.orbitalis.minecraft.map.MapSnapshot(
                    UUID.randomUUID(),
                    tick,
                    Vector3.ZERO,
                    emptyList(),
                    gravity = profile.surfaceGravity,
                    simulationTick = tick,
                    total = 0,
                    celestialProfile = profile,
                )
            org.cbi.orbitalis.minecraft.map.MapSnapshot.CODEC
                .encode(buffer, map)
            helper.assertTrue(
                org.cbi.orbitalis.minecraft.map.MapSnapshot.CODEC
                    .decode(buffer) == map,
                "Map packet lost the spinning surface phase",
            )
            buffer.readerIndex(0)
            buffer.setLong(buffer.writerIndex() - 8, tick + 1)
            helper.assertTrue(
                runCatching {
                    org.cbi.orbitalis.minecraft.map.MapSnapshot.CODEC
                        .decode(buffer)
                }.isFailure,
                "Map packet accepted a future spin epoch",
            )
        } finally {
            buffer.release()
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 400, batch = "orbital_station")
    fun nativeUnloadedStation(helper: GameTestHelper) {
        // The headless server runs unpaced: yield to the real catch-up worker during accelerated ticks.
        helper.onEachTick {
            java.util.concurrent.locks.LockSupport
                .parkNanos(10_000_000L)
        }
        val level = checkNotNull(helper.level.server.getLevel(FlatPlanetSpace.SPACE))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val physics = SubLevelPhysicsSystem.require(level)
        val pose = Pose3d().also { it.position().set(18000.0, 14096.0, 20000.0) }
        val body = container.allocateNewSubLevel(pose) as ServerSubLevel
        val id = body.uniqueId
        var observer: ServerSubLevel? = null
        body.plot.newEmptyChunk(body.plot.centerChunk)
        body.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), 3)
        (
            body.plot.loadedChunks
                .flatMap { it.chunk.blockEntities.values }
                .filterIsInstance<ChestBlockEntity>()
                .single()
        ).setItem(0, ItemStack(Items.DIAMOND, 7))
        body.updateMergedMassData(1.0f)
        body.updateBoundingBox()
        body.forceUpdateGlobalBounds()
        body.updateLastPose()
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        helper.testInfo.addListener(
            object : GameTestListener {
                override fun testStructureLoaded(test: GameTestInfo) {}

                override fun testAddedForRerun(
                    test: GameTestInfo,
                    retry: GameTestInfo,
                    runner: GameTestRunner,
                ) {}

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

                fun cleanup() {
                    observer?.takeUnless { it.isRemoved }?.let {
                        container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
                    }
                    if (container.getSubLevel(id) == null) {
                        SableOrbitalObjects.requestNativeLoad(level, id)
                    }
                    (container.getSubLevel(id) as? ServerSubLevel)?.let {
                        container.removeForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                        container.removeSubLevel(it, SubLevelRemovalReason.REMOVED)
                    }
                    SableOrbitalObjects.data(level).remove(id)
                }
            },
        )
        helper.runAfterDelay(20) {
            physics.pipeline.prePhysicsTicks()
            physics.pipeline.resetVelocity(body)
            physics.getPhysicsHandle(body).addLinearAndAngularVelocity(Vector3d(0.0, 0.0, 120.0), Vector3d())
            physics.pipeline.prePhysicsTicks()
            container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            val p = body.logicalPose().position()
            container.holdingChunkMap.moveToUnloaded(body, ChunkPos(BlockPos.containing(p.x, 0.0, p.z)))
            helper.assertTrue(container.getSubLevel(id) == null, "Native station stayed loaded")
            container.holdingChunkMap.saveAll()
            container.holdingChunkMap.storage.flush()
            val data = SableOrbitalObjects.data(level)
            val initial = checkNotNull(data.records[id]) { "Native unload did not capture orbital metadata" }
            val pointer = checkNotNull(initial.pointer) { "Native checkpoint did not publish a durable pointer" }
            val chunks = level.chunkSource.loadedChunksCount
            helper.runAfterDelay(100) {
                helper.assertTrue(container.getSubLevel(id) == null, "Background station acquired a native body")
                helper.assertTrue(level.chunkSource.loadedChunksCount <= chunks + 2, "Unloaded orbit loaded a terrain trail")
                val evolved = checkNotNull(data.records[id])
                helper.assertTrue(
                    evolved.tick > initial.tick && (evolved.state.position - initial.state.position).length() > 100,
                    "Unloaded station did not advance",
                )
                // A fresh DimensionDataStorage and a fresh native payload exercise the restart storage boundary.
                level.dataStorage.save()
                net.neoforged.neoforge.common.IOUtilities
                    .waitUntilIOWorkerComplete()
                val folder =
                    DimensionType
                        .getStorageFolder(
                            level.dimension(),
                            level.server.getWorldPath(LevelResource.ROOT),
                        ).resolve("data")
                val freshStorage = DimensionDataStorage(folder.toFile(), level.server.fixerUpper, level.registryAccess())
                val fresh = freshStorage.computeIfAbsent(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME)
                helper.assertTrue(
                    fresh !== data && fresh.clock == data.clock && fresh.records[id] == data.records[id],
                    "Orbital registry did not survive a fresh disk read",
                )
                val expected =
                    evolved(level, initial.state, initial.tick, fresh.clock - initial.tick)
                SableOrbitalObjects.close(level.server)
                level.dataStorage.set(OrbitalObjectData.NAME, fresh)
                val nativePointer =
                    dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer(
                        ChunkPos(pointer.x, pointer.z),
                        pointer.storage,
                        pointer.index,
                    )
                val payload =
                    checkNotNull(container.holdingChunkMap.storage.attemptLoadSubLevel(nativePointer.chunkPos(), nativePointer.local()))
                helper.assertTrue(payload.uuid() == id, "Native storage pointer names another payload")
                SableOrbitalObjects.requestNativeLoad(level, id)
                val restored = checkNotNull(container.getSubLevel(id) as? ServerSubLevel)
                val reading = SableFlightGravity.reading(restored)
                val actual =
                    org.cbi.orbitalis.core.planet.FlatPlanetFrame.toSurface(
                        Vector3(reading.position.x, reading.position.y, reading.position.z),
                    )
                helper.assertTrue((actual - expected.position).length() < 1e-5, "Native reload restored a stale orbital position")
                println(
                    "ORBITALIS_RESTORE_VELOCITY x=${reading.velocity.x} y=${reading.velocity.y} z=${reading.velocity.z} expected=${expected.velocity}",
                )
                helper.assertTrue(
                    Vector3(reading.velocity.x, reading.velocity.y, reading.velocity.z).minus(expected.velocity).length() <
                        2 *
                        maxOf(
                            Math.ulp(expected.velocity.x.toFloat()),
                            Math.ulp(expected.velocity.y.toFloat()),
                            Math.ulp(expected.velocity.z.toFloat()),
                        ).toDouble(),
                    "Native load retention damped orbital velocity: actual=${reading.velocity}, expected=${expected.velocity}",
                )
                val cargo =
                    restored.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<ChestBlockEntity>()
                        .single()
                        .getItem(
                            0,
                        )
                helper.assertTrue(cargo.`is`(Items.DIAMOND) && cargo.count == 7, "Native station cargo changed")
                helper.assertTrue(container.allSubLevels.count { it.uniqueId == id } == 1, "Station UUID duplicated")
                println(
                    "ORBITALIS_UNLOADED_STATION elapsedTicks=${fresh.clock - initial.tick} positionError=${(actual - expected.position).length()} cargo=7 diamonds nativeOwner=1",
                )
                // Exercise the actual automatic wake/snatch route, including a deferred long catch-up.
                val rp = restored.logicalPose().position()
                container.holdingChunkMap.moveToUnloaded(restored, ChunkPos(BlockPos.containing(rp.x, 0.0, rp.z)))
                container.holdingChunkMap.saveAll()
                val freshAnchor = checkNotNull(fresh.records[id])
                repeat(1300) { fresh.advanceClock() }
                val ref = checkNotNull(freshAnchor.pointer)
                container.holdingChunkMap.snatchAndLoad(
                    dev.ryanhcode.sable.sublevel.storage.holding
                        .GlobalSavedSubLevelPointer(ChunkPos(ref.x, ref.z), ref.storage, ref.index),
                    id,
                )
                helper.assertTrue(container.getSubLevel(id) == null, "Long catch-up was loaded with stale motion")
                container.holdingChunkMap.saveAll()
                container.holdingChunkMap.storage.flush()
                val retainedRef = checkNotNull(fresh.records[id]?.pointer)
                val retained =
                    checkNotNull(
                        container.holdingChunkMap.storage.attemptLoadSubLevel(
                            ChunkPos(retainedRef.x, retainedRef.z),
                            dev.ryanhcode.sable.sublevel.storage.holding
                                .SavedSubLevelPointer(retainedRef.storage, retainedRef.index),
                        ),
                    )
                helper.assertTrue(retained.uuid() == id, "Deferred snatch lost native ownership before saving")
                val projected =
                    evolved(level, evolved(level, freshAnchor.state, freshAnchor.tick, 1200), freshAnchor.tick + 1200, 160)
                val observerPose =
                    Pose3d().also {
                        val native =
                            org.cbi.orbitalis.core.planet.FlatPlanetFrame
                                .toSpace(projected.position)
                        it.position().set(native.x + 300, native.y, native.z)
                    }
                observer =
                    (container.allocateNewSubLevel(observerPose) as ServerSubLevel).also {
                        it.plot.newEmptyChunk(it.plot.centerChunk)
                        it.plot.embeddedLevelAccessor.setBlock(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(), 3)
                        it.updateMergedMassData(1.0f)
                        it.updateBoundingBox()
                        it.forceUpdateGlobalBounds()
                        it.updateLastPose()
                        container.addForceLoadTicket(it, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
                    }
                helper.runAfterDelay(100) {
                    val awake =
                        checkNotNull(
                            container.getSubLevel(id) as? ServerSubLevel,
                        ) { "Nearby physical construction did not automatically wake station" }
                    val cargoAgain =
                        awake.plot.loadedChunks
                            .flatMap { it.chunk.blockEntities.values }
                            .filterIsInstance<ChestBlockEntity>()
                            .single()
                            .getItem(0)
                    helper.assertTrue(cargoAgain.`is`(Items.DIAMOND) && cargoAgain.count == 7, "Automatic wake changed cargo")
                    helper.assertTrue(container.allSubLevels.count { it.uniqueId == id } == 1, "Automatic wake duplicated station")
                    println("ORBITALIS_AUTOMATIC_STATION_WAKE backlog=1300 deferredPayload=retained cargo=7 diamonds")
                    val wakeType =
                        checkNotNull(
                            SubLevelLoadingTicketType.byName(
                                net.minecraft.resources.ResourceLocation
                                    .fromNamespaceAndPath("orbitalis", "orbital_wake"),
                            ),
                        )
                    @Suppress("UNCHECKED_CAST")
                    container.removeForceLoadTicket(awake, wakeType as SubLevelLoadingTicketType<Unit>, Unit.INSTANCE)
                    val warmPosition = awake.logicalPose().position()
                    awake.plot.loadedChunks
                        .flatMap { it.chunk.blockEntities.values }
                        .filterIsInstance<ChestBlockEntity>()
                        .single()
                        .setItem(0, ItemStack(Items.DIAMOND, 9))
                    container.holdingChunkMap.moveToUnloaded(
                        awake,
                        ChunkPos(BlockPos.containing(warmPosition.x, warmPosition.y, warmPosition.z)),
                    )
                    helper.assertTrue(
                        container.holdingChunkMap.getHoldingSubLevel(id) != null,
                        "Warm holding fixture did not remain in native memory",
                    )
                    helper.runAfterDelay(40) {
                        val warm = checkNotNull(container.getSubLevel(id) as? ServerSubLevel) { "Warm holding failed to wake" }
                        val chest =
                            warm.plot.loadedChunks
                                .flatMap { it.chunk.blockEntities.values }
                                .filterIsInstance<ChestBlockEntity>()
                                .single()
                        helper.assertTrue(chest.getItem(0).count == 9, "Warm wake selected stale disk cargo")
                        chest.setItem(0, ItemStack(Items.DIAMOND, 11))
                        container.holdingChunkMap.saveAll()
                        container.holdingChunkMap.storage.flush()
                        val savedPointer = checkNotNull(warm.lastSerializationPointer)
                        val nativeSaved =
                            checkNotNull(
                                container.holdingChunkMap.storage.attemptLoadSubLevel(savedPointer.chunkPos(), savedPointer.local()),
                            )
                        val cargoTags = mutableListOf<CompoundTag>()

                        fun visit(tag: net.minecraft.nbt.Tag) {
                            when (tag) {
                                is CompoundTag -> {
                                    if (tag.getString("id") == "minecraft:chest") cargoTags.add(tag)
                                    tag.allKeys.forEach { key -> tag.get(key)?.let(::visit) }
                                }

                                is ListTag -> {
                                    tag.forEach(::visit)
                                }
                            }
                        }
                        visit(nativeSaved.fullTag())
                        val durableCargo = cargoTags.single().getList("Items", 10).getCompound(0)
                        helper.assertTrue(
                            durableCargo.getString("id") == "minecraft:diamond" && durableCargo.getInt("count") == 11,
                            "Stale holding payload overwrote updated live cargo",
                        )
                        println("ORBITALIS_WARM_STATION_WAKE durableCargo=11 diamonds staleHolding=absent")
                        helper.succeed()
                    }
                }
            }
        }
    }

    @JvmStatic
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 100, batch = "orbital_metadata")
    fun orbitalMetadataCompatibility(helper: GameTestHelper) {
        val registries = helper.level.registryAccess()
        val tag =
            CompoundTag().apply {
                putInt("Version", 1)
                putLong("Clock", 20)
                put("Objects", ListTag())
            }
        val record =
            OrbitalObjectData.Record(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                20,
                CartesianState(Vector3(100.0, 5000.0, 0.0), Vector3(10.0, 0.0, 0.0)),
                2.0,
                OrbitalObjectData.Pointer(-2, 3, 1, 2),
            )
        val data = OrbitalObjectData.load(tag)
        data.put(record)
        val saved = data.save(CompoundTag(), registries)
        helper.assertTrue(OrbitalObjectData.load(saved).records[record.id] == record, "Motion index roundtrip changed state")
        for (invalid in listOf(
            saved.copy().apply {
                putInt("Version", 99)
                putString("Future", "retain")
            },
            saved.copy().apply { putLong("Clock", -1) },
            saved.copy().apply { getList("Objects", 10).add(OrbitalObjectData.write(record)) },
            saved.copy().apply { getList("Objects", 10).getCompound(0).getCompound("Position").putDouble("X", Double.NaN) },
            saved.copy().apply { getList("Objects", 10).getCompound(0).putString("Frame", "other:unknown") },
        )) {
            val restored = OrbitalObjectData.load(invalid)
            helper.assertTrue(
                !restored.readable && restored.save(CompoundTag(), registries) == invalid,
                "Unknown/malformed orbital index was reinterpreted or overwritten",
            )
        }
        val folder = Files.createTempDirectory("orbitalis-orbit-index-").toFile()
        try {
            val storage = DimensionDataStorage(folder, helper.level.server.fixerUpper, registries)
            storage.set(OrbitalObjectData.NAME, data)
            data.setDirty()
            storage.save()
            net.neoforged.neoforge.common.IOUtilities
                .waitUntilIOWorkerComplete()
            val fresh =
                DimensionDataStorage(folder, helper.level.server.fixerUpper, registries)
                    .computeIfAbsent(OrbitalObjectData.FACTORY, OrbitalObjectData.NAME)
            helper.assertTrue(fresh.clock == 20L && fresh.records[record.id] == record, "Clock/pointer did not survive disk reload")
        } finally {
            folder.deleteRecursively()
        }
        helper.succeed()
    }
}
