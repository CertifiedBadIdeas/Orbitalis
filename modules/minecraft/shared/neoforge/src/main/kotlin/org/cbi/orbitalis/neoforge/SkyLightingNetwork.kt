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

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.cbi.orbitalis.core.Orbitalis
import org.cbi.orbitalis.minecraft.lighting.SkyLightingData
import org.cbi.orbitalis.minecraft.lighting.SkyLightingPayload

@EventBusSubscriber(modid = Orbitalis.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object SkyLightingNetwork {
    @JvmStatic @SubscribeEvent
    fun register(event: RegisterPayloadHandlersEvent) {
        event.registrar("1").playToClient(SkyLightingPayload.TYPE, SkyLightingPayload.CODEC) { payload, _ ->
            SkyLightingData.clientCosmic = payload.cosmic
        }
    }
}
