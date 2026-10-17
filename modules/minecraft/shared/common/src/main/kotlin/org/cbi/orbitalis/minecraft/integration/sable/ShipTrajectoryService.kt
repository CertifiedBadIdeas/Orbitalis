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

import com.mojang.logging.LogUtils
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import org.cbi.orbitalis.minecraft.navigation.TrajectorySnapshot
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Server-owned subscriptions. One calculation in flight, no queue of mutable world work. */
class ShipTrajectoryService(
    private val server: MinecraftServer,
    private val publish: (ServerPlayer, TrajectorySnapshot?) -> Unit,
) : AutoCloseable {
    private class Session {
        var ship: UUID? = null
        var dimension: net.minecraft.resources.ResourceLocation? = null
        var generation = 0L
        var requested = Long.MIN_VALUE
        var result: TrajectorySnapshot? = null
    }

    private class Pending(
        val player: UUID,
        val session: Session,
        val generation: Long,
        val future: Future<TrajectorySnapshot>,
    )

    private val sessions = linkedMapOf<UUID, Session>()
    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { task -> Thread(task, "Orbitalis coast prediction").apply { isDaemon = true } },
        )
    private var pending: Pending? = null
    private var ticks = 0L

    fun enable(player: ServerPlayer) {
        require(player.uuid in sessions || sessions.size < 16) { "Trajectory subscription limit reached" }
        sessions.getOrPut(player.uuid, ::Session)
    }

    fun disable(player: ServerPlayer) {
        sessions.remove(player.uuid)
        cancel(player.uuid)
        publish(player, null)
    }

    private fun cancel(player: UUID) {
        if (pending?.player == player) {
            pending?.future?.cancel(true)
            executor.purge()
            pending = null
        }
    }

    fun latest(player: ServerPlayer): TrajectorySnapshot? =
        sessions[player.uuid]?.result?.takeIf {
            it.dimension == player.level().dimension().location() && player.level().gameTime - it.sampledTick in 0L..40L &&
                tracked(player)?.uniqueId == it.ship
        }

    /** Capture, identity checks and publication run only on the logical server thread. */
    fun tick() {
        ticks++
        for ((id, session) in sessions.toMap()) {
            val player = server.playerList.getPlayer(id)
            if (player == null) {
                sessions.remove(id)
                cancel(id)
                continue
            }
            val body = tracked(player)
            val dimension = player.level().dimension().location()
            if (body?.uniqueId != session.ship || dimension != session.dimension) {
                cancel(id)
                session.generation++
                session.ship = body?.uniqueId
                session.dimension = dimension
                session.result = null
                session.requested = Long.MIN_VALUE
                publish(player, null)
            }
        }
        pending?.let { task ->
            if (!task.future.isDone) return
            pending = null
            val player = server.playerList.getPlayer(task.player)
            try {
                val result = task.future.get()
                if (player != null && sessions[task.player] === task.session && task.generation == task.session.generation &&
                    task.session.ship == result.ship && task.session.dimension == result.dimension &&
                    player.level().gameTime - result.sampledTick in 0L..40L &&
                    tracked(player)?.uniqueId == result.ship && player.level().dimension().location() == result.dimension
                ) {
                    task.session.result = result
                    publish(player, result)
                }
            } catch (error: java.util.concurrent.CancellationException) {
                // Cancellation is expected on explicit disable/shutdown.
            } catch (error: java.util.concurrent.ExecutionException) {
                LogUtils.getLogger().warn("Coast prediction failed", error.cause)
            }
        }
        val candidate =
            sessions.entries
                .filter {
                    it.value.ship != null &&
                        (it.value.requested == Long.MIN_VALUE || ticks - it.value.requested >= 20)
                }.minByOrNull { it.value.requested } ?: return
        val player = server.playerList.getPlayer(candidate.key) ?: return
        val body = tracked(player) ?: return
        val session = candidate.value
        session.requested = ticks
        val request = ShipCoastForecast.capture(body)
        if (request == null) {
            session.result = null
            publish(player, null)
            return
        }
        pending = Pending(player.uuid, session, session.generation, executor.submit<TrajectorySnapshot> { request.calculate() })
    }

    private fun tracked(player: ServerPlayer): ServerSubLevel? {
        if (!player.isAlive || !FlatPlanetSpace.contains(player.level())) return null
        val body = SableShipContact.resolve(player) as? ServerSubLevel
        return body?.takeIf {
            !it.isRemoved && it.level === player.level() && it.massTracker.centerOfMass != null &&
                it.massTracker.mass > 0.0
        }
    }

    override fun close() {
        pending?.future?.cancel(true)
        executor.shutdownNow()
        pending = null
        sessions.clear()
    }
}
