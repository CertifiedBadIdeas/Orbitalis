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

package org.cbi.orbitalis.minecraft.navigation

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import org.cbi.orbitalis.core.planet.ManeuverNode
import org.cbi.orbitalis.core.planet.ManeuverPlan
import java.util.UUID

/** Personal navigation plans. Persisted intent does not resume engine control after restart. */
class FlightPlans : SavedData() {
    data class Entry(
        val revision: Long,
        val plan: ManeuverPlan,
    )

    private val plans = linkedMapOf<Pair<UUID, UUID>, Entry>()

    fun get(
        owner: UUID,
        ship: UUID,
    ): Entry? = plans[owner to ship]

    fun put(
        owner: UUID,
        ship: UUID,
        expected: Long,
        plan: ManeuverPlan,
    ): Entry {
        val key = owner to ship
        val previous = plans[key]
        require((previous?.revision ?: 0L) == expected) { "Plan changed; reload before saving" }
        require(expected in 0 until Long.MAX_VALUE) { "Invalid revision" }
        require(key in plans || plans.size < 256) { "Flight plan storage is full" }
        val next = Entry(expected + 1, plan.copy(nodes = plan.nodes.toList()))
        plans[key] = next
        setDirty()
        return next
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
    ): CompoundTag {
        tag.putInt("version", 1)
        val list = ListTag()
        for ((key, entry) in plans) {
            val item = CompoundTag()
            item.putUUID("owner", key.first)
            item.putUUID("ship", key.second)
            item.putLong("revision", entry.revision)
            item.putLong("epoch", entry.plan.epochTick)
            val nodes = ListTag()
            for (node in entry.plan.nodes) {
                val n = CompoundTag()
                n.putDouble("time", node.seconds)
                n.putDouble("prograde", node.prograde)
                n.putDouble("normal", node.normal)
                n.putDouble("radial", node.radial)
                nodes.add(n)
            }
            item.put("nodes", nodes)
            list.add(item)
        }
        tag.put("plans", list)
        return tag
    }

    companion object {
        fun get(server: MinecraftServer): FlightPlans =
            server.overworld().dataStorage.computeIfAbsent(Factory(::FlightPlans, ::load, null), "orbitalis_flight_plans")

        fun load(
            tag: CompoundTag,
            registries: HolderLookup.Provider,
        ): FlightPlans {
            require(tag.getInt("version") == 1 && tag.contains("plans", 9)) { "Unsupported flight plan data" }
            val list = tag.get("plans") as ListTag
            require(list.size <= 256 && (list.isEmpty() || list.elementType.toInt() == 10))
            val data = FlightPlans()
            for (raw in list) {
                val item = raw as CompoundTag
                require(
                    item.hasUUID("owner") && item.hasUUID("ship") && item.contains("epoch", 4) && item.contains("revision", 4) &&
                        item.contains("nodes", 9),
                )
                val nodes = item.get("nodes") as ListTag
                require(nodes.size <= 8 && (nodes.isEmpty() || nodes.elementType.toInt() == 10))
                val plan =
                    ManeuverPlan(
                        item.getLong("epoch"),
                        nodes.map { rawNode ->
                            val n = rawNode as CompoundTag
                            require(listOf("time", "prograde", "normal", "radial").all { n.contains(it, 6) })
                            ManeuverNode(n.getDouble("time"), n.getDouble("prograde"), n.getDouble("normal"), n.getDouble("radial"))
                        },
                    )
                val revision = item.getLong("revision")
                require(revision > 0)
                require(
                    data.plans.put(item.getUUID("owner") to item.getUUID("ship"), Entry(revision, plan)) == null,
                ) { "Duplicate flight plan" }
            }
            return data
        }
    }
}

/** Optional engine adapter. Main Orbitalis owns navigation intent, never Propulsion engine classes. */
object FlightPlanHooks {
    var execute: ((ServerPlayer, UUID, ManeuverPlan) -> Unit)? = null
    var stop: ((ServerPlayer, UUID) -> Unit)? = null
    var running: (MinecraftServer, UUID) -> Boolean = { _, _ -> false }
}
