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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.world.level.ChunkPos
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Cold chunks share the native body checkpoint with resident chunks; no independent world journal.
 * All mutations run on the server thread, and user data is replaced rather than mutated after publication.
 * This lifecycle is deliberately opt-in until observer delivery/readiness is installed.
 */
object SablePlanetChunks {
    private const val STORAGE = "orbitalis_planet_cold_chunks"
    private const val VERSION = 1
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val selected = ThreadLocal<Pair<ServerLevelPlot, PlotChunkHolder>?>()

    @JvmStatic fun snapshotHolder(plot: ServerLevelPlot): PlotChunkHolder? = selected.get()?.takeIf { it.first === plot }?.second

    @JvmStatic fun loaded(body: ServerSubLevel) {
        if (body.userDataTag?.contains(STORAGE) == true) {
            storage(body) // Reject unknown schemas before native expansion can replace a cold chunk.
            (body.plot as PlanetPlotAccess).orbitalisDisableExpansion()
        }
    }

    fun coldCount(body: ServerSubLevel): Int = storage(body).getCompound("chunks").size()

    fun isCold(
        body: ServerSubLevel,
        local: ChunkPos,
    ): Boolean = storage(body).getCompound("chunks").contains(local.toLong().toString())

    fun evict(
        body: ServerSubLevel,
        local: ChunkPos,
    ): Boolean {
        checkThread(body)
        check(SablePlanetCollisionPipeline.isStatic(body)) { "Only fixed planet charts support cold chunks" }
        val plot = body.plot
        val holder = plot.getChunkHolder(local) ?: return false
        // A native body with no nonempty resident plot has invalid bounds on full reload.
        if (plot.loadedChunks.count { !it.chunk.isEmpty } <= 1 && !holder.chunk.isEmpty) return false
        // Delivery is installed in a subsequent stage. Never leave an existing observer with stale blocks.
        if (body.trackingPlayers.isNotEmpty()) return false
        check(selected.get() == null)
        val fragment =
            try {
                selected.set(plot to holder)
                plot.save()
            } finally {
                selected.remove()
            }
        validate(body, local, fragment)
        val bytes = ByteArrayOutputStream().also { NbtIo.writeCompressed(fragment, it) }.toByteArray()
        check(bytes.size <= MAX_BYTES) { "Planet chunk exceeds cold snapshot limit" }
        // Check the bounded decoder before releasing the resident authority.
        NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(MAX_BYTES.toLong()))
        val next = storage(body).copy()
        next.getCompound("chunks").putByteArray(local.toLong().toString(), bytes)
        // Native saves cannot interleave this server-thread transition. Keep the snapshot on any detach failure.
        publish(body, next)
        (plot as PlanetPlotAccess).orbitalisDisableExpansion()
        invalidateCollision(body, holder.pos)
        (plot as PlanetPlotAccess).orbitalisDetachChunk(local)
        return true
    }

    fun restore(
        body: ServerSubLevel,
        local: ChunkPos,
    ): Boolean {
        checkThread(body)
        check(SablePlanetCollisionPipeline.isStatic(body))
        val stored = storage(body)
        val key = local.toLong().toString()
        val chunks = stored.getCompound("chunks")
        if (!chunks.contains(key)) return false
        // A resident chunk wins an interrupted in-memory transition; checkpoints themselves are atomic.
        if (body.plot.getChunkHolder(local) == null) {
            val bytes = chunks.getByteArray(key)
            check(bytes.size in 1..MAX_BYTES) { "Invalid cold planet chunk size" }
            val fragment = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.create(MAX_BYTES.toLong()))
            validate(body, local, fragment)
            try {
                body.plot.load(fragment)
                checkNotNull(body.plot.getChunkHolder(local))
            } catch (error: Exception) {
                // A native palette/registry error must not turn a partial restore into authoritative air.
                val global = body.plot.toGlobal(local)
                invalidateCollision(body, global)
                (body.plot as PlanetPlotAccess).orbitalisDetachChunk(local)
                throw IllegalStateException("Could not restore planet chunk $local; cold snapshot retained", error)
            }
        }
        val next = stored.copy()
        next.getCompound("chunks").remove(key)
        publish(body, next)
        return true
    }

    private fun storage(body: ServerSubLevel): CompoundTag {
        val tag = body.userDataTag?.getCompound(STORAGE) ?: CompoundTag()
        if (tag.isEmpty) {
            return CompoundTag().also {
                it.putInt("version", VERSION)
                it.put("chunks", CompoundTag())
            }
        }
        check(tag.getInt("version") == VERSION && tag.contains("chunks", Tag.TAG_COMPOUND.toInt())) {
            "Unsupported or malformed cold planet storage"
        }
        return tag
    }

    private fun publish(
        body: ServerSubLevel,
        storage: CompoundTag,
    ) {
        body.userDataTag = (body.userDataTag?.copy() ?: CompoundTag()).also { it.put(STORAGE, storage) }
    }

    private fun validate(
        body: ServerSubLevel,
        local: ChunkPos,
        fragment: CompoundTag,
    ) {
        val plot = body.plot
        val size = 1 shl (plot as PlanetPlotAccess).orbitalisLogSize()
        check(local.x in 0 until size && local.z in 0 until size) { "Cold chunk outside plot" }
        check(fragment.getInt("data_version") == 1 && fragment.getInt("log_size") == (plot as PlanetPlotAccess).orbitalisLogSize())
        val key = local.toLong().toString()
        val chunks = fragment.getCompound("chunks")
        check(chunks.allKeys == setOf(key)) { "Cold snapshot must describe exactly one chunk" }
        val data = chunks.getCompound(key)
        for (section in data.getCompound("sections").allKeys) {
            check((section.toIntOrNull() ?: -1) in 0 until body.level.sectionsCount) { "Cold section outside build height" }
        }
        val global = plot.toGlobal(local)
        for (name in listOf("block_entities", "block_ticks", "fluid_ticks")) {
            val entries = data.getList(name, Tag.TAG_COMPOUND.toInt())
            for (entry in entries) {
                val item = entry as CompoundTag
                val pos = BlockPos(item.getInt("x"), item.getInt("y"), item.getInt("z"))
                check(ChunkPos(pos) == global && !body.level.isOutsideBuildHeight(pos)) { "Cold entry outside its chunk" }
            }
        }
    }

    private fun invalidateCollision(
        body: ServerSubLevel,
        global: ChunkPos,
    ) {
        val pipeline = checkNotNull(SubLevelContainer.getContainer(body.level)).physicsSystem()?.pipeline ?: return
        for (y in body.level.minSection until body.level.maxSection) pipeline.handleChunkSectionRemoval(global.x, y, global.z)
    }

    private fun checkThread(body: ServerSubLevel) {
        check(body.level.server.isSameThread) { "Planet chunk lifecycle requires the server thread" }
    }
}
