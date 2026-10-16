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

package org.cbi.orbitalis.neoforge.integration;

import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;

/** Address conversion only. All pressure, flow and fluid authority remains in Create. */
public final class CreatePlanetFluid {
    private CreatePlanetFluid() {}
    public static PlanetNeighbours.Edge edge(Level level, BlockFace face) {
        return PlanetNeighbours.neighbour(level, face.getPos(), face.getFace());
    }
    public static BlockPos target(Level level, BlockPos source, Direction direction) {
        var edge = PlanetNeighbours.neighbour(level, source, direction);
        return edge != null && edge.getReady() ? edge.getPosition() : source.relative(direction);
    }
    public static BlockPos target(Level level, BlockFace face) {
        return target(level, face.getPos(), face.getFace());
    }
    public static Direction incoming(Level level, BlockFace face) {
        var edge = edge(level, face);
        return edge != null && edge.getReady() ? edge.getDirection().getOpposite() : face.getOppositeFace();
    }
    public static BlockFace peer(Level level, BlockFace face) {
        return new BlockFace(target(level, face), incoming(level, face));
    }
    /** A query-only view of an edge in the destination frame; never stored as graph identity. */
    public static BlockFace query(PlanetNeighbours.Edge edge) {
        return new BlockFace(edge.getPosition().relative(edge.getDirection().getOpposite()), edge.getDirection());
    }
}
