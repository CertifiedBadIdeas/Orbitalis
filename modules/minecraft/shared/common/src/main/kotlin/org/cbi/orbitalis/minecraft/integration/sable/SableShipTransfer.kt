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

package org.cbi.orbitalis.minecraft.integration.sable

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelOccupancySavedData
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.core.planet.Vector3
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.joml.Vector3d
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.util.Unit as MinecraftUnit

/** One bounded diagnostic transaction at a time. All native calls are server-thread confined. */
class SableShipTransfer(
    private val server: MinecraftServer,
    folder: Path,
    private val pilotSync: ShipTransferPilotSync,
) : AutoCloseable {
    constructor(server: MinecraftServer, folder: Path) : this(server, folder, ShipTransferPilotSync.NATIVE)

    private val journal = ShipTransferJournal(folder)
    private val records = mutableMapOf<UUID, ShipTransferRecord>()
    private val startup = journal.read()
    private var startupHandled = false
    private var pending: CompletableFuture<Unit>? = null
    private var continuation: (() -> Unit)? = null
    private var waitFor: (() -> Boolean)? = null
    private var afterWait: (() -> Unit)? = null
    private var waitedTicks = 0
    private var waitFailure = "Native entity loading timed out"
    private var active: ShipTransferRecord? = null
    private var pilot: ServerPlayer? = null
    private var transferStarted = 0L
    private val pauses = mutableMapOf<SubLevelPhysicsSystem, Boolean>()
    private val tickets = mutableSetOf<ServerSubLevel>()
    private val entityPreloads = mutableListOf<org.cbi.orbitalis.minecraft.flight.ShipDestinationPreload>()
    var error: String? = null
        private set
    val busy: Boolean get() = !startupHandled || active != null || pending != null || waitFor != null || error != null

    fun locks(level: ServerLevel): Boolean = (busy || records.values.any { it.pilotPending }) && level in worlds()

    private fun sourceLevel(record: ShipTransferRecord): ServerLevel =
        checkNotNull(
            org.cbi.orbitalis.minecraft.flight.OrbitalSectors
                .ensure(server, record.sourceDimension),
        )

    private fun destinationLevel(record: ShipTransferRecord): ServerLevel =
        checkNotNull(
            org.cbi.orbitalis.minecraft.flight.OrbitalSectors
                .ensure(server, record.destinationDimension),
        )

    private fun worlds(): List<ServerLevel> = CelestialFlight.levels(server)

    private fun pause() {
        val required = worlds().map(SubLevelPhysicsSystem::require).toSet()
        for ((physics, previous) in pauses.toMap()) {
            if (physics !in required) {
                physics.paused = previous
                pauses.remove(physics)
            }
        }
        for (physics in required) {
            pauses.putIfAbsent(physics, physics.paused)
            physics.paused = true
        }
    }

    private fun resume() {
        if (transferStarted != 0L) {
            Sable.LOGGER.info(
                "ORBITALIS_TRANSFER_TIMING ship={} stage=total ms={}",
                active?.ship,
                (System.nanoTime() - transferStarted) / 1_000_000.0,
            )
            transferStarted = 0L
        }
        ShipTransferTickGate.release(this)
        entityPreloads.forEach { it.close() }
        entityPreloads.clear()
        tickets.forEach { body ->
            if (!body.isRemoved) {
                SubLevelContainer
                    .getContainer(
                        body.level,
                    )?.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
            }
        }
        tickets.clear()
        pauses.forEach { (physics, previous) -> physics.paused = previous }
        pauses.clear()
        active = null
        pilot = null
    }

    private fun retain(body: ServerSubLevel) {
        SubLevelContainer
            .getContainer(
                body.level,
            )!!
            .addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, MinecraftUnit.INSTANCE)
        tickets.add(body)
        active?.let { record ->
            ShipTransferTickGate.holdEntities(this, body.level, ShipTransferPassengers.ids(record.sourcePassengers))
        }
        ShipTransferTickGate.hold(this, body.level, body.plot.loadedChunks.map { it.chunk.pos })
    }

    private fun persist(
        record: ShipTransferRecord,
        next: () -> Unit,
    ) {
        val started = System.nanoTime()
        pending = journal.write(record)
        continuation = {
            Sable.LOGGER.info(
                "ORBITALIS_TRANSFER_TIMING ship={} stage=journal-{} ms={}",
                record.ship,
                record.phase,
                (System.nanoTime() - started) / 1_000_000.0,
            )
            records[record.ship] = record
            active = record
            next()
        }
    }

    fun tick() {
        if (error != null) return
        try {
            if (!startupHandled) {
                pause()
                if (!startup.isDone) return
                records.putAll(startup.join().associateBy { it.ship })
                startupHandled = true
                recoverNext()
            }
            waitFor?.let { ready ->
                check(++waitedTicks <= 200) { waitFailure }
                if (ready()) {
                    val next = checkNotNull(afterWait)
                    waitFor = null
                    afterWait = null
                    next()
                }
                return
            }
            val write = pending ?: return
            if (!write.isDone) return
            val next = checkNotNull(continuation)
            write.join()
            pending = null
            continuation = null
            next()
        } catch (failure: Exception) {
            // Keep the diagnostic worlds paused; the durable record remains available for restart.
            error = failure.cause?.message ?: failure.message ?: failure.javaClass.simpleName
            pilot?.sendSystemMessage(Component.literal("Ship transfer stopped: $error. Restart to reconcile its journal."))
            Sable.LOGGER.error("Orbitalis ship transfer stopped; diagnostic physics remains paused", failure)
        }
    }

    private fun awaitEntities(
        bodies: List<ServerSubLevel>,
        next: () -> Unit,
    ) {
        awaitCondition("Native entity loading timed out", { bodies.all(SableShipSnapshot::entitiesReady) }, next)
    }

    private fun awaitCondition(
        failure: String,
        ready: () -> Boolean,
        next: () -> Unit,
    ) {
        waitedTicks = 0
        waitFailure = failure
        waitFor = ready
        afterWait = next
    }

    private fun handoffPilot(
        player: ServerPlayer,
        body: ServerSubLevel,
        state: CompoundTag,
        preserveClientMotion: Boolean = false,
        next: () -> Unit,
    ) {
        check(
            movePlayer(
                player,
                body.level,
                state,
                preserveClientMotion,
                active?.frozenFrame?.takeUnless {
                    val passengers =
                        if (player.level().dimension() ==
                            body.level.dimension()
                        ) {
                            active?.destinationPassengers
                        } else {
                            active?.sourcePassengers
                        }
                    passengers?.filterIsInstance<CompoundTag>()?.any { it.getBoolean("player") && it.getBoolean("plot") } == true
                },
            ),
        ) { "Pilot dimension transfer was rejected" }

        fun available(): Boolean {
            check(player.isAlive && player.level() === body.level) { "Pilot became unavailable during client handoff" }
            return true
        }
        awaitCondition("Destination ship tracking timed out", { available() && pilotSync.hasShip(player, body) }) {
            // Native tracking has queued the complete ship bundle. This second teleport is
            // ordered after it on TCP, so its vanilla acknowledgment fences client loading.
            check(
                movePlayer(
                    player,
                    body.level,
                    state,
                    preserveClientMotion,
                    active?.frozenFrame?.takeUnless {
                        val passengers =
                            if (player.level().dimension() ==
                                body.level.dimension()
                            ) {
                                active?.destinationPassengers
                            } else {
                                active?.sourcePassengers
                            }
                        passengers?.filterIsInstance<CompoundTag>()?.any { it.getBoolean("player") && it.getBoolean("plot") } == true
                    },
                ),
            ) { "Pilot deck positioning was rejected" }
            awaitCondition("Pilot teleport acknowledgment or deck contact timed out", { available() && pilotSync.isReady(player, body) }) {
                player.deltaMovement = Vec3(state.getDouble("vx"), state.getDouble("vy"), state.getDouble("vz"))
                player.fallDistance = 0f
                next()
            }
        }
    }

    private fun handoffTravellers(
        player: ServerPlayer?,
        body: ServerSubLevel,
        state: CompoundTag,
        passengers: net.minecraft.nbt.ListTag,
        preserveClientMotion: Boolean = false,
        next: () -> Unit,
    ) {
        val mount = passengers.map { it as CompoundTag }.firstOrNull { it.getBoolean("player") && it.hasUUID("vehicle") }
        if (player == null) {
            awaitCondition("Destination entities did not become available", { ShipTransferPassengers.ready(body.level, passengers) }) {
                ShipTransferPassengers.mount(body.level, passengers, null)
                next()
            }
        } else if (mount == null) {
            handoffPilot(player, body, state, preserveClientMotion) {
                awaitCondition("Destination entities did not become available", { ShipTransferPassengers.ready(body.level, passengers) }) {
                    ShipTransferPassengers.mount(body.level, passengers, player)
                    next()
                }
            }
        } else {
            player.stopRiding()
            check(movePlayer(player, body.level, state)) { "Passenger dimension transfer was rejected" }
            awaitCondition("Passenger ship tracking timed out", { pilotSync.hasShip(player, body) }) {
                check(movePlayer(player, body.level, state))
                awaitCondition("Passenger teleport acknowledgment timed out", {
                    check(player.isAlive && player.level() === body.level) { "Passenger became unavailable" }
                    !(player.connection as ShipTransferTeleportState).orbitalisHasPendingTeleport() &&
                        ShipTransferPassengers.ready(body.level, passengers)
                }) {
                    ShipTransferPassengers.mount(body.level, passengers, player)
                    next()
                }
            }
        }
    }

    @JvmOverloads
    fun begin(
        player: ServerPlayer?,
        body: ServerSubLevel,
        targetDimension: ResourceKey<Level>? = null,
    ) {
        require(
            !busy && records.values.none { it.pilotPending },
        ) { "A transfer/recovery is running: ${error ?: "wait for completion"}" }
        require(SableFlightGravity.isManaged(body.level) && (player == null || player.level() === body.level)) {
            "Use the Overworld surface or a supported flight space"
        }
        val sourceDimension = body.level.dimension()
        val sourceOrbit = FlightSpaceCoordinates.isOrbital(sourceDimension)
        val destinationDimension = targetDimension ?: if (sourceOrbit) Level.OVERWORLD else FlatPlanetSpace.SPACE
        require(CelestialFlight.supported(destinationDimension) && destinationDimension != sourceDimension) { "Unsupported transfer route" }
        val surfaceDimension = if (sourceOrbit) destinationDimension else sourceDimension
        val orbitalDimension = if (sourceOrbit) sourceDimension else destinationDimension
        val frameTransform = ShipFrameTransform(CelestialFlight.sample(server), sourceDimension, destinationDimension)
        // Freeze bounds at the same current pose as the snapshot, after native drift.
        body.updateBoundingBox()
        val participants = ShipTransferPassengers.preflight(body, player)
        val passengers = participants.data
        val travellerIds = ShipTransferPassengers.ids(passengers)
        val source = SableShipSnapshot.capture(body, travellerIds)
        val target =
            checkNotNull(
                org.cbi.orbitalis.minecraft.flight.OrbitalSectors
                    .ensure(server, destinationDimension),
            )
        require(participants.entities.all { it.canChangeDimensions(body.level, target) }) { "A travelling entity rejects dimension travel" }
        val destination =
            SableShipSnapshot.forDestination(
                frameTransform.ship(source, SableFlightGravity.reading(body).position),
                body.level,
                target,
            )
        checkDestination(target, destination)
        val sourcePilot = player?.let(::playerState) ?: CompoundTag()
        if (player != null) {
            org.cbi.orbitalis.minecraft.flight.FlightMotionPayload(
                sourceDimension.location(),
                Vector3(sourcePilot.getDouble("vx"), sourcePilot.getDouble("vy"), sourcePilot.getDouble("vz")),
            )
        }
        val destinationPassengers = frameTransform.passengers(passengers, source, destination)
        val destinationPilot =
            if (player != null) {
                destinationPassengers
                    .filterIsInstance<CompoundTag>()
                    .single { it.getBoolean("player") }
                    .getCompound("state")
                    .copy()
            } else {
                CompoundTag()
            }
        if (player != null &&
            !destinationPassengers.filterIsInstance<CompoundTag>().single { it.getBoolean("player") }.getBoolean("plot")
        ) {
            val native = Vector3(destinationPilot.getDouble("x"), destinationPilot.getDouble("y"), destinationPilot.getDouble("z"))
            val physical =
                frameTransform.sample.rootPoint(native, destinationDimension)
            require(FlatPlanetSpace.validTransferPosition(physical, frameTransform.sample.frames.profile)) {
                "Pilot outside bounded physical flight coordinates"
            }
        }
        val record =
            ShipTransferRecord(
                UUID.randomUUID(),
                body.uniqueId,
                player?.uuid,
                sourceOrbit,
                source,
                destination,
                sourcePilot,
                destinationPilot,
                savedPointer(body),
                ShipTransferPhase.PREPARED,
                orbitalDimension = orbitalDimension,
                surfaceDimension = surfaceDimension,
                sourcePassengers = passengers,
                frozenFrame = frameTransform.sample,
                destinationPassengers = destinationPassengers,
                explicitSource = sourceDimension,
                explicitDestination = destinationDimension,
            )
        player?.closeContainer()
        active = record
        transferStarted = System.nanoTime()
        pause()
        retain(body)
        pilot = player
        val sourceDataRequired =
            !org.cbi.orbitalis.minecraft.space.SpaceWorlds
                .objects(body.level) ||
                passengers.filterIsInstance<CompoundTag>().any { !it.getBoolean("player") && !it.getBoolean("plot") }
        if (sourceDataRequired) {
            val bounds = SableNBTUtils.readBoundingBox(source.getCompound("world_bounds"))
            val chunks =
                ((kotlin.math.floor(bounds.minX() - 1).toInt() shr 4)..(kotlin.math.floor(bounds.maxX() + 1).toInt() shr 4)).flatMap { x ->
                    ((kotlin.math.floor(bounds.minZ() - 1).toInt() shr 4)..(kotlin.math.floor(bounds.maxZ() + 1).toInt() shr 4)).map { z ->
                        ChunkPos(x, z)
                    }
                }
            val preload =
                org.cbi.orbitalis.minecraft.flight
                    .ShipDestinationPreload()
            preload.update(
                body.level,
                Vector3(
                    (bounds.minX() + bounds.maxX()) * 0.5,
                    (bounds.minY() + bounds.maxY()) * 0.5,
                    (bounds.minZ() + bounds.maxZ()) * 0.5,
                ),
            )
            entityPreloads.add(preload)
            persist(record) {
                // Hold the durable PREPARED source still, then repeat preflight after loading
                // so newly loaded loose entities cannot be discarded during authority change.
                awaitCondition("Source entity data loading timed out", {
                    chunks.all { body.level.areEntitiesLoaded(it.toLong()) }
                }) { stage(record, body, player) }
            }
        } else {
            persist(record) { stage(record, body, player) }
        }
    }

    private fun stage(
        record: ShipTransferRecord,
        source: ServerSubLevel,
        player: ServerPlayer?,
    ) {
        try {
            stagePrepared(record, source, player)
        } catch (failure: Exception) {
            abortPrepared(record, player, failure.message ?: "Preparation failed")
        }
    }

    private fun abortPrepared(
        record: ShipTransferRecord,
        player: ServerPlayer?,
        reason: String,
    ) {
        Sable.LOGGER.warn("Orbitalis transfer cancelled; source retained: ship={} reason={}", record.ship, reason)
        // Re-establish source authority durably before discarding a partially staged body.
        persist(record) {
            if (SubLevelContainer.getContainer(sourceLevel(record))!!.getSubLevel(record.ship) == null) {
                recoverNext()
                return@persist
            }
            val target = SubLevelContainer.getContainer(destinationLevel(record))!!
            val staged = target.getSubLevel(record.ship) as? ServerSubLevel
            val cleanup: () -> Unit = {
                if (staged != null) {
                    SableShipSnapshot.discardAttachments(staged.level, record.destination)
                    target.removeSubLevel(staged, SubLevelRemovalReason.REMOVED)
                }
                checkpoint()
                persist(record.copy(phase = ShipTransferPhase.DONE, recoveredSource = true)) { resume() }
                player?.sendSystemMessage(Component.literal("Transfer cancelled; source retained: $reason"))
            }
            if (staged != null) awaitEntities(listOf(staged), cleanup) else cleanup()
        }
    }

    private fun stagePrepared(
        record: ShipTransferRecord,
        source: ServerSubLevel,
        player: ServerPlayer?,
    ) {
        // A block/entity update during the asynchronous prepare must not be silently discarded.
        val changed =
            when {
                source.isRemoved -> "source ship was removed"

                player != null && (!player.isAlive || player.level() !== source.level) -> "pilot became unavailable"

                SableShipSnapshot.capture(source, ShipTransferPassengers.ids(record.sourcePassengers)).getCompound("plot") !=
                    record.source.getCompound("plot") -> "ship block data changed"

                savedPointer(source) != record.sourcePointer -> "native save pointer changed"

                player != null && player.position().distanceTo(position(record.sourcePilot)) > 0.25 -> "pilot moved more than 0.25 m"

                else -> null
            }
        if (changed != null) {
            // No authority change happened: preserve the source's current state and checkpoint it.
            checkpoint()
            persist(record.copy(phase = ShipTransferPhase.DONE)) { resume() }
            player?.sendSystemMessage(Component.literal("Transfer cancelled; source retained: $changed."))
            return
        }
        SableShipSnapshot.preflight(source, player, ShipTransferPassengers.ids(record.sourcePassengers))
        val target = destinationLevel(record)
        checkDestination(target, record.destination)
        val staged = SableShipSnapshot.load(target, record.destination)
        retain(staged)
        check(staged.uniqueId == record.ship)
        awaitEntities(listOf(staged)) {
            try {
                val current = SableShipSnapshot.capture(source, ShipTransferPassengers.ids(record.sourcePassengers))
                require(
                    !source.isRemoved && (player == null || (player.level() === source.level && player.isAlive)),
                ) { "Source or pilot became unavailable" }
                require(
                    current.getCompound("plot") == record.source.getCompound("plot") &&
                        current.get("orbitalis_attached") == record.source.get("orbitalis_attached"),
                ) { "Source changed while loading destination" }
                require(
                    ShipTransferPassengers.unchanged(source.level, record.sourcePassengers),
                ) { "Travelling entity data changed while staging" }
                SableShipSnapshot.preflight(source, player, ShipTransferPassengers.ids(record.sourcePassengers))
                SableShipSnapshot.restoreAttachments(staged, record.destination)
                verifyCargo(record.destination, SableShipSnapshot.capture(staged, ShipTransferPassengers.ids(record.destinationPassengers)))
                persist(record.copy(phase = ShipTransferPhase.COMMITTED)) { commit(record, source, staged, player) }
            } catch (failure: Exception) {
                abortPrepared(record, player, failure.message ?: "Destination verification failed")
            }
        }
    }

    private fun commit(
        record: ShipTransferRecord,
        source: ServerSubLevel,
        staged: ServerSubLevel,
        player: ServerPlayer?,
    ) {
        // Sable removal packets carry plot coordinates but no dimension. Send them while
        // the pilot is still in the source world, before the destination respawn packet.
        player?.stopRiding()
        ShipTransferPassengers.discard(source.level, record.sourcePassengers)
        SableShipSnapshot.discardAttachments(source.level, record.source)
        SubLevelContainer.getContainer(source.level)!!.removeSubLevel(source, SubLevelRemovalReason.REMOVED)
        ShipTransferPassengers.restore(staged.level, record.destinationPassengers)
        handoffTravellers(player, staged, record.destinationPilot, record.destinationPassengers, true) {
            checkpoint()
            verifySaved(staged)
            persist(record.copy(phase = ShipTransferPhase.DONE)) {
                player?.sendSystemMessage(
                    Component.literal(
                        "Transferred ship ${record.ship}; exact motion and cargo preserved. /orbitalis flight transfer returns it.",
                    ),
                )
                resume()
            }
        }
    }

    private fun checkDestination(
        world: ServerLevel,
        snapshot: CompoundTag,
        ignoredPlayers: Set<UUID> = emptySet(),
    ) {
        val container = checkNotNull(SubLevelContainer.getContainer(world))
        val plot = snapshot.getCompound("plot")
        val x = plot.getInt("plot_x")
        val z = plot.getInt("plot_z")
        require(x in 0 until (1 shl container.logSideLength) && z in 0 until (1 shl container.logSideLength)) { "Invalid native plot" }
        require(!container.occupancy[container.getIndex(x, z)]) {
            "Destination plot is occupied (including unloaded ships); no source changes were made"
        }
        require(container.getSubLevel(snapshot.getUUID("uuid")) == null) { "Destination already contains this ship" }
        val pose = SableNBTUtils.readPose3d(snapshot.getCompound("pose"))
        val bounds = SableNBTUtils.readBoundingBox(snapshot.getCompound("world_bounds"))
        require(bounds.maxX() - bounds.minX() <= 32 && bounds.maxZ() - bounds.minZ() <= 32) { "Diagnostic craft is too wide" }
        for (cx in (kotlin.math.floor(bounds.minX()).toInt() shr 4)..(kotlin.math.floor(bounds.maxX()).toInt() shr 4)) {
            for (cz in (
                kotlin.math
                    .floor(
                        bounds.minZ(),
                    ).toInt() shr 4
            )..(kotlin.math.floor(bounds.maxZ()).toInt() shr 4)) {
                world.getChunk(cx, cz)
            }
        }
        val area = AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(0.1)
        val clearBlocks =
            if (ignoredPlayers.isEmpty()) {
                world.noCollision(null, area)
            } else {
                world.getBlockCollisions(null, area).none { !it.isEmpty }
            }
        require(
            clearBlocks &&
                world.getEntities(null, area).all {
                    it.uuid in ignoredPlayers && it is ServerPlayer
                },
        ) { "Destination is obstructed" }
        require(pose.position().isFinite) { "Invalid destination position" }
    }

    /** Native save APIs own their server-thread checkpoint; journal compression/fsync uses its worker. */
    private fun checkpoint() {
        val started = System.nanoTime()
        for (world in worlds()) {
            val container = SubLevelContainer.getContainer(world)!!
            // This is the same native ship checkpoint used by Sable's ServerLevel save hook.
            // A handoff changes native plots/occupancy, not ordinary terrain. Flushing every
            // vanilla chunk visited by a full orbit would stall the server for minutes.
            container.holdingChunkMap.saveAll()
            container.holdingChunkMap.storage.flush()
            val record = active
            if (record != null && (
                    record.sourcePassengers.any { !(it as CompoundTag).getBoolean("player") } ||
                        record.destinationPassengers.any { !(it as CompoundTag).getBoolean("player") }
                )
            ) {
                (world as ShipTransferEntityCheckpoint).orbitalisCheckpointEntities()
            }
            world.dataStorage.save()
        }
        server.playerList.saveAll()
        // Sable can create a mounted player's LoginPoint while saving player data.
        // Persist that native SavedData after the player/RootVehicle save, before DONE.
        for (world in worlds()) world.dataStorage.save()
        Sable.LOGGER.info(
            "ORBITALIS_TRANSFER_TIMING ship={} stage=native-checkpoint ms={}",
            active?.ship,
            (System.nanoTime() - started) / 1_000_000.0,
        )
    }

    private fun verifySaved(body: ServerSubLevel) {
        val pointer = checkNotNull(body.lastSerializationPointer) { "Native checkpoint did not save the destination" }
        val storage = SubLevelContainer.getContainer(body.level)!!.holdingChunkMap.storage
        val saved = checkNotNull(storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local())) { "Native checkpoint is unreadable" }
        check(saved.uuid() == body.uniqueId) { "Native checkpoint identity mismatch" }
        verifyCargo(
            SableShipSnapshot.capture(body, active?.let { ShipTransferPassengers.ids(it.sourcePassengers) } ?: emptySet()),
            saved.fullTag(),
        )
    }

    private fun recoverNext() {
        val record = records.values.firstOrNull { it.phase != ShipTransferPhase.DONE }
        active = record
        pause()
        if (record == null) {
            val waiting = records.values.filter { it.pilotPending }
            if (waiting.isEmpty()) {
                resume()
            } else {
                // This bounded diagnostic has no offline flight mode. Keep recovery anchors stable.
                for (receipt in waiting) {
                    val world = if (receipt.recoveredSource) sourceLevel(receipt) else destinationLevel(receipt)
                    val snapshot = if (receipt.recoveredSource) receipt.source else receipt.destination
                    retain(checkNotNull(findCopy(world, snapshot, CompoundTag())) { "Recovered ship checkpoint is unavailable" })
                }
                active = null
                pilot = null
            }
            return
        }
        active = record
        val sourceWorld = sourceLevel(record)
        val destinationWorld = destinationLevel(record)
        val copies =
            listOf(sourceWorld to record.source, destinationWorld to record.destination).map { (world, snapshot) ->
                val container = SubLevelContainer.getContainer(world)!!
                val pointer = if (world === sourceWorld) record.sourcePointer else CompoundTag()
                findCopy(world, snapshot, pointer) ?: run {
                    val plot = snapshot.getCompound("plot")
                    require(
                        container.getSubLevel(plot.getInt("plot_x"), plot.getInt("plot_z")) == null,
                    ) { "Recovery slot belongs to another ship" }
                    container.occupancy.clear(container.getIndex(plot.getInt("plot_x"), plot.getInt("plot_z")))
                    SableShipSnapshot.load(world, snapshot).also(::retain)
                }
            }
        val passengerWorlds = listOf(sourceWorld to record.sourcePassengers, destinationWorld to record.destinationPassengers)
        val entityChunks = mutableListOf<Pair<ServerLevel, ChunkPos>>()
        for ((world, passengers) in passengerWorlds) {
            for (item in passengers) {
                val entry = item as CompoundTag
                if (entry.getBoolean("player") || entry.getBoolean("plot")) continue
                val pos = entry.getCompound("data").getList("Pos", 6)
                val point = Vector3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2))
                entityPreloads.add(
                    org.cbi.orbitalis.minecraft.flight
                        .ShipDestinationPreload()
                        .also { it.update(world, point) },
                )
                entityChunks.add(world to ChunkPos(BlockPos.containing(point.x, point.y, point.z)))
            }
        }
        awaitCondition("Recovery entity loading timed out", {
            copies.all(SableShipSnapshot::entitiesReady) && entityChunks.all { (world, chunk) -> world.areEntitiesLoaded(chunk.toLong()) }
        }) {
            for ((world, passengers) in passengerWorlds) ShipTransferPassengers.discard(world, passengers)
            for ((world, snapshot) in listOf(sourceWorld to record.source, destinationWorld to record.destination)) {
                val pointer = if (world === sourceWorld) record.sourcePointer else CompoundTag()
                SableShipSnapshot.discardAttachments(world, snapshot)
                discardCopies(world, snapshot, pointer)
            }
            val committed = record.phase == ShipTransferPhase.COMMITTED
            val winnerWorld = if (committed) destinationWorld else sourceWorld
            val snapshot = if (committed) record.destination else record.source
            checkDestination(winnerWorld, snapshot, record.pilot?.let { setOf(it) } ?: emptySet())
            val restored = SableShipSnapshot.load(winnerWorld, snapshot)
            retain(restored)
            awaitEntities(listOf(restored)) {
                SableShipSnapshot.restoreAttachments(restored, snapshot)
                val passengers = if (committed) record.destinationPassengers else record.sourcePassengers
                ShipTransferPassengers.restore(winnerWorld, passengers)
                val online = record.pilot?.let(server.playerList::getPlayer)
                val finish = {
                    checkpoint()
                    verifySaved(restored)
                    persist(
                        record.copy(
                            phase = ShipTransferPhase.DONE,
                            pilotPending = record.pilot != null && online == null,
                            recoveredSource = !committed,
                        ),
                    ) { recoverNext() }
                }
                if (online == null) {
                    ShipTransferPassengers.mount(winnerWorld, passengers, null)
                    finish()
                } else {
                    pilot = online
                    handoffTravellers(
                        online,
                        restored,
                        if (committed) record.destinationPilot else record.sourcePilot,
                        passengers,
                        next = finish,
                    )
                }
            }
        }
    }

    private fun discardCopies(
        world: ServerLevel,
        snapshot: CompoundTag,
        previous: CompoundTag,
    ) {
        val container = SubLevelContainer.getContainer(world)!!
        val plot = snapshot.getCompound("plot")
        val localX = plot.getInt("plot_x")
        val localZ = plot.getInt("plot_z")
        val allocated = container.getSubLevel(localX, localZ)
        require(allocated == null || allocated.uniqueId == snapshot.getUUID("uuid")) { "Recovery plot belongs to a different ship" }
        findCopy(world, snapshot, previous)?.let { copy ->
            // A native interrupted save can leave the UUID indexed at both old and current anchors.
            val map = container.holdingChunkMap
            for (chunk in anchorChunks(snapshot, previous)) {
                val holding = map.storage.attemptLoadHoldingChunk(chunk) ?: continue
                for (pointer in holding.subLevelPointers.toList()) {
                    if (map.storage.attemptLoadSubLevel(chunk, pointer)?.uuid() == copy.uniqueId) {
                        copy.lastSerializationPointer =
                            GlobalSavedSubLevelPointer(chunk, pointer.storageIndex(), pointer.subLevelIndex())
                        map.queueDeletion(copy)
                    }
                }
            }
            container.removeSubLevel(copy, SubLevelRemovalReason.REMOVED)
        }
        // The journal reserved these two slots before staging. An interrupted native checkpoint may
        // have persisted deletion before its occupancy bitmap; no foreign allocation is cleared.
        if (container.getSubLevel(localX, localZ) == null) {
            container.occupancy.clear(container.getIndex(localX, localZ))
            SubLevelOccupancySavedData.getOrLoad(world).setDirty()
        }
    }

    private fun anchorChunks(
        snapshot: CompoundTag,
        pointer: CompoundTag,
    ): Set<ChunkPos> {
        val pose = SableNBTUtils.readPose3d(snapshot.getCompound("pose"))
        return mutableSetOf(ChunkPos(BlockPos.containing(pose.position().x, pose.position().y, pose.position().z))).also { chunks ->
            if (pointer.contains("x")) chunks.add(ChunkPos(pointer.getInt("x"), pointer.getInt("z")))
        }
    }

    /** Frozen snapshots can only have been saved at their anchor or at their previous native pointer. */
    private fun findCopy(
        world: ServerLevel,
        snapshot: CompoundTag,
        pointer: CompoundTag,
    ): ServerSubLevel? {
        val container = SubLevelContainer.getContainer(world)!!
        val uuid = snapshot.getUUID("uuid")
        (container.getSubLevel(uuid) as? ServerSubLevel)?.let { return it }
        val map = container.holdingChunkMap
        for (chunk in anchorChunks(snapshot, pointer)) {
            val holding = map.storage.attemptLoadHoldingChunk(chunk) ?: continue
            for (local in holding.subLevelPointers.toList()) {
                val data = map.storage.attemptLoadSubLevel(chunk, local) ?: continue
                if (data.uuid() == uuid) {
                    map.snatchAndLoad(GlobalSavedSubLevelPointer(chunk, local.storageIndex(), local.subLevelIndex()), uuid)
                    return container.getSubLevel(uuid) as? ServerSubLevel
                }
            }
        }
        return null
    }

    fun loggedIn(player: ServerPlayer) {
        if (busy) return
        val record = records.values.firstOrNull { it.pilot == player.uuid && it.pilotPending } ?: return
        val state = if (record.recoveredSource) record.sourcePilot else record.destinationPilot
        val world = if (record.recoveredSource) sourceLevel(record) else destinationLevel(record)
        active = record
        pilot = player
        pause()
        val snapshot = if (record.recoveredSource) record.source else record.destination
        val body = checkNotNull(findCopy(world, snapshot, CompoundTag())) { "Recovered ship checkpoint is unavailable" }
        retain(body)
        handoffTravellers(player, body, state, if (record.recoveredSource) record.sourcePassengers else record.destinationPassengers) {
            checkpoint()
            persist(record.copy(pilotPending = false)) { resume() }
        }
    }

    override fun close() {
        entityPreloads.forEach { it.close() }
        entityPreloads.clear()
        ShipTransferTickGate.release(this)
        journal.close()
    }

    companion object {
        fun playerState(player: ServerPlayer): CompoundTag =
            CompoundTag().also {
                it.putDouble("x", player.x)
                it.putDouble("y", player.y)
                it.putDouble("z", player.z)
                it.putDouble("vx", player.deltaMovement.x)
                it.putDouble("vy", player.deltaMovement.y)
                it.putDouble("vz", player.deltaMovement.z)
                it.putDouble("yaw", player.yRot.toDouble())
                it.putDouble("pitch", player.xRot.toDouble())
            }

        private fun position(tag: CompoundTag) = Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"))

        private fun movePlayer(
            player: ServerPlayer,
            world: ServerLevel,
            state: CompoundTag,
            preserveClientMotion: Boolean = false,
            frame: CelestialFlight? = null,
        ): Boolean {
            val sourceDimension = player.level().dimension()
            val targetDimension = world.dimension()
            val rotation =
                if (frame != null && sourceDimension != targetDimension) {
                    org.cbi.orbitalis.core.orbit.Rotation3.fromAxes(
                        frame.direction(Vector3(1.0, 0.0, 0.0), sourceDimension, targetDimension),
                        frame.direction(Vector3(0.0, 1.0, 0.0), sourceDimension, targetDimension),
                        frame.direction(Vector3(0.0, 0.0, 1.0), sourceDimension, targetDimension),
                    )
                } else {
                    org.cbi.orbitalis.core.orbit.Rotation3.IDENTITY
                }
            val offset =
                if (frame != null && sourceDimension != targetDimension) {
                    frame
                        .state(
                            org.cbi.orbitalis.core.planet
                                .CartesianState(Vector3(player.x, player.y, player.z), Vector3.ZERO),
                            sourceDimension,
                            targetDimension,
                        ).velocity * 0.05
                } else {
                    Vector3.ZERO
                }
            val p = position(state)
            (player as EntityMovementExtension).`sable$setTrackingSubLevel`(null)
            (player as EntityMovementExtension).`sable$setLastTrackingSubLevelID`(null)
            val motionToken =
                if (preserveClientMotion) {
                    org.cbi.orbitalis.minecraft.flight.PlayerFlightMotion
                        .capture(player)
                } else {
                    java.util.UUID(0L, 0L)
                }
            val moved =
                player.teleportTo(
                    world,
                    p.x,
                    p.y,
                    p.z,
                    emptySet(),
                    state.getDouble("yaw").toFloat(),
                    state.getDouble("pitch").toFloat(),
                )
            if (moved) {
                player.deltaMovement = Vec3(state.getDouble("vx"), state.getDouble("vy"), state.getDouble("vz"))
                player.fallDistance = 0f
                org.cbi.orbitalis.minecraft.flight.PlayerFlightMotion
                    .restore(player, player.deltaMovement, motionToken, rotation, offset)
            }
            return moved
        }

        private fun savedPointer(body: ServerSubLevel): CompoundTag =
            CompoundTag().also { tag ->
                body.lastSerializationPointer?.let { pointer ->
                    tag.putInt("x", pointer.chunkPos().x)
                    tag.putInt("z", pointer.chunkPos().z)
                    tag.putInt("storage", pointer.storageIndex().toInt())
                    tag.putInt("index", pointer.subLevelIndex().toInt())
                }
            }

        private fun verifyCargo(
            source: CompoundTag,
            destination: CompoundTag,
        ) {
            if (destination.contains("orbitalis_attached")) {
                check(
                    source.get("orbitalis_attached") == destination.get("orbitalis_attached"),
                ) { "Attached metadata changed during reconstruction" }
            }
            val a = source.getCompound("plot").getCompound("chunks")
            val b = destination.getCompound("plot").getCompound("chunks")
            check(a.allKeys == b.allKeys) { "Reconstructed chunk set differs" }
            for (key in a.allKeys) {
                val ac = a.getCompound(key)
                val bc = b.getCompound(key)
                SableShipSnapshot.verifyBlockEntities(ac.getList("block_entities", 10), bc.getList("block_entities", 10))
                val sections = ac.getCompound("sections")
                for (section in sections.allKeys) {
                    check(
                        sections.getCompound(section).get("block_states") ==
                            bc.getCompound("sections").getCompound(section).get("block_states"),
                    ) { "Blocks changed during reconstruction" }
                }
            }
        }
    }
}
