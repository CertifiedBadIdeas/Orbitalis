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
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.cbi.orbitalis.minecraft.planet.PlanetNeighbours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.ArrayList;
import java.util.List;

/** Native Create networks retain actual source addresses, stress, ownership and invalidation. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.RotationPropagator", remap = false)
public abstract class CreatePlanetRotationMixin {
    @Invoker("getAxisModifier")
    public static float orbitalis$axisModifier(KineticBlockEntity body, Direction direction) { throw new AssertionError(); }

    @WrapMethod(method = "getPotentialNeighbourLocations")
    private static List<BlockPos> orbitalis$locations(KineticBlockEntity body, Operation<List<BlockPos>> original) {
        var result = new ArrayList<>(original.call(body));
        for (Direction d : Direction.values()) {
            var edge = PlanetNeighbours.neighbour(body.getLevel(), body.getBlockPos(), d);
            if (edge == null) continue;
            result.remove(body.getBlockPos().relative(d));
            if (edge.getReady() && !result.contains(edge.getPosition())) result.add(edge.getPosition());
        }
        return result;
    }

    @WrapMethod(method = "getRotationSpeedModifier")
    private static float orbitalis$rotation(KineticBlockEntity from, KineticBlockEntity to, Operation<Float> original) {
        var relation = PlanetNeighbours.relation(from.getLevel(), from.getBlockPos(), to.getBlockPos());
        if (relation == null) return original.call(from, to);
        BlockPos delta = relation.getOffset();
        if (delta.distManhattan(BlockPos.ZERO) != 1) return 0;
        Direction direction = Direction.fromDelta(delta.getX(), delta.getY(), delta.getZ());
        Direction targetDirection = relation.toTarget(direction);
        var a = from.getBlockState();
        var b = to.getBlockState();
        if (!(a.getBlock() instanceof IRotate source && b.getBlock() instanceof IRotate target)) return 0;
        boolean shaft = source.hasShaftTowards(from.getLevel(), from.getBlockPos(), a, direction)
            && target.hasShaftTowards(to.getLevel(), to.getBlockPos(), b, targetDirection.getOpposite());
        if (shaft) {
            float targetModifier = orbitalis$axisModifier(to, targetDirection.getOpposite());
            if (targetModifier == 0) return 0;
            int sign = direction.getAxisDirection() == targetDirection.getAxisDirection() ? 1 : -1;
            return sign * orbitalis$axisModifier(from, direction) / targetModifier;
        }
        if (ICogWheel.isSmallCog(a) && ICogWheel.isSmallCog(b)) {
            Direction axis = relation.toSource(Direction.get(Direction.AxisDirection.POSITIVE, target.getRotationAxis(b)));
            if (axis.getAxis() == source.getRotationAxis(a) && direction.getAxis() != axis.getAxis())
                return axis.getAxisDirection() == Direction.AxisDirection.POSITIVE ? -1 : 1;
        }
        // Unknown/custom and diagonal cross-chart transmissions need their own explicit contract.
        return 0;
    }
}
