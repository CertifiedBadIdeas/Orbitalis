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

package org.cbi.orbitalis.neoforge.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.minecraft.compat.voxy.WORLDGEN_FIND_WORK
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenBorderScope
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenCompletion
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenProgress
import org.cbi.orbitalis.minecraft.compat.voxy.WorldgenSurfaceBounds
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["com.ethan.voxyworldgenv2.core.ChunkGenerationManager"], remap = false)
internal abstract class VoxyWorldgenBorderMixin : WorldgenCompletion {
    @Shadow
    abstract override fun isChunkCompleted(
        level: ServerLevel,
        position: ChunkPos,
    ): Boolean

    @WrapMethod(method = ["dispatchGeneration"])
    private fun orbitalisRestoreSearchScope(
        players: List<ServerPlayer>,
        limit: Int,
        original: Operation<Boolean>,
    ): Boolean {
        val previous = WorldgenBorderScope.current.get()
        val pending = WorldgenBorderScope.pending.get()
        return try {
            original.call(players, limit)
        } finally {
            if (previous == null) WorldgenBorderScope.current.remove() else WorldgenBorderScope.current.set(previous)
            if (pending == null) WorldgenBorderScope.pending.remove() else WorldgenBorderScope.pending.set(pending)
        }
    }

    @WrapMethod(method = ["restartScan"])
    private fun orbitalisRestoreCountScope(original: Operation<Void>) {
        val previous = WorldgenBorderScope.current.get()
        val counting = WorldgenBorderScope.counting.get()
        try {
            original.call()
        } finally {
            if (previous == null) WorldgenBorderScope.current.remove() else WorldgenBorderScope.current.set(previous)
            if (counting == null) WorldgenBorderScope.counting.remove() else WorldgenBorderScope.counting.set(counting)
        }
    }

    @Inject(
        method = ["restartScan"],
        at = [
            At(
                value = "INVOKE",
                target = "Lcom/ethan/voxyworldgenv2/core/DistanceGraph;countMissingInRange(Lnet/minecraft/world/level/ChunkPos;I)I",
            ),
        ],
    )
    private fun orbitalisSelectCountBounds(
        callback: CallbackInfo,
        @Local player: ServerPlayer,
        @Local radius: Int,
    ) {
        val level = player.serverLevel()
        val bounds = WorldgenSurfaceBounds.get(level)
        if (bounds == null) {
            WorldgenBorderScope.current.remove()
            WorldgenBorderScope.counting.remove()
        } else {
            val completion: WorldgenCompletion = this
            WorldgenBorderScope.current.set(bounds)
            WorldgenBorderScope.counting.set(
                WorldgenProgress(bounds, player.chunkPosition(), radius) { completion.isChunkCompleted(level, it) },
            )
        }
    }

    @Inject(method = ["dispatchGeneration"], at = [At(value = "INVOKE", target = WORLDGEN_FIND_WORK)])
    private fun orbitalisSelectSearchBorder(
        callback: CallbackInfoReturnable<Boolean>,
        @Local player: ServerPlayer,
    ) {
        val level = player.serverLevel()
        val bounds = WorldgenSurfaceBounds.get(level)
        if (bounds == null) WorldgenBorderScope.current.remove() else WorldgenBorderScope.current.set(bounds)
        val completion: WorldgenCompletion = this
        WorldgenBorderScope.pending.set {
            (
                bounds == null ||
                    bounds.intersectsChunk(
                        it.x,
                        it.z,
                    )
            ) && !completion.isChunkCompleted(level, it)
        }
    }
}
