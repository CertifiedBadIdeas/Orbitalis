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

package org.cbi.orbitalis.neoforge.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferTeleportState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Observe vanilla teleport completion without introducing a second acknowledgment protocol. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerTeleportMixin implements ShipTransferTeleportState {
    @Accessor("awaitingPositionFromClient")
    protected abstract Vec3 orbitalis$getAwaitingPositionFromClient();

    @Override
    public boolean orbitalisHasPendingTeleport() {
        return orbitalis$getAwaitingPositionFromClient() != null;
    }
}
