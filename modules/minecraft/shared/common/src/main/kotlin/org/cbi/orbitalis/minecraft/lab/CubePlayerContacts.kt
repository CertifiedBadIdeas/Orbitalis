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

package org.cbi.orbitalis.minecraft.lab

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Optional moving-world contact boundary. Terrain stays owned by planetary locomotion. */
interface CubePlayerContacts {
    data class Contact(
        val motion: Vec3,
        val carry: Vec3 = Vec3.ZERO,
        val grounded: Boolean = false,
        val verticalCollision: Boolean = false,
        val horizontalCollision: Boolean = false,
    )

    fun collide(
        entity: Entity,
        requested: Vec3,
    ): Contact

    fun clear(
        entity: Entity,
        bounds: AABB,
    ): Boolean

    /** Resolve a world-space support probe into native block coordinates, or defer to parent terrain. */
    fun supportPosition(
        entity: Entity,
        probe: Vec3,
    ): BlockPos? = null

    companion object {
        var adapter: CubePlayerContacts? = null
    }
}
