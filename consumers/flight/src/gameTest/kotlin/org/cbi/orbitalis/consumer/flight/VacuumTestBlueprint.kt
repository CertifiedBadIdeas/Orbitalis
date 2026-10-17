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

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Unit
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import org.cbi.orbitalis.minecraft.space.SpaceWorlds

/** Physics-only fixtures build inside an existing native plot; vacuum never gets static blocks. */
object VacuumTestBlueprint {
    fun create(
        level: ServerLevel,
        origin: BlockPos,
        function: String,
    ): ServerSubLevel {
        require(SpaceWorlds.objects(level))
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        val pose = Pose3d().also { it.position().set(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble()) }
        val body = container.allocateNewSubLevel(pose) as ServerSubLevel
        container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
        try {
            val address = body.plot.centerBlock
            // Both blueprints fit within six blocks of their origin. Do not allocate an
            // extra ring: native rocket control deliberately caps its plot chunk budget.
            for (x in ((address.x - 6) shr 4)..((address.x + 6) shr 4)) {
                for (z in ((address.z - 6) shr 4)..((address.z + 6) shr 4)) {
                    body.plot.newEmptyChunk(ChunkPos(x, z))
                }
            }
            val source =
                level.server
                    .createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(Vec3.atLowerCornerOf(address))
                    .withSuppressedOutput()
            level.server.commands.performPrefixedCommand(source, "function orbitalis_flight_test:$function")
            check(body.plot.loadedChunks.any { it.chunk.blockEntities.isNotEmpty() }) { "Blueprint did not populate its physical plot" }
            body.updateMergedMassData(1.0f)
            body.updateBoundingBox()
            body.forceUpdateGlobalBounds()
            body.updateLastPose()
            return body
        } catch (failure: Throwable) {
            discardGlue(body)
            container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE)
            if (!body.isRemoved) container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
            throw failure
        }
    }

    fun discardGlue(body: ServerSubLevel) {
        body.level
            .getAllEntities()
            .filterIsInstance<SuperGlueEntity>()
            .filter { body.plot.contains(it.position()) }
            .forEach { it.discard() }
    }
}
