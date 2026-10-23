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

import net.minecraft.server.level.ServerPlayer
import org.cbi.orbitalis.core.planet.ManeuverPlan
import org.cbi.orbitalis.minecraft.map.OrbitalMapHooks
import org.cbi.orbitalis.minecraft.map.OrbitalMapService

/** Server thread only. No ship/chunk wake; revisions protect delayed UI writes. */
object FlightPlanService {
    fun handle(
        player: ServerPlayer,
        request: FlightPlanCommand,
        map: OrbitalMapService,
    ): FlightPlanState? {
        if (!map.subscribed(player, request.token)) return null
        val allowed = map.allowPlanRequest(player)
        if (!allowed && request.action == FlightPlanCommand.Action.READ) return null
        val server = player.server
        val data = FlightPlans.get(server)
        var error = if (allowed) "" else "Wait a moment before changing the plan again"
        try {
            if (allowed && request.action != FlightPlanCommand.Action.READ) {
                require(player.hasPermissions(2) && player.isCreative) { "Test flight plans require creative mode and permission level 2" }
                require(OrbitalMapHooks.objects(server).any { it.id == request.ship }) { "Ship is not in the orbital directory" }
                val current = data.get(player.uuid, request.ship)
                require((current?.revision ?: 0) == request.revision) { "Plan changed; reload before continuing" }
                when (request.action) {
                    FlightPlanCommand.Action.SAVE -> {
                        require(
                            request.plan.epochTick <= server.overworld().gameTime &&
                                server.overworld().gameTime - request.plan.epochTick <= 72000,
                        ) { "Plan epoch expired; start a new plan" }
                        data.put(player.uuid, request.ship, request.revision, request.plan)
                    }
                }
            }
        } catch (failure: IllegalArgumentException) {
            error = failure.message.orEmpty()
        } catch (failure: IllegalStateException) {
            error = failure.message.orEmpty()
        }
        val current = data.get(player.uuid, request.ship)
        return FlightPlanState(
            request.token,
            request.ship,
            server.overworld().gameTime,
            current?.revision ?: 0,
            current?.plan ?: ManeuverPlan(server.overworld().gameTime, emptyList()),
            error
                .filterNot {
                    it.isISOControl()
                }.take(160),
        )
    }
}
