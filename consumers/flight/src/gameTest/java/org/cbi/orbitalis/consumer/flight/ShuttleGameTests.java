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

package org.cbi.orbitalis.consumer.flight;

import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cbi.orbitalis.core.planet.Vector3;
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class ShuttleGameTests {
    /** Uses the actual player blueprint and native assembler, including Create glue. */
    private static final class Shuttle implements AutoCloseable {
        final ServerLevel level;
        final BlockPos origin = new BlockPos(1400, 180, 8);
        final Set<UUID> previous = new HashSet<>();
        final Set<UUID> owned = new HashSet<>();
        final List<Long> forcedChunks = new ArrayList<>();
        final List<Long> launchChunks = new ArrayList<>();
        final Set<UUID> glue = new HashSet<>();
        boolean closed;
        ServerSubLevel body;
        AutopilotSession session;

        Shuttle(GameTestHelper helper, ServerLevel level) {
            this.level = level;
            var container = SubLevelContainer.getContainer(level);
            for (var existing : container.getAllSubLevels()) previous.add(existing.getUniqueId());
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo test) {}
                public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo test, GameTestInfo retry, GameTestRunner runner) {}
            });
            for (int x = (origin.getX() - 10) >> 4; x <= (origin.getX() + 10) >> 4; x++)
                for (int z = (origin.getZ() - 10) >> 4; z <= (origin.getZ() + 10) >> 4; z++) {
                    var key = net.minecraft.world.level.ChunkPos.asLong(x, z);
                    launchChunks.add(key);
                    if (!level.getForcedChunks().contains(key)) {
                        level.setChunkForced(x, z, true);
                        forcedChunks.add(key);
                    }
                    level.getChunk(x, z);
                }
        }

        boolean ready() {
            level.resetEmptyTime();
            return launchChunks.stream().allMatch(level::areEntitiesLoaded) && level.isPositionEntityTicking(origin);
        }

        void assemble(GameTestHelper helper) {
            var container = SubLevelContainer.getContainer(level);
            var box = new AABB(Vec3.atLowerCornerOf(origin.offset(-10, 0, -10)), Vec3.atLowerCornerOf(origin.offset(11, 11, 11)));
            // The disposable GameTest launch area may contain data from an earlier failed run.
            for (var e : level.getEntitiesOfClass(com.simibubi.create.content.contraptions.glue.SuperGlueEntity.class, box)) e.discard();
            var source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atLowerCornerOf(origin)).withSuppressedOutput();
            level.getServer().getCommands().performPrefixedCommand(source,
                "fill ~-10 ~ ~-10 ~10 ~10 ~10 minecraft:air");
            level.getServer().getCommands().performPrefixedCommand(source,
                "function orbitalis_flight_test:shuttle");
            for (var e : level.getEntitiesOfClass(com.simibubi.create.content.contraptions.glue.SuperGlueEntity.class, box)) glue.add(e.getUUID());
            helper.assertTrue(glue.size() == 1, "Fixture glue missing after entity chunks became ready");
            helper.assertTrue(com.simibubi.create.content.contraptions.glue.SuperGlueEntity.isGlued(level,
                origin.offset(0, 3, 0), net.minecraft.core.Direction.EAST, null), "Blueprint glue does not bind cabin floor");
            var assembler = level.getBlockEntity(origin.offset(0, 4, 0));
            helper.assertTrue(assembler instanceof PhysicsAssemblerBlockEntity, "Fixture assembler missing");
            ((PhysicsAssemblerBlockEntity) assembler).assembleOrDisassemble();
            var created = container.getAllSubLevels().stream().filter(b -> !previous.contains(b.getUniqueId())).toList();
            helper.assertTrue(created.size() == 1, "Native fixture assembly did not create one ship");
            body = (ServerSubLevel) created.getFirst();
            for (var b : created) owned.add(b.getUniqueId());
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        }

        void checkLayout(GameTestHelper helper) {
            helper.assertTrue(!body.isRemoved(), "Native connectivity removed/split the fixture");
            var blocks = body.getPlot().getLoadedChunks().stream()
                .flatMap(h -> h.getChunk().getBlockEntities().values().stream()).toList();
            var engines = blocks.stream().filter(b -> b instanceof CreativeThrusterBlockEntity).toList();
            helper.assertTrue(engines.size() == 24, "Fixture lost engines: " + engines.size());
            helper.assertTrue(engines.stream().allMatch(b -> ((CreativeThrusterBlockEntity) b).isController()),
                "Native connectivity merged independent engine controllers");
            var cargo = blocks.stream().filter(b -> b instanceof ChestBlockEntity).map(b -> (ChestBlockEntity) b).toList();
            helper.assertTrue(cargo.size() == 4 && cargo.stream().allMatch(c -> c.getItem(0).getCount() == 16 &&
                c.getItem(0).is(net.minecraft.world.item.Items.COBBLESTONE)), "Fixture lost cargo sentinels");
            helper.assertTrue(SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                .filter(b -> !previous.contains(b.getUniqueId())).count() == 1, "Fixture split into multiple bodies");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (session != null) session.close();
            var container = SubLevelContainer.getContainer(level);
            for (var b : List.copyOf(container.getAllSubLevels())) if (owned.contains(b.getUniqueId()) || owned.contains(b.getSplitFromSubLevel())) {
                container.removeForceLoadTicket(b, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
                container.removeSubLevel(b, SubLevelRemovalReason.REMOVED);
            }
            for (var id : glue) {
                var entity = level.getEntity(id);
                if (entity != null) entity.discard();
            }
            for (long key : forcedChunks) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key),
                net.minecraft.world.level.ChunkPos.getZ(key), false);
            forcedChunks.clear();
        }
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 1000, batch = "shuttle_hold")
    public static void shuttleAssemblyAndHold(GameTestHelper helper) {
        var shuttle = new Shuttle(helper, helper.getLevel().getServer().getLevel(FlightTestSpace.INSTANCE.getSURFACE()));
        var physics = SubLevelPhysicsSystem.require(shuttle.level);
        class Sequence implements Runnable {
            int tick;
            int assembledAt;
            Quaterniond attitude;
            public void run() {
                shuttle.level.resetEmptyTime();
                tick++;
                if (assembledAt == 0) {
                    helper.assertTrue(tick < 200, "Timed out waiting for native entity chunks");
                    if (shuttle.ready()) {
                        shuttle.assemble(helper);
                        assembledAt = tick;
                    }
                    helper.runAfterDelay(1, () -> this.run());
                    return;
                }
                int flightTick = tick - assembledAt;
                if (flightTick == 10) {
                    shuttle.checkLayout(helper);
                    var start = AutopilotSession.position(shuttle.body);
                    attitude = new Quaterniond(shuttle.body.logicalPose().orientation());
                    shuttle.session = new AutopilotSession(shuttle.body, new Vector3(start.getX(), start.getY() + 16, start.getZ()));
                    // Initial test disturbance only; all recovery is powered by the installed engines.
                    var handle = physics.getPhysicsHandle(shuttle.body);
                    double mass = shuttle.body.getMassTracker().getMass();
                    handle.applyLinearImpulse(new Vector3d(mass * 2, 0, mass));
                    handle.applyAngularImpulse(new Vector3d(mass * 0.1, mass * 0.15, mass * 0.1));
                }
                if (flightTick >= 10) shuttle.session.update();
                if (flightTick == 800) {
                    shuttle.checkLayout(helper);
                    var handle = physics.getPhysicsHandle(shuttle.body);
                    helper.assertTrue(AutopilotSession.position(shuttle.body).minus(shuttle.session.target).length() < 1,
                        "Shuttle ascent/hold failed: " + shuttle.session.status());
                    helper.assertTrue(handle.getLinearVelocity(new Vector3d()).length() < 0.4, "Shuttle did not settle translation");
                    helper.assertTrue(handle.getAngularVelocity(new Vector3d()).length() < 0.04, "Shuttle did not settle rotation");
                    double angle = new Quaterniond(shuttle.body.logicalPose().orientation()).conjugate().mul(attitude).angle();
                    helper.assertTrue(Math.min(angle, Math.PI * 2 - angle) < 0.08, "Shuttle attitude did not recover");
                    System.out.println("ORBITALIS_SHUTTLE " + shuttle.session.status());
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, () -> this.run());
            }
        }
        helper.runAfterDelay(1, new Sequence());
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 600, batch = "shuttle_gravity")
    public static void shuttleGravityProfile(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var surface = new Shuttle(helper, server.getLevel(FlightTestSpace.INSTANCE.getSURFACE()));
        var orbit = new Shuttle(helper, server.getLevel(FlightTestSpace.INSTANCE.getORBIT()));
        var craft = List.of(surface, surface, surface, surface, orbit);
        var points = List.of(new Vector3(0, 240, 0), new Vector3(1600, 240, 0),
            new Vector3(1600, 2064, 0), new Vector3(1600, 6064, 0), new Vector3(1600, 5808, 0));
        var measured = new ArrayList<Vector3>();
        class Trial implements Runnable {
            int index;
            public void run() {
                var shuttle = craft.get(index);
                shuttle.checkLayout(helper);
                var handle = SubLevelPhysicsSystem.require(shuttle.level).getPhysicsHandle(shuttle.body);
                // Arrange independent trial initial conditions; no state corrections during measurement.
                var delta = points.get(index).minus(AutopilotSession.position(shuttle.body));
                handle.teleport(new Vector3d(shuttle.body.logicalPose().position()).add(delta.getX(), delta.getY(), delta.getZ()),
                    shuttle.body.logicalPose().orientation());
                handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),
                    handle.getAngularVelocity(new Vector3d()).negate());
                helper.runAfterDelay(8, () -> {
                    var position = AutopilotSession.position(shuttle.body);
                    var global = FlightTestSpace.INSTANCE.globalPosition(shuttle.level, position);
                    var expected = FlightTestSpace.INSTANCE.getGravity().acceleration(global.minus(FlightTestSpace.INSTANCE.getCENTRE()));
                    var velocity = handle.getLinearVelocity(new Vector3d());
                    helper.runAfterDelay(20, () -> {
                        var actual = AutopilotSession.vector(handle.getLinearVelocity(new Vector3d()).sub(velocity));
                        // Exactly one second of native integration; field variation is tiny over this fall.
                        helper.assertTrue(actual.minus(expected).length() < 0.05,
                            "Native gravity mismatch at " + global + ": measured=" + actual + ", field=" + expected);
                        helper.assertTrue(handle.getAngularVelocity(new Vector3d()).length() < 0.01, "Gravity introduced torque");
                        helper.assertTrue(shuttle.body.getPlot().getLoadedChunks().stream()
                            .flatMap(h -> h.getChunk().getBlockEntities().values().stream())
                            .filter(b -> b instanceof CreativeThrusterBlockEntity)
                            .allMatch(b -> ((CreativeThrusterBlockEntity) b).getCurrentThrust() == 0), "Free-fall trial has engine output");
                        measured.add(actual);
                        System.out.println("ORBITALIS_SHUTTLE_GRAVITY frame=" + shuttle.level.dimension().location() +
                            " global=" + global + " field=" + expected + " measured=" + actual);
                        if (++index < points.size()) helper.runAfterDelay(1, () -> this.run());
                        else {
                            helper.assertTrue(Math.abs(measured.get(1).getX()) < 0.02, "Near-surface offset produced lateral gravity");
                            helper.assertTrue(measured.get(2).getX() < -0.01, "Transition field lacks centreward lateral force");
                            helper.assertTrue(measured.get(3).length() < measured.get(1).length() * 0.5, "High field did not weaken");
                            helper.assertTrue(measured.get(3).minus(measured.get(4)).length() < 0.03, "Frame translation changed gravity");
                            helper.succeed();
                        }
                    });
                });
            }
        }
        class Prepare implements Runnable {
            int attempts;
            public void run() {
                helper.assertTrue(++attempts < 200, "Timed out waiting for native entity chunks");
                if (surface.ready() && orbit.ready()) {
                    surface.assemble(helper);
                    orbit.assemble(helper);
                    helper.runAfterDelay(10, new Trial());
                } else helper.runAfterDelay(1, () -> this.run());
            }
        }
        helper.runAfterDelay(1, new Prepare());
    }
}
