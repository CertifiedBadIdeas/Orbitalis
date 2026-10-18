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

package org.cbi.orbitalis.minecraft.orbit

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.saveddata.SavedData
import org.cbi.orbitalis.core.orbit.SolarSystemProfile
import org.cbi.orbitalis.core.planet.CartesianState
import org.cbi.orbitalis.core.planet.Vector3
import java.util.UUID

/** Simulation metadata only. Native Sable storage remains the sole owner of blocks and inventories. */
class OrbitalObjectData private constructor(
    private val original: CompoundTag?,
) : SavedData() {
    var clock = 0L
        private set
    var profile: SolarSystemProfile? = null
        private set
    val records = linkedMapOf<UUID, Record>()
    var readable = true
        private set

    data class Pointer(
        val x: Int,
        val z: Int,
        val storage: Short,
        val index: Short,
    )

    data class Record(
        val id: UUID,
        val anchor: UUID,
        val snapshotTick: Long,
        val tick: Long,
        val state: CartesianState,
        val radius: Double,
        val pointer: Pointer? = null,
    ) {
        init {
            require(snapshotTick >= 0 && tick >= snapshotTick)
            require(state.position.length() <= 1e8 && state.velocity.length() <= 10000.0)
            require(radius.isFinite() && radius in 0.0..2048.0)
        }
    }

    fun configureCelestials(
        gravity: Double,
        selectedEarthWidth: Int? = null,
    ): SolarSystemProfile {
        check(readable) { "Unknown or malformed orbital simulation data" }
        val previous = profile
        if (previous == null || previous.surfaceGravity != gravity || previous.earthSpinEpochTick == null ||
            previous.preset != SolarSystemProfile.Preset.CALENDAR || previous.selectedEarthWidth != selectedEarthWidth
        ) {
            val tuned =
                previous?.retune(gravity, clock, SolarSystemProfile.Preset.CALENDAR, selectedEarthWidth)
                    ?: SolarSystemProfile(gravity, clock, selectedEarthWidth = selectedEarthWidth)
            // Enable with the existing orientation; reading old frozen receipts never calls this.
            profile = if (tuned.earthSpinEpochTick == null) tuned.copy(earthSpinEpochTick = clock) else tuned
            setDirty()
        }
        return checkNotNull(profile)
    }

    fun advanceClock() {
        if (readable) {
            clock = Math.addExact(clock, 1)
            setDirty()
        }
    }

    fun put(record: Record) {
        check(readable && record.tick <= clock)
        require(record.id in records || records.size < MAX_OBJECTS)
        records[record.id] = record
        setDirty()
    }

    fun remove(id: UUID) {
        if (readable && records.remove(id) != null) setDirty()
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag {
        if (!readable) return tag.merge(checkNotNull(original).copy())
        tag.putInt("Version", 2)
        tag.putLong("Clock", clock)
        profile?.let { value ->
            tag.put(
                "Celestial",
                CompoundTag().apply {
                    putInt("Version", SolarSystemProfile.VERSION)
                    putInt("Preset", value.preset.version)
                    putInt("EarthWidth", value.selectedEarthWidth ?: 0)
                    putLong("EpochTick", value.epochTick)
                    putDouble("Gravity", value.surfaceGravity)
                    putDouble("EarthPhase", value.earthPhase)
                    putDouble("MoonPhase", value.moonPhase)
                    putBoolean("EarthRotates", value.earthSpinEpochTick != null)
                    value.earthSpinEpochTick?.let { putLong("EarthSpinEpoch", it) }
                },
            )
        }
        tag.put("Objects", ListTag().also { list -> records.values.forEach { list.add(write(it)) } })
        return tag
    }

    companion object {
        const val NAME = "orbitalis_orbital_objects"
        const val KEY = "orbitalis:orbit"
        const val BODY = "orbitalis:overworld"
        const val FRAME = "orbitalis:overworld_body"
        const val MAX_OBJECTS = 4096
        val FACTORY = Factory({ OrbitalObjectData(null) }, { tag, _ -> load(tag) }, null)

        fun load(tag: CompoundTag): OrbitalObjectData {
            val result = OrbitalObjectData(tag.copy())
            try {
                require(tag.getInt("Version") in 1..2 && tag.contains("Clock", Tag.TAG_LONG.toInt()))
                result.clock = tag.getLong("Clock").also { require(it >= 0) }
                if (tag.getInt("Version") == 2 && tag.contains("Celestial")) {
                    require(tag.contains("Celestial", Tag.TAG_COMPOUND.toInt()))
                    val celestial = tag.getCompound("Celestial")
                    require(celestial.getInt("Version") in 1..SolarSystemProfile.VERSION && celestial.contains("EpochTick", 4))
                    require(listOf("Gravity", "EarthPhase", "MoonPhase").all { celestial.contains(it, 6) })
                    val spinEpoch =
                        if (celestial.getInt("Version") >= 3) {
                            require(celestial.contains("EarthRotates", 1) && celestial.getByte("EarthRotates").toInt() in 0..1)
                            if (celestial.getBoolean("EarthRotates")) {
                                require(celestial.contains("EarthSpinEpoch", 4))
                                celestial.getLong("EarthSpinEpoch").also { require(it in 0..result.clock) }
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    result.profile =
                        SolarSystemProfile(
                            celestial.getDouble("Gravity"),
                            celestial.getLong("EpochTick"),
                            celestial.getDouble("EarthPhase"),
                            celestial.getDouble("MoonPhase"),
                            if (celestial.getInt("Version") >= 3) {
                                require(celestial.contains("Preset", 3))
                                SolarSystemProfile.Preset.fromVersion(celestial.getInt("Preset"))
                            } else {
                                SolarSystemProfile.Preset.fromVersion(celestial.getInt("Version"))
                            },
                            spinEpoch,
                            if (celestial.getInt("Version") >= 3) {
                                require(celestial.contains("EarthWidth", 3))
                                celestial.getInt("EarthWidth").takeIf { it != 0 }
                            } else {
                                null
                            },
                        ).also { require(it.epochTick <= result.clock) }
                }
                require(tag.contains("Objects", Tag.TAG_LIST.toInt()))
                val list = tag.getList("Objects", Tag.TAG_COMPOUND.toInt())
                require(list.size <= MAX_OBJECTS && list.size == (tag.get("Objects") as ListTag).size)
                for (value in list) {
                    val record = read(value as CompoundTag)
                    require(record.tick <= result.clock && result.records.put(record.id, record) == null)
                }
            } catch (_: IllegalArgumentException) {
                result.records.clear()
                result.profile = null
                result.readable = false
            }
            return result
        }

        fun write(record: Record): CompoundTag =
            CompoundTag().apply {
                putInt("Version", 1)
                putString("Body", BODY)
                putString("Frame", FRAME)
                putUUID("Id", record.id)
                putUUID("Anchor", record.anchor)
                putLong("SnapshotTick", record.snapshotTick)
                putLong("Tick", record.tick)
                put("Position", vector(record.state.position))
                put("Velocity", vector(record.state.velocity))
                putDouble("Radius", record.radius)
                record.pointer?.let {
                    put(
                        "Pointer",
                        CompoundTag().apply {
                            putInt("X", it.x)
                            putInt("Z", it.z)
                            putShort("Storage", it.storage)
                            putShort("Index", it.index)
                        },
                    )
                }
            }

        fun read(tag: CompoundTag): Record {
            require(tag.getInt("Version") == 1 && tag.getString("Body") == BODY && tag.getString("Frame") == FRAME)
            require(tag.hasUUID("Id") && tag.hasUUID("Anchor"))
            require(tag.contains("SnapshotTick", 4) && tag.contains("Tick", 4) && tag.contains("Radius", 6))
            val pointer =
                if (tag.contains("Pointer")) {
                    val p = tag.getCompound("Pointer")
                    require(p.contains("X", 3) && p.contains("Z", 3) && p.contains("Storage", 2) && p.contains("Index", 2))
                    Pointer(p.getInt("X"), p.getInt("Z"), p.getShort("Storage"), p.getShort("Index"))
                } else {
                    null
                }
            return Record(
                tag.getUUID("Id"),
                tag.getUUID("Anchor"),
                tag.getLong("SnapshotTick"),
                tag.getLong("Tick"),
                CartesianState(readVector(tag.getCompound("Position")), readVector(tag.getCompound("Velocity"))),
                tag.getDouble("Radius"),
                pointer,
            )
        }

        fun vector(value: Vector3): CompoundTag =
            CompoundTag().apply {
                putDouble("X", value.x)
                putDouble("Y", value.y)
                putDouble("Z", value.z)
            }

        fun readVector(tag: CompoundTag): Vector3 {
            require(tag.contains("X", 6) && tag.contains("Y", 6) && tag.contains("Z", 6))
            return Vector3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"))
        }
    }
}
