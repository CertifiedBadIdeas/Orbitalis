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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.sugar.Local
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.cbi.orbitalis.consumer.flight.WORLDGEN_FIND_WORK
import org.cbi.orbitalis.consumer.flight.WorldgenBorderScope
import org.cbi.orbitalis.minecraft.surface.FiniteSurfaceTerrain
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Pseudo
@Mixin(targets = ["com.ethan.voxyworldgenv2.core.ChunkGenerationManager"], remap = false)
internal abstract class VoxyWorldgenBorderMixin {
    @WrapMethod(method = ["dispatchGeneration"])
    private fun flightRestoreSearchScope(
        players: List<ServerPlayer>,
        limit: Int,
        original: Operation<Boolean>,
    ): Boolean {
        val previous = WorldgenBorderScope.current.get()
        return try {
            original.call(players, limit)
        } finally {
            if (previous == null) WorldgenBorderScope.current.remove() else WorldgenBorderScope.current.set(previous)
        }
    }

    @Inject(
        method = ["dispatchGeneration"],
        at = [At(value = "INVOKE", target = WORLDGEN_FIND_WORK)],
    )
    private fun flightSelectSearchBorder(
        callback: CallbackInfoReturnable<Boolean>,
        @Local player: ServerPlayer,
    ) {
        val bounds = FiniteSurfaceTerrain.bounds(player.serverLevel())
        if (bounds == null) WorldgenBorderScope.current.remove() else WorldgenBorderScope.current.set(bounds)
    }

    @ModifyExpressionValue(
        method = ["dispatchGeneration"],
        at = [At(value = "INVOKE", target = WORLDGEN_FIND_WORK)],
    )
    private fun flightFilterEdgeBatch(batch: List<ChunkPos>?): List<ChunkPos>? {
        val bounds = WorldgenBorderScope.current.get() ?: return batch
        return batch?.filter { bounds.intersectsChunk(it.x, it.z) }?.takeIf { it.isNotEmpty() }
    }
}
