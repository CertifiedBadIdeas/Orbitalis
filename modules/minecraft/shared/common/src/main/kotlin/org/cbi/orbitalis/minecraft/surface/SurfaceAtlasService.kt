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

package org.cbi.orbitalis.minecraft.surface

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import org.cbi.orbitalis.core.planet.SurfaceAtlas
import org.cbi.orbitalis.minecraft.flight.FlatPlanetSpace
import java.util.UUID

/** Server-thread owner: two already-loaded chunks/tick, 256 samples/orbital player/tick. */
class SurfaceAtlasService(
    private val server: MinecraftServer,
    private val send: (ServerPlayer, SurfaceAtlasPayload) -> Unit,
) {
    private val queue = linkedSetOf<Long>()
    private val cursors = mutableMapOf<UUID, Long>()
    private val session = UUID.randomUUID()
    private var captureTick = -1
    private var captures = 0

    private fun capture(chunk: LevelChunk) {
        if (captureTick != server.tickCount) {
            captureTick = server.tickCount
            captures = 0
        }
        if (captures >= 2) return
        captures++
        data.capture(server.overworld(), chunk)
    }

    private val data by lazy { SurfaceAtlasData.get(server.overworld()) }

    fun loaded(
        x: Int,
        z: Int,
    ) {
        if (x in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK &&
            z in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK
        ) {
            queue.add(ChunkPos.asLong(x, z))
        }
    }

    fun unloaded(chunk: LevelChunk) {
        queue.remove(chunk.pos.toLong())
        if (chunk.pos.x in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK &&
            chunk.pos.z in SurfaceAtlas.MIN_CHUNK..SurfaceAtlas.MAX_CHUNK
        ) {
            capture(chunk)
        }
    }

    fun logout(player: UUID) {
        cursors.remove(player)
    }

    fun tick() {
        val level = server.overworld()
        repeat(minOf(2, queue.size)) {
            val key = queue.first()
            queue.remove(key)
            val chunk = level.chunkSource.getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key))
            if (chunk != null) {
                capture(chunk)
                queue.add(key)
            }
        }
        for (player in server.playerList.players) {
            if (player.level().dimension() != FlatPlanetSpace.SPACE) continue
            val cursor = cursors[player.uuid]
            val (revision, samples) = data.atlas.after(cursor ?: 0L, 256)
            if (cursor == null || samples.isNotEmpty()) {
                send(player, SurfaceAtlasPayload(session, cursor == null, revision, samples))
                cursors[player.uuid] = revision
            }
        }
    }
}
