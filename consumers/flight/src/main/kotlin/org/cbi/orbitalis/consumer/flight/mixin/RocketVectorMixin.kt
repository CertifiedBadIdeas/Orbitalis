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

import dev.propulsionteam.propulsionsimulated.content.thruster.vector_thruster.VectorThrusterBlockEntity
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import org.cbi.orbitalis.consumer.flight.RocketSteering
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Does not change redstone signals or persist controller ownership. */
@Mixin(value = [VectorThrusterBlockEntity::class], remap = false)
internal abstract class RocketVectorMixin : RocketSteering {
    @Shadow @JvmField
    protected var targetVectorX: Float = 0.0f

    @Shadow @JvmField
    protected var targetVectorY: Float = 0.0f

    @field:Unique private var rocketOwned = false

    @field:Unique private var rocketX = 0.0f

    @field:Unique private var rocketY = 0.0f

    @Shadow protected abstract fun updateMappedTargets()

    override fun setRocketVector(
        x: Float,
        y: Float,
    ) {
        require(x.isFinite() && y.isFinite() && x in -1.0f..1.0f && y in -1.0f..1.0f)
        rocketX = x
        rocketY = y
        rocketOwned = true
        updateMappedTargets()
    }

    override fun clearRocketVector() {
        rocketOwned = false
        updateMappedTargets()
    }

    @Inject(method = ["updateMappedTargets"], at = [At("TAIL")])
    private fun rocketMappedTargets(callback: CallbackInfo) {
        if (rocketOwned) {
            targetVectorX = rocketX
            targetVectorY = rocketY
        }
    }

    @Inject(method = ["read"], at = [At("TAIL")])
    private fun rocketClearOnRead(
        tag: CompoundTag,
        registries: HolderLookup.Provider,
        clientPacket: Boolean,
        callback: CallbackInfo,
    ) {
        rocketOwned = false
        if (!clientPacket) updateMappedTargets()
    }
}
