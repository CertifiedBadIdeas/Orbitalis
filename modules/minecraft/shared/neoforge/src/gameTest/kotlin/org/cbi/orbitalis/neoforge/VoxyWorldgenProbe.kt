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

package org.cbi.orbitalis.neoforge

import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenBorderScope
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenProgress
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenSurfaceBounds
import org.cbi.orbitalis.minecraft.innerplanets.NativeInnerPlanets
import org.cbi.orbitalis.minecraft.moon.MoonWorld
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain
import java.util.UUID

/** Explicit opt-in native pinned-mod test; absence of the requested optional mod is a failure. */
object VoxyWorldgenProbe {
    fun verify(helper: GameTestHelper) {
        val type = Class.forName("com.ethan.voxyworldgenv2.core.DistanceGraph")
        val graph = type.getConstructor().newInstance()
        val count = type.getMethod("countMissingInRange", ChunkPos::class.java, Int::class.javaPrimitiveType)
        val mark = type.getMethod("markChunkCompleted", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val find = type.getMethod("findWork", ChunkPos::class.java, Int::class.javaPrimitiveType, Set::class.java)
        val completed = mutableSetOf(ChunkPos(-1, 0), ChunkPos(0, 0), ChunkPos(2, 1), ChunkPos(500, 500))
        completed.forEach { mark.invoke(graph, it.x, it.z) }
        val before = count.invoke(graph, ChunkPos(0, 0), 512) as Int
        for (bounds in listOf(
            FiniteSurfaceTerrain.Bounds(-9, -1, 23, 77),
            FiniteSurfaceTerrain.Bounds(-513, -77, 777, 1025),
        )) {
            for (center in listOf(ChunkPos(0, 0), ChunkPos(-5, 2))) {
                for (radius in listOf(0, 1, 15, 32, 64, 128, 512)) {
                    fun eligible(pos: ChunkPos): Boolean {
                        val dx = pos.x.toLong() - center.x
                        val dz = pos.z.toLong() - center.z
                        return bounds.intersectsChunk(pos.x, pos.z) && dx * dx + dz * dz <= radius.toLong() * radius
                    }
                    val expected = mutableSetOf<ChunkPos>()
                    for (x in Math.floorDiv(bounds.minX, 16)..Math.floorDiv(bounds.maxX - 1, 16)) {
                        for (z in Math.floorDiv(bounds.minZ, 16)..Math.floorDiv(bounds.maxZ - 1, 16)) {
                            val pos = ChunkPos(x, z)
                            if (eligible(pos) && pos !in completed) expected.add(pos)
                        }
                    }
                    val actual =
                        WorldgenBorderScope.count(bounds, WorldgenProgress(bounds, center, radius) { it in completed }) {
                            count.invoke(graph, center, radius) as Int
                        }
                    helper.assertTrue(
                        actual == expected.size,
                        "Pinned Voxy graph count differs: $actual != ${expected.size}, $bounds, $center, $radius",
                    )
                    val tracked = mutableSetOf<Long>()
                    val dispatched = mutableSetOf<ChunkPos>()
                    WorldgenBorderScope.current.set(bounds)
                    WorldgenBorderScope.pending.set { eligible(it) && it !in completed }
                    try {
                        while (true) {
                            val batch = (find.invoke(graph, center, radius, tracked) as? List<*>) ?: break
                            helper.assertTrue(batch.isNotEmpty(), "Empty native graph batch")
                            batch.forEach { helper.assertTrue(dispatched.add(it as ChunkPos), "Native graph dispatched duplicate chunk") }
                        }
                    } finally {
                        WorldgenBorderScope.current.remove()
                        WorldgenBorderScope.pending.remove()
                    }
                    helper.assertTrue(dispatched == expected, "Pinned Voxy graph dispatched exterior chunks or missed eligible work")
                }
            }
        }
        helper.assertTrue((count.invoke(graph, ChunkPos(0, 0), 512) as Int) == before, "Scoped Voxy queries persisted false completion")
        val finalBounds = FiniteSurfaceTerrain.Bounds(-9, -1, 23, 77)
        for (x in Math.floorDiv(finalBounds.minX, 16)..Math.floorDiv(finalBounds.maxX - 1, 16)) {
            for (z in Math.floorDiv(finalBounds.minZ, 16)..Math.floorDiv(finalBounds.maxZ - 1, 16)) {
                completed.add(ChunkPos(x, z))
                mark.invoke(graph, x, z)
            }
        }
        val exhausted =
            WorldgenBorderScope.count(finalBounds, WorldgenProgress(finalBounds, ChunkPos(0, 0), 512) { it in completed }) {
                count.invoke(graph, ChunkPos(0, 0), 512) as Int
            }
        helper.assertTrue(exhausted == 0, "Completed finite footprint still reports remaining exterior work")
        helper.assertTrue(
            (count.invoke(graph, ChunkPos(0, 0), 512) as Int) > 0,
            "Exterior chunks became fake completed after footprint exhaustion",
        )
        verifyManagerScopes(helper)
        println("Orbitalis pinned WorldGenV2 graph probe passed: finite dispatch, exact progress, circle/partial edges, no fake completion")
    }

    private fun verifyManagerScopes(helper: GameTestHelper) {
        val managerType = Class.forName("com.ethan.voxyworldgenv2.core.ChunkGenerationManager")
        val manager = managerType.getMethod("getInstance").invoke(null)

        fun method(name: String) = managerType.getDeclaredMethod(name).apply { isAccessible = true }
        val trackerType = Class.forName("com.ethan.voxyworldgenv2.core.PlayerTracker")
        val tracker = trackerType.getMethod("getInstance").invoke(null)
        val states = managerType.getDeclaredField("dimensionStates").apply { isAccessible = true }
        val config = Class.forName("com.ethan.voxyworldgenv2.core.Config").getField("DATA").get(null)
        val radius = config.javaClass.getField("generationRadius").getInt(config)
        // This disposable GameTest server has no players. Suspend only its optional worker so a
        // synthetic progress subscriber cannot dispatch terrain work or require a network connection.
        helper.assertTrue(
            (trackerType.getMethod("getPlayers").invoke(tracker) as Collection<*>).isEmpty(),
            "Voxy fixture requires an empty tracker",
        )
        method("stopWorker").invoke(manager)
        try {
            for (key in listOf(Level.OVERWORLD, MoonWorld.KEY) + NativeInnerPlanets.dimensions) {
                val world = checkNotNull(helper.level.server.getLevel(key))
                val bounds = checkNotNull(WorldgenSurfaceBounds.get(world))
                val player =
                    ServerPlayer(
                        helper.level.server,
                        world,
                        GameProfile(UUID.randomUUID(), "VoxyBoundProbe"),
                        ClientInformation.createDefault(),
                    )
                player.setPos(0.0, 160.0, 0.0)
                trackerType.getMethod("addPlayer", ServerPlayer::class.java).invoke(tracker, player)
                try {
                    method("restartScan").invoke(manager)
                    val state = (states.get(manager) as Map<*, *>)[key]
                    val remaining =
                        state!!
                            .javaClass
                            .getDeclaredField("remainingInRadius")
                            .apply {
                                isAccessible = true
                            }.get(state) as java.util.concurrent.atomic.AtomicInteger
                    var expected = 0
                    for (x in Math.floorDiv(bounds.minX, 16)..Math.floorDiv(bounds.maxX - 1, 16)) {
                        for (z in Math.floorDiv(bounds.minZ, 16)..Math.floorDiv(bounds.maxZ - 1, 16)) {
                            if (x.toLong() * x + z.toLong() * z <= radius.toLong() * radius) expected++
                        }
                    }
                    helper.assertTrue(
                        remaining.get() == expected,
                        "Native manager lost current dimension bounds: $key ${remaining.get()} != $expected",
                    )
                    helper.assertTrue(
                        WorldgenBorderScope.current.get() == null && WorldgenBorderScope.counting.get() == null,
                        "Native manager leaked count context",
                    )
                } finally {
                    trackerType.getMethod("removePlayer", ServerPlayer::class.java).invoke(tracker, player)
                }
            }
        } finally {
            method("startWorker").invoke(manager)
        }
    }
}
