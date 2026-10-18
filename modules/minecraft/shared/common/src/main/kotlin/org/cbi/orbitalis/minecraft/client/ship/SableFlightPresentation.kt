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

package org.cbi.orbitalis.minecraft.client.ship

import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.minecraft.client.Minecraft
import net.minecraft.core.SectionPos
import org.cbi.orbitalis.minecraft.integration.sable.SableShipContact
import java.util.UUID

object SableFlightPresentation {
    private fun body(): ClientSubLevel? = Minecraft.getInstance().player?.let { SableShipContact.resolve(it) as? ClientSubLevel }

    fun ship(): UUID? = body()?.uniqueId

    fun ready(id: UUID): Boolean {
        val body = body()?.takeIf { it.uniqueId == id && it.isFinalized } ?: return false
        val bounds = body.plot.boundingBox
        val x0 = SectionPos.blockToSectionCoord(bounds.minX())
        val x1 = SectionPos.blockToSectionCoord(bounds.maxX())
        val y0 = SectionPos.blockToSectionCoord(bounds.minY())
        val y1 = SectionPos.blockToSectionCoord(bounds.maxY())
        val z0 = SectionPos.blockToSectionCoord(bounds.minZ())
        val z1 = SectionPos.blockToSectionCoord(bounds.maxZ())
        if ((x1.toLong() - x0 + 1) * (y1.toLong() - y0 + 1) * (z1.toLong() - z0 + 1) !in 1..4096) return false
        val render = body.renderData ?: return false
        // Hidden sections may legitimately remain uncompiled; wait for a drawable native scene,
        // rather than blocking the reveal on geometry outside the current camera's visibility.
        if (render.visibleSectionCount == 0) return false
        for (x in x0..x1) {
            for (y in y0..y1) {
                for (z in z0..z1) {
                    if (render.isSectionCompiled(x, y, z)) return true
                }
            }
        }
        return false
    }
}
