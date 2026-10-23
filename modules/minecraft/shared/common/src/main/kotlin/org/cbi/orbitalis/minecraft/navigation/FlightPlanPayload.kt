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

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import org.cbi.orbitalis.core.planet.ManeuverNode
import org.cbi.orbitalis.core.planet.ManeuverPlan
import java.util.UUID

/** Version-two bounded navigation-only plan messages; connection owns player identity. */
data class FlightPlanCommand(
    val token: UUID,
    val ship: UUID,
    val action: Action,
    val revision: Long = 0,
    val plan: ManeuverPlan = ManeuverPlan(0, emptyList()),
) : CustomPacketPayload {
    enum class Action { READ, SAVE }

    init {
        require(revision >= 0)
    }

    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FlightPlanCommand>(ResourceLocation.fromNamespaceAndPath("orbitalis", "flight_plan_command"))
        val CODEC: StreamCodec<FriendlyByteBuf, FlightPlanCommand> =
            object : StreamCodec<FriendlyByteBuf, FlightPlanCommand> {
                override fun encode(
                    b: FriendlyByteBuf,
                    v: FlightPlanCommand,
                ) {
                    b.writeUUID(v.token)
                    b.writeUUID(v.ship)
                    b.writeEnum(v.action)
                    b.writeLong(v.revision)
                    PlanWire.write(b, v.plan)
                }

                override fun decode(b: FriendlyByteBuf) =
                    FlightPlanCommand(b.readUUID(), b.readUUID(), b.readEnum(Action::class.java), b.readLong(), PlanWire.read(b))
            }
    }
}

data class FlightPlanState(
    val token: UUID,
    val ship: UUID,
    val gameTick: Long,
    val revision: Long,
    val plan: ManeuverPlan,
    val error: String = "",
) : CustomPacketPayload {
    init {
        require(gameTick >= 0 && revision >= 0 && error.length <= 160 && error.none { it.isISOControl() })
    }

    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FlightPlanState>(ResourceLocation.fromNamespaceAndPath("orbitalis", "flight_plan_state"))
        val CODEC: StreamCodec<FriendlyByteBuf, FlightPlanState> =
            object : StreamCodec<FriendlyByteBuf, FlightPlanState> {
                override fun encode(
                    b: FriendlyByteBuf,
                    v: FlightPlanState,
                ) {
                    b.writeUUID(v.token)
                    b.writeUUID(v.ship)
                    b.writeLong(v.gameTick)
                    b.writeLong(v.revision)
                    PlanWire.write(b, v.plan)
                    b.writeUtf(v.error, 160)
                }

                override fun decode(b: FriendlyByteBuf) =
                    FlightPlanState(
                        b.readUUID(),
                        b.readUUID(),
                        b.readLong(),
                        b.readLong(),
                        PlanWire.read(b),
                        b.readUtf(160),
                    )
            }
    }
}

private object PlanWire {
    fun write(
        b: FriendlyByteBuf,
        plan: ManeuverPlan,
    ) {
        b.writeLong(plan.epochTick)
        b.writeVarInt(plan.nodes.size)
        plan.nodes.forEach {
            b.writeDouble(it.seconds)
            b.writeDouble(it.prograde)
            b.writeDouble(it.normal)
            b.writeDouble(it.radial)
        }
    }

    fun read(b: FriendlyByteBuf): ManeuverPlan {
        val epoch = b.readLong()
        val count = b.readVarInt()
        require(count in 0..8) { "Too many maneuver nodes" }
        return ManeuverPlan(epoch, List(count) { ManeuverNode(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()) })
    }
}

object FlightPlanReceiver {
    var accept: (FlightPlanState) -> Unit = {}
}
