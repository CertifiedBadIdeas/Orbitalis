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
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace
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
    private val pauses = mutableMapOf<SubLevelPhysicsSystem, Boolean>()
    private val tickets = mutableSetOf<ServerSubLevel>()
    var error: String? = null
        private set
    val busy: Boolean get() = !startupHandled || active != null || pending != null || waitFor != null || error != null

    fun locks(level: ServerLevel): Boolean = (busy || records.values.any { it.pilotPending }) && SableFlightGravity.isFlightSpace(level)

    private fun level(
        orbit: Boolean,
        record: ShipTransferRecord,
    ): ServerLevel = checkNotNull(server.getLevel(if (orbit) record.orbitalDimension else FlightTestSpace.SURFACE))

    private fun worlds(): List<ServerLevel> =
        listOf(FlightTestSpace.SURFACE, FlightTestSpace.ORBIT, FlightTestSpace.SPACE).map { checkNotNull(server.getLevel(it)) }

    private fun pause() {
        for (world in worlds()) {
            val physics = SubLevelPhysicsSystem.require(world)
            pauses.putIfAbsent(physics, physics.paused)
            physics.paused = true
        }
    }

    private fun resume() {
        ShipTransferTickGate.release(this)
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
        ShipTransferTickGate.hold(this, body.level, body.plot.loadedChunks.map { it.chunk.pos })
    }

    private fun persist(
        record: ShipTransferRecord,
        next: () -> Unit,
    ) {
        pending = journal.write(record)
        continuation = {
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
        next: () -> Unit,
    ) {
        check(movePlayer(player, body.level, state)) { "Pilot dimension transfer was rejected" }

        fun available(): Boolean {
            check(player.isAlive && player.level() === body.level) { "Pilot became unavailable during client handoff" }
            return true
        }
        awaitCondition("Destination ship tracking timed out", { available() && pilotSync.hasShip(player, body) }) {
            // Native tracking has queued the complete ship bundle. This second teleport is
            // ordered after it on TCP, so its vanilla acknowledgment fences client loading.
            check(movePlayer(player, body.level, state)) { "Pilot deck positioning was rejected" }
            awaitCondition("Pilot teleport acknowledgment or deck contact timed out", { available() && pilotSync.isReady(player, body) }) {
                player.deltaMovement = Vec3(state.getDouble("vx"), state.getDouble("vy"), state.getDouble("vz"))
                player.fallDistance = 0f
                next()
            }
        }
    }

    @JvmOverloads
    fun begin(
        player: ServerPlayer,
        body: ServerSubLevel,
        targetDimension: ResourceKey<Level>? = null,
    ) {
        require(!SableCubePlanet.isFrame(body)) { "Planet charts cannot be transferred as ships" }
        require(
            !busy && records.values.none { it.pilotPending },
        ) { "A transfer/recovery is running: ${error ?: "wait for completion"}" }
        require(SableFlightGravity.isFlightSpace(body.level) && player.level() === body.level) { "Use the flight or orbit test space" }
        require(
            server.playerList.players.none {
                it !== player && SableFlightGravity.isFlightSpace(it.serverLevel())
            },
        ) { "Other players must leave the diagnostic spaces" }
        SableShipSnapshot.preflight(body, player)
        val sourceDimension = body.level.dimension()
        val sourceOrbit = sourceDimension != FlightTestSpace.SURFACE
        val orbitalDimension = if (sourceOrbit) sourceDimension else targetDimension ?: FlightTestSpace.ORBIT
        require(orbitalDimension == FlightTestSpace.ORBIT || orbitalDimension == FlightTestSpace.SPACE) { "Unknown orbital route" }
        val destinationDimension = if (sourceOrbit) FlightTestSpace.SURFACE else orbitalDimension
        require(
            targetDimension == null || targetDimension == destinationDimension,
        ) { "Only surface-to-orbital diagnostic routes are supported" }
        val source = SableShipSnapshot.capture(body)
        val delta =
            FlightTestSpace
                .localPosition(
                    destinationDimension,
                    FlightTestSpace.globalPosition(sourceDimension, Vector3.ZERO),
                ).y
        val destination = SableShipSnapshot.translated(source, delta, destinationDimension)
        val target = checkNotNull(server.getLevel(destinationDimension))
        checkDestination(target, destination)
        val sourcePilot = playerState(player)
        val destinationPilot = sourcePilot.copy().also { it.putDouble("y", it.getDouble("y") + delta) }
        require(destinationPilot.getDouble("y") >= 64.0) { "Climb higher before transferring the pilot" }
        val record =
            ShipTransferRecord(
                UUID.randomUUID(),
                body.uniqueId,
                player.uuid,
                sourceOrbit,
                source,
                destination,
                sourcePilot,
                destinationPilot,
                savedPointer(body),
                ShipTransferPhase.PREPARED,
                orbitalDimension = orbitalDimension,
            )
        pause()
        retain(body)
        pilot = player
        active = record
        persist(record) { stage(record, body, player) }
    }

    private fun stage(
        record: ShipTransferRecord,
        source: ServerSubLevel,
        player: ServerPlayer,
    ) {
        try {
            stagePrepared(record, source, player)
        } catch (failure: Exception) {
            abortPrepared(record, player, failure.message ?: "Preparation failed")
        }
    }

    private fun abortPrepared(
        record: ShipTransferRecord,
        player: ServerPlayer,
        reason: String,
    ) {
        // Re-establish source authority durably before discarding a partially staged body.
        persist(record) {
            if (SubLevelContainer.getContainer(level(record.sourceIsOrbit, record))!!.getSubLevel(record.ship) == null) {
                recoverNext()
                return@persist
            }
            val target = SubLevelContainer.getContainer(level(!record.sourceIsOrbit, record))!!
            val staged = target.getSubLevel(record.ship) as? ServerSubLevel
            val cleanup = {
                if (staged != null) {
                    SableShipSnapshot.discardAttachments(staged.level, record.destination)
                    target.removeSubLevel(staged, SubLevelRemovalReason.REMOVED)
                }
                checkpoint()
                persist(record.copy(phase = ShipTransferPhase.DONE, recoveredSource = true)) { resume() }
                player.sendSystemMessage(Component.literal("Transfer cancelled; source retained: $reason"))
            }
            if (staged != null) awaitEntities(listOf(staged), cleanup) else cleanup()
        }
    }

    private fun stagePrepared(
        record: ShipTransferRecord,
        source: ServerSubLevel,
        player: ServerPlayer,
    ) {
        // A block/entity update during the asynchronous prepare must not be silently discarded.
        val changed =
            when {
                source.isRemoved -> "source ship was removed"
                !player.isAlive || player.level() !== source.level -> "pilot became unavailable"
                SableShipSnapshot.capture(source).getCompound("plot") != record.source.getCompound("plot") -> "ship block data changed"
                savedPointer(source) != record.sourcePointer -> "native save pointer changed"
                player.position().distanceTo(position(record.sourcePilot)) > 0.25 -> "pilot moved more than 0.25 m"
                else -> null
            }
        if (changed != null) {
            // No authority change happened: preserve the source's current state and checkpoint it.
            checkpoint()
            persist(record.copy(phase = ShipTransferPhase.DONE)) { resume() }
            player.sendSystemMessage(Component.literal("Transfer cancelled; source retained: $changed."))
            return
        }
        SableShipSnapshot.preflight(source, player)
        val target = level(!record.sourceIsOrbit, record)
        checkDestination(target, record.destination)
        val staged = SableShipSnapshot.load(target, record.destination)
        retain(staged)
        check(staged.uniqueId == record.ship)
        awaitEntities(listOf(staged)) {
            try {
                val current = SableShipSnapshot.capture(source)
                require(!source.isRemoved && player.level() === source.level && player.isAlive) { "Source or pilot became unavailable" }
                require(
                    current.getCompound("plot") == record.source.getCompound("plot") &&
                        current.get("orbitalis_attached") == record.source.get("orbitalis_attached"),
                ) { "Source changed while loading destination" }
                SableShipSnapshot.preflight(source, player)
                SableShipSnapshot.restoreAttachments(staged, record.destination)
                verifyCargo(record.source, SableShipSnapshot.capture(staged))
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
        player: ServerPlayer,
    ) {
        // Sable removal packets carry plot coordinates but no dimension. Send them while
        // the pilot is still in the source world, before the destination respawn packet.
        SableShipSnapshot.discardAttachments(source.level, record.source)
        SubLevelContainer.getContainer(source.level)!!.removeSubLevel(source, SubLevelRemovalReason.REMOVED)
        handoffPilot(player, staged, record.destinationPilot) {
            checkpoint()
            verifySaved(staged)
            persist(record.copy(phase = ShipTransferPhase.DONE)) {
                player.sendSystemMessage(
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
        require(world.noCollision(null, area) && world.getEntities(null, area).isEmpty()) { "Destination is obstructed" }
        require(pose.position().isFinite) { "Invalid destination position" }
    }

    /** Native save APIs own their server-thread checkpoint; journal compression/fsync uses its worker. */
    private fun checkpoint() {
        for (world in worlds()) {
            val container = SubLevelContainer.getContainer(world)!!
            // This is the same native ship checkpoint used by Sable's ServerLevel save hook.
            // A handoff changes native plots/occupancy, not ordinary terrain. Flushing every
            // vanilla chunk visited by a full orbit would stall the server for minutes.
            container.holdingChunkMap.saveAll()
            container.holdingChunkMap.storage.flush()
            world.dataStorage.save()
        }
        server.playerList.saveAll()
    }

    private fun verifySaved(body: ServerSubLevel) {
        val pointer = checkNotNull(body.lastSerializationPointer) { "Native checkpoint did not save the destination" }
        val storage = SubLevelContainer.getContainer(body.level)!!.holdingChunkMap.storage
        val saved = checkNotNull(storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local())) { "Native checkpoint is unreadable" }
        check(saved.uuid() == body.uniqueId) { "Native checkpoint identity mismatch" }
        verifyCargo(SableShipSnapshot.capture(body), saved.fullTag())
    }

    private fun recoverNext() {
        val record = records.values.firstOrNull { it.phase != ShipTransferPhase.DONE }
        if (record == null) {
            val waiting = records.values.filter { it.pilotPending }
            if (waiting.isEmpty()) {
                resume()
            } else {
                // This bounded diagnostic has no offline flight mode. Keep recovery anchors stable.
                for (receipt in waiting) {
                    val world = level(if (receipt.recoveredSource) receipt.sourceIsOrbit else !receipt.sourceIsOrbit, receipt)
                    val snapshot = if (receipt.recoveredSource) receipt.source else receipt.destination
                    retain(checkNotNull(findCopy(world, snapshot, CompoundTag())) { "Recovered ship checkpoint is unavailable" })
                }
                active = null
                pilot = null
            }
            return
        }
        active = record
        val sourceWorld = level(record.sourceIsOrbit, record)
        val destinationWorld = level(!record.sourceIsOrbit, record)
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
        awaitEntities(copies) {
            for ((world, snapshot) in listOf(sourceWorld to record.source, destinationWorld to record.destination)) {
                val pointer = if (world === sourceWorld) record.sourcePointer else CompoundTag()
                SableShipSnapshot.discardAttachments(world, snapshot)
                discardCopies(world, snapshot, pointer)
            }
            val committed = record.phase == ShipTransferPhase.COMMITTED
            val winnerWorld = if (committed) destinationWorld else sourceWorld
            val snapshot = if (committed) record.destination else record.source
            checkDestination(winnerWorld, snapshot)
            val restored = SableShipSnapshot.load(winnerWorld, snapshot)
            retain(restored)
            awaitEntities(listOf(restored)) {
                SableShipSnapshot.restoreAttachments(restored, snapshot)
                val online = server.playerList.getPlayer(record.pilot)
                val finish = {
                    checkpoint()
                    verifySaved(restored)
                    persist(
                        record.copy(phase = ShipTransferPhase.DONE, pilotPending = online == null, recoveredSource = !committed),
                    ) { recoverNext() }
                }
                if (online == null) {
                    finish()
                } else {
                    pilot = online
                    handoffPilot(online, restored, if (committed) record.destinationPilot else record.sourcePilot, finish)
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
        val world = if (record.recoveredSource) level(record.sourceIsOrbit, record) else level(!record.sourceIsOrbit, record)
        active = record
        pilot = player
        pause()
        val snapshot = if (record.recoveredSource) record.source else record.destination
        val body = checkNotNull(findCopy(world, snapshot, CompoundTag())) { "Recovered ship checkpoint is unavailable" }
        retain(body)
        handoffPilot(player, body, state) {
            checkpoint()
            persist(record.copy(pilotPending = false)) { resume() }
        }
    }

    override fun close() {
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
        ): Boolean {
            val p = position(state)
            (player as EntityMovementExtension).`sable$setTrackingSubLevel`(null)
            (player as EntityMovementExtension).`sable$setLastTrackingSubLevelID`(null)
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
