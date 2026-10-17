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

import net.minecraft.world.level.ChunkPos
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** WorldGenV2 2.4.2 interrupts its worker on shutdown, but its graph search ignores it. */
@Pseudo
@Mixin(targets = ["com.ethan.voxyworldgenv2.core.DistanceGraph"], remap = false)
internal abstract class VoxyWorldgenCancellationMixin {
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
