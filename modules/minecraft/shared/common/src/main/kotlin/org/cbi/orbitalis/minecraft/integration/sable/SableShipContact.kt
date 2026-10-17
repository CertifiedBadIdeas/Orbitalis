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

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.SubLevel
import net.minecraft.world.entity.Entity

/** Native deck contact or an actual plot-owned mount; proximity alone never selects a ship. */
object SableShipContact {
    fun resolve(entity: Entity): SubLevel? {
        val root = entity.rootVehicle
        val mounted = if (root !== entity) Sable.HELPER.getContaining(root) else null
        val tracked = Sable.HELPER.getTrackingSubLevel(root) ?: Sable.HELPER.getTrackingSubLevel(entity)
        if (mounted != null && tracked != null && mounted !== tracked) return null
        return (mounted ?: tracked)?.takeUnless { it.isRemoved }
    }
}
