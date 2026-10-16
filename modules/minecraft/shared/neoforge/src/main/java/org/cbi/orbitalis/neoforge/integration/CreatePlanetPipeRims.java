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

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import org.cbi.orbitalis.minecraft.lab.PlanetLabCoordinates;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;

/** A disposable rendering projection; native Create owns every pipe connection and fluid. */
public final class CreatePlanetPipeRims {
    public static final String KEY = "OrbitalisPlanetRims";
    private CreatePlanetPipeRims() {}

    public interface Data {
        int orbitalis$getPipeRims();
    }

    /** Version 1: six two-bit modes in native Direction data order; zero means native rendering. */
    public static int read(CompoundTag packet) {
        CompoundTag tag = packet.getCompound(KEY);
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                || !tag.contains("Modes", Tag.TAG_INT)) return 0;
        int modes = tag.getInt("Modes");
        return modes >= 0 && modes <= 0xfff ? modes : 0;
    }

    public static void write(CompoundTag packet, FluidTransportBehaviour pipe) {
        int modes = project(pipe);
        packet.remove(KEY);
        if (modes == 0) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putInt("Modes", modes);
        packet.put(KEY, tag);
    }

    public static int project(FluidTransportBehaviour pipe) {
        int modes = 0;
        for (Direction d : Direction.values()) modes |= mode(pipe, pipe.blockEntity.getBlockState(), d) << (d.get3DDataValue() * 2);
        return modes;
    }

    public static FluidTransportBehaviour.AttachmentTypes attachment(FluidTransportBehaviour pipe, BlockState state, Direction d) {
        var level = pipe.blockEntity.getLevel();
        if (level == null || !level.dimension().equals(PlanetLabCoordinates.INSTANCE.getDIMENSION())
                || !pipe.canHaveFlowToward(state, d)) return null;
        int modes = level.isClientSide ? ((Data) pipe).orbitalis$getPipeRims() : mode(pipe, state, d) << (d.get3DDataValue() * 2);
        return decode(modes, d);
    }

    public static FluidTransportBehaviour.AttachmentTypes decode(int modes, Direction d) {
        return switch ((modes >> (d.get3DDataValue() * 2)) & 3) {
            case 1 -> FluidTransportBehaviour.AttachmentTypes.NONE;
            case 2 -> FluidTransportBehaviour.AttachmentTypes.CONNECTION;
            case 3 -> FluidTransportBehaviour.AttachmentTypes.DETAILED_CONNECTION;
            default -> null;
        };
    }

    private static int mode(FluidTransportBehaviour pipe, BlockState state, Direction direction) {
        var body = pipe.blockEntity;
        var level = body.getLevel();
        if (level == null || level.isClientSide || !PlanetNeighbours.active(level)
                || !pipe.canHaveFlowToward(state, direction)) return 0;
        boolean glass = state.getBlock() instanceof GlassFluidPipeBlock;
        boolean regular = FluidPipeBlock.isPipe(state);
        if (!glass && !regular) return 0;
        var edge = PlanetNeighbours.neighbour(level, body.getBlockPos(), direction);
        if (edge == null || !edge.getReady()) return 0;
        var other = level.getBlockState(edge.getPosition());
        boolean otherGlass = other.getBlock() instanceof GlassFluidPipeBlock;
        boolean otherRegular = FluidPipeBlock.isPipe(other);
        // Preserve native mixed-type collars and unsupported attachments.
        if (!(glass && (otherGlass || otherRegular)) && !(regular && otherRegular)) return 0;
        FluidTransportBehaviour peer = BlockEntityBehaviour.get(level, edge.getPosition(), FluidTransportBehaviour.TYPE);
        if (peer == null || !peer.canHaveFlowToward(other, edge.getDirection().getOpposite())) return 0;
        var reverse = PlanetNeighbours.neighbour(level, edge.getPosition(), edge.getDirection().getOpposite());
        if (reverse == null || !reverse.getReady() || !reverse.getPosition().equals(body.getBlockPos())
                || reverse.getDirection() != direction.getOpposite()) return 0;
        if (glass) return 1;
        return FluidPropagator.getStraightPipeAxis(state) == direction.getAxis() ? 2 : 3;
    }
}
