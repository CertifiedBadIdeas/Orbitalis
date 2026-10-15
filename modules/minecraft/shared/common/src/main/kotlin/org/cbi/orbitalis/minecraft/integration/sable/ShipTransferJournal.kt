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

import dev.ryanhcode.sable.util.SableNBTUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** Shared by live snapshot handling and every persisted-manifest reader. */
internal object ShipTransferAttachments {
    fun supports(id: String): Boolean = id == "create:super_glue" || id == "simulated:honey_glue"
}

/** Authority is durable before either copy is discarded. DONE snapshots are never replayed. */
enum class ShipTransferPhase { PREPARED, COMMITTED, DONE }

data class ShipTransferRecord(
    val transaction: UUID,
    val ship: UUID,
    val pilot: UUID,
    val sourceIsOrbit: Boolean,
    val source: CompoundTag,
    val destination: CompoundTag,
    val sourcePilot: CompoundTag,
    val destinationPilot: CompoundTag,
    val sourcePointer: CompoundTag,
    val phase: ShipTransferPhase,
    val pilotPending: Boolean = false,
    val recoveredSource: Boolean = false,
) {
    fun encode(): CompoundTag =
        CompoundTag().also {
            it.putInt("version", 1)
            it.putUUID("transaction", transaction)
            it.putUUID("ship", ship)
            it.putUUID("pilot", pilot)
            it.putBoolean("source_is_orbit", sourceIsOrbit)
            // A completed checkpoint needs only a receipt; never retain stale cargo as a backup.
            it.put(
                "source",
                if (phase ==
                    ShipTransferPhase.DONE
                ) {
                    CompoundTag().also { receipt ->
                        receipt.putUUID("uuid", ship)
                        receipt.put("pose", source.getCompound("pose").copy())
                    }
                } else {
                    source.copy()
                },
            )
            it.put(
                "destination",
                if (phase ==
                    ShipTransferPhase.DONE
                ) {
                    CompoundTag().also { receipt ->
                        receipt.putUUID("uuid", ship)
                        receipt.put("pose", destination.getCompound("pose").copy())
                    }
                } else {
                    destination.copy()
                },
            )
            it.put("source_pilot", sourcePilot.copy())
            it.put("destination_pilot", destinationPilot.copy())
            it.put("source_pointer", sourcePointer.copy())
            it.putString("phase", phase.name)
            it.putBoolean("pilot_pending", pilotPending)
            it.putBoolean("recovered_source", recoveredSource)
        }

    companion object {
        fun decode(tag: CompoundTag): ShipTransferRecord {
            require(tag.getInt("version") == 1) { "Unsupported ship transfer journal version" }
            require(tag.contains("source_is_orbit", 1) && tag.contains("pilot_pending", 1) && tag.contains("recovered_source", 1)) {
                "Missing transfer flags"
            }
            val phase = ShipTransferPhase.valueOf(tag.getString("phase"))
            val ship = tag.getUUID("ship")
            val source = tag.getCompound("source")
            val destination = tag.getCompound("destination")
            require(source.getUUID("uuid") == ship && destination.getUUID("uuid") == ship) { "Journal ship identity mismatch" }
            for (snapshot in if (phase == ShipTransferPhase.DONE) emptyList() else listOf(source, destination)) {
                require(snapshot.contains("plot", 10) && snapshot.contains("pose", 10) && snapshot.contains("world_bounds", 10)) {
                    "Incomplete native ship snapshot"
                }
                require(snapshot.contains("exact_linear", 10) && snapshot.contains("exact_angular", 10)) { "Missing exact kinematics" }
                require(snapshot.contains("orbitalis_attached", 9)) { "Missing attached entity manifest" }
                val attached = snapshot.getList("orbitalis_attached", 10)
                require(attached.size <= 64) { "Too many attached metadata entities" }
                for (entity in attached) {
                    val data = entity as CompoundTag
                    require(
                        ShipTransferAttachments.supports(data.getString("id")) && data.hasUUID("UUID") && !data.contains("Passengers"),
                    ) {
                        "Invalid attached entity manifest"
                    }
                }
                val plot = snapshot.getCompound("plot")
                for (key in listOf(
                    "plot_x",
                    "plot_z",
                    "log_size",
                    "data_version",
                )) {
                    require(plot.contains(key, 99)) { "Missing native plot metadata" }
                }
                require(plot.getInt("plot_x") >= 0 && plot.getInt("plot_z") >= 0 && plot.getCompound("chunks").size() in 1..4) {
                    "Invalid native plot bounds"
                }
                val pose = snapshot.getCompound("pose")
                val vectors =
                    listOf(
                        pose.getCompound("position"),
                        pose.getCompound("rotation_point"),
                        snapshot.getCompound("exact_linear"),
                        snapshot.getCompound("exact_angular"),
                    )
                for (vector in vectors) {
                    for (axis in listOf("x", "y", "z")) {
                        require(vector.contains(axis, 99) && vector.getDouble(axis).isFinite()) { "Invalid native vector" }
                    }
                }
                val q = pose.getCompound("orientation")
                for (axis in listOf(
                    "x",
                    "y",
                    "z",
                    "w",
                )) {
                    require(q.contains(axis, 99) && q.getDouble(axis).isFinite()) { "Invalid orientation" }
                }
                val bounds = SableNBTUtils.readBoundingBox(snapshot.getCompound("world_bounds"))
                require(
                    listOf(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()).all {
                        it.isFinite()
                    },
                ) { "Invalid world bounds" }
                require(
                    bounds.maxX() >= bounds.minX() && bounds.maxY() >= bounds.minY() && bounds.maxZ() >= bounds.minZ() &&
                        bounds.maxX() - bounds.minX() <= 32 &&
                        bounds.maxZ() - bounds.minZ() <= 32,
                ) { "Unbounded snapshot" }
                val decoded = SableNBTUtils.readPose3d(pose)
                require(
                    kotlin.math.abs(decoded.position().x) <= 2048 && kotlin.math.abs(decoded.position().z) <= 2048 &&
                        decoded.position().y in 64.0..16256.0,
                ) { "Pose outside diagnostic bounds" }
                require(kotlin.math.abs(decoded.orientation().lengthSquared() - 1.0) < 1e-6) { "Invalid rotation magnitude" }
            }
            for (key in listOf("source_pilot", "destination_pilot")) {
                require(tag.contains(key, 10)) { "Missing pilot snapshot" }
                val pilot = tag.getCompound(key)
                for (axis in listOf("x", "y", "z", "vx", "vy", "vz", "yaw", "pitch")) {
                    require(pilot.contains(axis, 99) && pilot.getDouble(axis).isFinite()) { "Invalid pilot state" }
                }
            }
            return ShipTransferRecord(
                tag.getUUID("transaction"),
                ship,
                tag.getUUID("pilot"),
                tag.getBoolean("source_is_orbit"),
                source.copy(),
                destination.copy(),
                tag.getCompound("source_pilot").copy(),
                tag.getCompound("destination_pilot").copy(),
                tag.getCompound("source_pointer").copy(),
                phase,
                tag.getBoolean("pilot_pending"),
                tag.getBoolean("recovered_source"),
            )
        }
    }
}

/** Only immutable/copied NBT crosses to this worker. Native world/storage APIs stay on the server. */
class ShipTransferJournal(
    private val folder: Path,
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "orbitalis-transfer-journal").apply { isDaemon = true } }

    fun read(): CompletableFuture<List<ShipTransferRecord>> =
        CompletableFuture.supplyAsync({
            if (!Files.exists(folder)) {
                emptyList()
            } else {
                Files.list(folder).use { paths ->
                    paths
                        .filter { it.fileName.toString().endsWith(".nbt") }
                        .sorted()
                        .map { path ->
                            require(Files.size(path) <= MAX_BYTES) { "Oversized transfer journal: $path" }
                            val record = ShipTransferRecord.decode(NbtIo.readCompressed(path, NbtAccounter.create(MAX_BYTES)))
                            require(path.fileName.toString() == "${record.ship}.nbt") { "Journal file identity mismatch" }
                            record
                        }.toList()
                }
            }
        }, worker)

    fun write(record: ShipTransferRecord): CompletableFuture<Unit> {
        val tag = record.encode()
        return CompletableFuture.supplyAsync({
            Files.createDirectories(folder)
            FileChannel.open(folder.parent, StandardOpenOption.READ).use { it.force(true) }
            val target = folder.resolve("${record.ship}.nbt")
            val temporary = folder.resolve("${record.ship}.${UUID.randomUUID()}.tmp")
            try {
                NbtIo.writeCompressed(tag, temporary)
                require(Files.size(temporary) <= MAX_BYTES) { "Transfer snapshot is too large" }
                // Validate the decompressed bound as well; compressed size alone does not bound NBT.
                ShipTransferRecord.decode(NbtIo.readCompressed(temporary, NbtAccounter.create(MAX_BYTES)))
                FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                FileChannel.open(folder, StandardOpenOption.READ).use { it.force(true) }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }, worker)
    }

    override fun close() {
        worker.shutdown()
    }

    companion object {
        private const val MAX_BYTES = 16L * 1024 * 1024
    }
}
