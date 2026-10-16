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

package org.cbi.orbitalis.consumer.flight

import com.simibubi.create.content.logistics.vault.ItemVaultBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import org.cbi.orbitalis.minecraft.integration.sable.SableCubePlanet

/** Create acceptance fixture on the production six-chart cube. */
class FaceFrameScene(
    val level: ServerLevel,
) : AutoCloseable {
    val planet = SableCubePlanet(level)
    val bodies get() = planet.bodies

    fun prepare() {
        planet.prepare()
        val vault =
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("create:item_vault"))
                .defaultBlockState()
                .setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X)
        for (body in bodies) for (x in -8..-6) check(body.plot.embeddedLevelAccessor.setBlock(BlockPos(x, 0, -10), vault, 3))
    }

    fun world(
        face: Direction,
        x: Double,
        y: Double,
        z: Double,
    ) = planet.world(face, x, y, z)

    override fun close() = planet.clear()
}
