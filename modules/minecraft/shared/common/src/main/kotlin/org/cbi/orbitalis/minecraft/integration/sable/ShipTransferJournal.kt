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
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.orbit.SurfaceFlightFrames
import org.cbi.orbitalis.minecraft.flight.CelestialFlight
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.math.abs

/** Recognize removed routes without registering worlds or reinterpreting their coordinates. */
internal object RetiredFlightData {
    private val orbitalIds = setOf("orbitalis:orbit_test", "orbitalis:space_test")

    fun isDiagnosticJournal(tag: CompoundTag): Boolean =
        when (tag.getInt("version")) {
            1 -> true

            // This version only ever described the retired diagnostic surface.
            2 -> tag.getString("orbital_dimension") in orbitalIds

            3, 4 -> tag.getString("surface_dimension") == "orbitalis:flight_test" && tag.getString("orbital_dimension") in orbitalIds

            else -> false
        }
}

/** Shared by live snapshot handling and every persisted-manifest reader. */
internal object ShipTransferAttachments {
    fun supports(id: String): Boolean = id == "create:super_glue" || id == "simulated:honey_glue"
}

/** Authority is durable before either copy is discarded. DONE snapshots are never replayed. */
enum class ShipTransferPhase { PREPARED, COMMITTED, DONE }

data class ShipTransferRecord(
    val transaction: UUID,
    val ship: UUID,
    val pilot: UUID?,
    val sourceIsOrbit: Boolean,
    val source: CompoundTag,
    val destination: CompoundTag,
    val sourcePilot: CompoundTag,
    val destinationPilot: CompoundTag,
    val sourcePointer: CompoundTag,
    val phase: ShipTransferPhase,
    val pilotPending: Boolean = false,
    val recoveredSource: Boolean = false,
    val orbitalDimension: ResourceKey<Level> = FlatPlanetSpace.SPACE,
    val surfaceDimension: ResourceKey<Level> = Level.OVERWORLD,
    val sourcePassengers: ListTag = ListTag(),
    val destinationPassengers: ListTag = ListTag(),
    val frozenFrame: CelestialFlight? = null,
) {
    fun encode(): CompoundTag =
        CompoundTag().also {
            require(
                surfaceDimension in setOf(Level.OVERWORLD, MoonWorld.KEY) && orbitalDimension == FlatPlanetSpace.SPACE,
            ) { "Invalid transfer route" }
            it.putInt("version", if (frozenFrame != null) 7 else 5)
            if (frozenFrame != null) {
                val sample = frozenFrame
                it.put(
                    "frame",
                    CompoundTag().also { frame ->
                        frame.putInt("version", SolarSystemProfile.VERSION)
                        frame.putLong("tick", sample.frames.tick)
                        frame.putLong("epoch", sample.frames.profile.epochTick)
                        frame.putDouble("gravity", sample.frames.profile.surfaceGravity)
                        frame.putDouble("earth_phase", sample.frames.profile.earthPhase)
                        frame.putDouble("moon_phase", sample.frames.profile.moonPhase)
                        frame.putBoolean("earth_rotates", sample.frames.profile.earthSpinEpochTick != null)
                        sample.frames.profile.earthSpinEpochTick
                            ?.let { frame.putLong("earth_spin_epoch", it) }
                    },
                )
            }
            it.putString("surface_dimension", surfaceDimension.location().toString())
            it.putString("orbital_dimension", orbitalDimension.location().toString())
            it.putUUID("transaction", transaction)
            it.putUUID("ship", ship)
            pilot?.let { id -> it.putUUID("pilot", id) }
            it.put("source_passengers", passengerPayload(sourcePassengers))
            it.put("destination_passengers", passengerPayload(destinationPassengers))
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

    private fun passengerPayload(list: ListTag): ListTag =
        list.copy().also { copy ->
            if (phase == ShipTransferPhase.DONE) copy.forEach { (it as CompoundTag).remove("data") }
        }

    companion object {
        fun decode(tag: CompoundTag): ShipTransferRecord {
            val version = tag.getInt("version")
            require(!RetiredFlightData.isDiagnosticJournal(tag)) {
                "Retired diagnostic ship route; finish recovery with the previous Orbitalis version"
            }
            require(version in 3..7) { "Unsupported ship transfer journal version" }
            val surfaceDimension =
                when (tag.getString("surface_dimension")) {
                    Level.OVERWORLD.location().toString() -> Level.OVERWORLD
                    MoonWorld.KEY.location().toString() -> MoonWorld.KEY
                    else -> throw IllegalArgumentException("Invalid transfer surface")
                }
            require(tag.getString("orbital_dimension") == FlatPlanetSpace.SPACE.location().toString())
            require(version == 7 || surfaceDimension == if (version == 6) MoonWorld.KEY else Level.OVERWORLD)
            val orbitalDimension = FlatPlanetSpace.SPACE
            val frozenFrame =
                if (version >= 6) {
                    require(tag.contains("frame", 10)) { "Missing frozen surface frame" }
                    val frame = tag.getCompound("frame")
                    require(frame.contains("version", 3) && frame.getInt("version") in 1..SolarSystemProfile.VERSION)
                    require(frame.contains("tick", 4) && frame.contains("epoch", 4))
                    require(listOf("gravity", "earth_phase", "moon_phase").all { frame.contains(it, 6) })
                    val tick = frame.getLong("tick")
                    val spinEpoch =
                        if (frame.getInt("version") >= 2) {
                            require(frame.contains("earth_rotates", 1) && frame.getByte("earth_rotates").toInt() in 0..1)
                            if (frame.getBoolean("earth_rotates")) {
                                require(frame.contains("earth_spin_epoch", 4))
                                frame.getLong("earth_spin_epoch").also { require(it in 0..tick) }
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    val profile =
                        SolarSystemProfile(
                            frame.getDouble("gravity"),
                            frame.getLong("epoch"),
                            frame.getDouble("earth_phase"),
                            frame.getDouble("moon_phase"),
                            spinEpoch,
                        )
                    require(tick >= profile.epochTick)
                    CelestialFlight(SurfaceFlightFrames(profile, tick))
                } else {
                    null
                }
            require(tag.contains("source_is_orbit", 1) && tag.contains("pilot_pending", 1) && tag.contains("recovered_source", 1)) {
                "Missing transfer flags"
            }
            val phase = ShipTransferPhase.valueOf(tag.getString("phase"))
            val ship = tag.getUUID("ship")
            val source = tag.getCompound("source")
            val destination = tag.getCompound("destination")
            require(source.getUUID("uuid") == ship && destination.getUUID("uuid") == ship) { "Journal ship identity mismatch" }
            for ((index, snapshot) in (if (phase == ShipTransferPhase.DONE) emptyList() else listOf(source, destination)).withIndex()) {
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
                val orbitalSnapshot = if (index == 0) tag.getBoolean("source_is_orbit") else !tag.getBoolean("source_is_orbit")
                if (version >= 5) {
                    val native =
                        org.cbi.orbitalis.core.planet
                            .Vector3(decoded.position().x, decoded.position().y, decoded.position().z)
                    val dimension = if (orbitalSnapshot) FlatPlanetSpace.SPACE else surfaceDimension
                    val physical =
                        if (frozenFrame != null) {
                            frozenFrame.rootPoint(native, dimension)
                        } else {
                            org.cbi.orbitalis.minecraft.flight.FlightSpaceCoordinates
                                .legacyRootPosition(dimension, native)
                        }
                    require(FlatPlanetSpace.validTransferPosition(physical)) { "Pose outside bounded physical flight coordinates" }
                } else {
                    val heights = if (orbitalSnapshot) 4160.0..20352.0 else 64.0..16256.0
                    require(
                        abs(decoded.position().x) <= FlatPlanetSpace.HALF_SIZE + 32.0 &&
                            abs(decoded.position().z) <= FlatPlanetSpace.HALF_SIZE + 32.0 && decoded.position().y in heights,
                    ) {
                        "Pose outside legacy diagnostic bounds"
                    }
                }
                require(abs(decoded.orientation().lengthSquared() - 1.0) < 1e-6) { "Invalid rotation magnitude" }
            }
            val pilotId = if (tag.hasUUID("pilot")) tag.getUUID("pilot") else null
            require(version >= 4 || pilotId != null) { "Missing legacy pilot" }
            require(pilotId != null || !tag.getBoolean("pilot_pending")) { "Empty craft has no pending pilot" }
            val sourcePassengers =
                if (version >= 4) {
                    tag.get("source_passengers") as? ListTag
                        ?: throw IllegalArgumentException("Missing source travellers")
                } else {
                    ListTag()
                }
            val destinationPassengers =
                if (version >= 4) {
                    tag.get("destination_passengers") as? ListTag
                        ?: throw IllegalArgumentException("Missing destination travellers")
                } else {
                    ListTag()
                }
            if (version >= 4) {
                require(tag.contains("source_passengers", 9) && tag.contains("destination_passengers", 9)) { "Missing travellers" }
                ShipTransferPassengers.validate(sourcePassengers, phase == ShipTransferPhase.DONE)
                ShipTransferPassengers.validate(destinationPassengers, phase == ShipTransferPhase.DONE)
                require(ShipTransferPassengers.ids(sourcePassengers) == ShipTransferPassengers.ids(destinationPassengers)) {
                    "Traveller identities changed"
                }
                if (phase != ShipTransferPhase.DONE) {
                    val expected =
                        if (frozenFrame != null) {
                            val from = if (tag.getBoolean("source_is_orbit")) orbitalDimension else surfaceDimension
                            val to = if (tag.getBoolean("source_is_orbit")) surfaceDimension else orbitalDimension
                            ShipFrameTransform(frozenFrame, from, to).passengers(sourcePassengers, source, destination)
                        } else {
                            ShipTransferPassengers.translated(sourcePassengers, source, destination)
                        }
                    require(expected == destinationPassengers) {
                        "Travelling entity state changed across frames"
                    }
                }
                for (entry in sourcePassengers) {
                    val e = entry as CompoundTag
                    require(!e.getBoolean("player") || e.getUUID("uuid") == pilotId) { "Traveller pilot mismatch" }
                }
            }
            for (key in if (pilotId == null) emptyList() else listOf("source_pilot", "destination_pilot")) {
                require(tag.contains(key, 10)) { "Missing pilot snapshot" }
                val pilot = tag.getCompound(key)
                for (axis in listOf("x", "y", "z", "vx", "vy", "vz", "yaw", "pitch")) {
                    require(pilot.contains(axis, 99) && pilot.getDouble(axis).isFinite()) { "Invalid pilot state" }
                }
            }
            return ShipTransferRecord(
                tag.getUUID("transaction"),
                ship,
                pilotId,
                tag.getBoolean("source_is_orbit"),
                source.copy(),
                destination.copy(),
                tag.getCompound("source_pilot").copy(),
                tag.getCompound("destination_pilot").copy(),
                tag.getCompound("source_pointer").copy(),
                phase,
                tag.getBoolean("pilot_pending"),
                tag.getBoolean("recovered_source"),
                orbitalDimension,
                surfaceDimension,
                sourcePassengers.copy(),
                destinationPassengers.copy(),
                frozenFrame,
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
                            val tag = NbtIo.readCompressed(path, NbtAccounter.create(MAX_BYTES))
                            require(path.fileName.toString() == "${tag.getUUID("ship")}.nbt") { "Journal file identity mismatch" }
                            if (RetiredFlightData.isDiagnosticJournal(tag)) {
                                require(
                                    tag.getString("phase") == "DONE" && tag.contains("pilot_pending", 1) &&
                                        !tag.getBoolean("pilot_pending"),
                                ) {
                                    "Unfinished retired diagnostic transfer in $path; recover with the previous Orbitalis version"
                                }
                                // A completed receipt owns no ship payload; preserve the file without replaying it.
                                null
                            } else {
                                ShipTransferRecord.decode(tag)
                            }
                        }.filter { it != null }
                        .map { checkNotNull(it) }
                        .toList()
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
