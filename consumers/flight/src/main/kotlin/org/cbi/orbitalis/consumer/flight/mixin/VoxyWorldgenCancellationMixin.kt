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

package org.cbi.orbitalis.consumer.flight.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.consumer.flight.WorldgenBorderScope
import org.cbi.orbitalis.consumer.flight.WorldgenWorkSearch
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** WorldGenV2 2.4.2 interrupts its worker on shutdown, but its graph search ignores it. */
@Pseudo
@Mixin(targets = ["com.ethan.voxyworldgenv2.core.DistanceGraph"], remap = false)
internal abstract class VoxyWorldgenCancellationMixin {
    @WrapMethod(method = ["findWork"])
    private fun flightFindPendingWork(
        position: ChunkPos,
        radius: Int,
        tracked: MutableSet<Long>,
        original: Operation<List<ChunkPos>?>,
    ): List<ChunkPos>? {
        val pending = WorldgenBorderScope.pending.get() ?: return original.call(position, radius, tracked)
        // Native pruning measures distance in four-chunk cells and can omit valid edge chunks.
        // Search two cells further, then retain the exact requested circle in chunk coordinates.
        val searchRadius = (radius.toLong() + 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return WorldgenWorkSearch.findPending(tracked, { original.call(position, searchRadius, it) }) {
            val dx = it.x.toLong() - position.x
            val dz = it.z.toLong() - position.z
            dx * dx + dz * dz <= radius.toLong() * radius && pending(it)
        }
    }

    @Inject(method = ["getDistSq"], at = [At("HEAD")], cancellable = true)
    private fun flightPruneOutsideBorder(
        x: Int,
        z: Int,
        size: Int,
        playerX: Int,
        playerZ: Int,
        callback: CallbackInfoReturnable<Double>,
    ) {
        if (WorldgenBorderScope.outside(x, z, size)) callback.returnValue = Double.POSITIVE_INFINITY
    }

    @Inject(
        method = ["findWork"],
        at = [
            At(value = "INVOKE", target = "Ljava/util/PriorityQueue;poll()Ljava/lang/Object;"),
            At(value = "INVOKE", target = "Ljava/util/PriorityQueue;add(Ljava/lang/Object;)Z"),
        ],
        cancellable = true,
    )
    private fun flightCancelInterruptedSearch(callback: CallbackInfoReturnable<List<ChunkPos>?>) {
        if (Thread.currentThread().isInterrupted) callback.returnValue = null
    }
}
