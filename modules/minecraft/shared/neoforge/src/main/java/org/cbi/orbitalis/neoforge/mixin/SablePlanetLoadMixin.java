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

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.cbi.orbitalis.minecraft.integration.sable.SablePlanetCollisionPipeline;
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer", remap = false)
public abstract class SablePlanetLoadMixin {
    @WrapMethod(method = "fullyLoad")
    private static ServerSubLevel orbitalis$fixedChartLoad(ServerLevel level, SubLevelData data, Operation<ServerSubLevel> original) {
        CompoundTag tag = data.fullTag().getCompound("user_data");
        boolean previous = SablePlanetCollisionPipeline.constructing();
        boolean frame = level.dimension().equals(PlanetLabCoordinates.INSTANCE.getDIMENSION()) && tag.getInt("orbitalis_cube_frame_version") == 1
            && net.minecraft.core.Direction.byName(tag.getString("orbitalis_cube_face")) != null;
        if (frame) SablePlanetCollisionPipeline.constructing(true);
        try { return original.call(level, data); }
        finally { SablePlanetCollisionPipeline.constructing(previous); }
    }
}
