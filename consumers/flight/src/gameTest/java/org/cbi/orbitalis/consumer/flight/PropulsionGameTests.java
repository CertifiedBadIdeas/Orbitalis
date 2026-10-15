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

import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlock;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cbi.orbitalis.core.planet.Vector3;
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = FlightConsumerMod.ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public final class PropulsionGameTests {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) throws Exception {
        GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(System.getProperty("orbitalis.consumerGameTestReport"))));
        event.register(PropulsionGameTests.class);
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis")
    public static void flightToolsAvailableByDefault(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (var name : List.of("setup", "build")) {
            helper.assertTrue(server.getFunctions().get(ResourceLocation.parse("orbitalis_flight_test:" + name)).isPresent(),
                "Built-in flight function missing or failed to parse: " + name);
        }
        var root = server.getCommands().getDispatcher().getRoot();
        var flight = root.getChild("orbitalis").getChild("flight");
        var autopilot = flight.getChild("autopilot");
        helper.assertTrue(autopilot != null, "Consumer autopilot commands missing");
        for (var name : List.of("hold", "altitude", "status", "stop")) {
            helper.assertTrue(autopilot.getChild(name) != null, "Consumer autopilot command missing: " + name);
        }
        helper.succeed();
    }

    private static final class Rig implements AutoCloseable {
        final ServerLevel level;
        final ServerSubLevel body;
        final List<CreativeThrusterBlockEntity> engines = new ArrayList<>();
        AutopilotSession session;
        Rig(GameTestHelper helper, double x) { this(helper, x, Direction.values()); }
        Rig(GameTestHelper helper, double x, Direction[] faces) {
            level = helper.getLevel().getServer().getLevel(FlightTestSpace.INSTANCE.getSURFACE());
            var container = SubLevelContainer.getContainer(level);
            var pose = new Pose3d();
            pose.position().set(x, 240, 0);
            body = (ServerSubLevel) container.allocateNewSubLevel(pose);
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo test) {}
                public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo test, GameTestInfo retry, GameTestRunner runner) {}
            });
            body.getPlot().newEmptyChunk(body.getPlot().getCenterChunk());
            var accessor = body.getPlot().getEmbeddedLevelAccessor();
            // Native connectivity must see one actual ship, not 24 disconnected engines.
            for (int xBlock = -2; xBlock <= 2; xBlock++) for (int yBlock = -2; yBlock <= 2; yBlock++)
                for (int zBlock = -2; zBlock <= 2; zBlock++)
                    helper.assertTrue(accessor.setBlock(new BlockPos(xBlock, yBlock, zBlock),
                        Blocks.OAK_PLANKS.defaultBlockState(), 3), "Could not place connected core");
            // All 24 engines stay within one plot chunk, with unobstructed outward exhaust.
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createpropulsion:creative_thruster"));
            helper.assertTrue(block != Blocks.AIR, "Pinned creative engine is missing");
            for (Direction face : faces) {
                for (int a : new int[]{-2, 2}) for (int b : new int[]{-2, 2}) {
                    BlockPos p = switch (face.getAxis()) {
                        case X -> new BlockPos(face.getStepX() * 3, a, b);
                        case Y -> new BlockPos(a, face.getStepY() * 3, b);
                        case Z -> new BlockPos(a, b, face.getStepZ() * 3);
                    };
                    helper.assertTrue(accessor.setBlock(p, block.defaultBlockState()
                        .setValue(AbstractThrusterBlock.FACING, face.getOpposite()), 3), "Could not place engine");
                    engines.add((CreativeThrusterBlockEntity) accessor.getBlockEntity(p));
                }
            }
            body.updateLastPose();
            container.addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);

        }
        void configure() {
            // Set up the test vehicle through native configuration, never alter config during control.
            engines.clear();
            for (var holder : body.getPlot().getLoadedChunks()) for (var be : holder.getChunk().getBlockEntities().values()) {
                if (be instanceof CreativeThrusterBlockEntity e) engines.add(e);
            }
            for (var e : engines) e.setThrustConfig(9);
        }
        @Override public void close() {
            if (session != null) session.close();
            if (!body.isRemoved()) {
                var container = SubLevelContainer.getContainer(level);
                container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            }
        }
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 1000, batch = "propulsion_hold")
    public static void nativePropulsionHoldAndCutoff(GameTestHelper helper) {
        var rig = new Rig(helper, 1200);
        var physics = SubLevelPhysicsSystem.require(rig.level);
        class Sequence implements Runnable {
            int tick;
            Vector3 target;
            Quaterniond attitude;
            double peakOutput;
            public void run() {
                rig.level.resetEmptyTime();
                tick++;
                if (tick == 10) {
                    rig.configure();
                    var start = AutopilotSession.position(rig.body);
                    target = new Vector3(start.getX(), start.getY() + 12, start.getZ());
                    attitude = new Quaterniond(rig.body.logicalPose().orientation());
                    helper.assertTrue(rig.engines.size() == 24, "Native connectivity split the engine rig");
                    System.out.println("ORBITALIS_AUTOPILOT rig mass=" + rig.body.getMassTracker().getMass() + " engines=" + rig.engines.size());
                    rig.session = new AutopilotSession(rig.body, target);
                    // A one-time test disturbance; all subsequent correction comes from real engines.
                    var handle = physics.getPhysicsHandle(rig.body);
                    handle.applyLinearImpulse(new Vector3d(rig.body.getMassTracker().getMass() * 2, 0, 0));
                    handle.applyAngularImpulse(new Vector3d(0, rig.body.getMassTracker().getMass() * 0.15, 0));
                }
                if (tick >= 10 && tick < 800) {
                    rig.session.update();
                    for (var e : rig.engines) peakOutput = Math.max(peakOutput, e.getCurrentThrust());
                }
                if (tick % 100 == 0) System.out.println("ORBITALIS_AUTOPILOT tick=" + tick + " " + rig.session.status() +
                    " v=" + physics.getPhysicsHandle(rig.body).getLinearVelocity(new Vector3d()) +
                    " omega=" + physics.getPhysicsHandle(rig.body).getAngularVelocity(new Vector3d()));
                if (tick == 799) {
                    var handle = physics.getPhysicsHandle(rig.body);
                    helper.assertTrue(peakOutput > 0, "No native engine output occurred");
                    helper.assertTrue(AutopilotSession.position(rig.body).minus(target).length() < 1.0, "Powered ascent/hold did not reach target: " + rig.session.status());
                    helper.assertTrue(handle.getLinearVelocity(new Vector3d()).length() < 0.4, "Hold did not settle velocity");
                    helper.assertTrue(handle.getAngularVelocity(new Vector3d()).length() < 0.04, "Native differential thrust did not settle rotation");
                    double angle = new Quaterniond(rig.body.logicalPose().orientation()).conjugate().mul(attitude).angle();
                    helper.assertTrue(Math.min(angle, Math.PI * 2 - angle) < 0.08, "Attitude did not recover");
                    rig.session.close();
                    for (var e : rig.engines) {
                        var saved = e.saveWithFullMetadata(rig.level.registryAccess());
                        helper.assertTrue(saved.getFloat("DigitalInput") == 0 && saved.getInt("ControlMode") == 0, "Stop left an engine latched");
                    }
                }
                if (tick == 830) {
                    helper.assertTrue(rig.engines.stream().allMatch(e -> e.getCurrentThrust() == 0), "Native engine output did not fade to zero");
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, () -> this.run());
            }
        }
        helper.runAfterDelay(1, new Sequence());
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 80, batch = "propulsion_preflight")
    public static void preflightRejectsUnsafeEngines(GameTestHelper helper) {
        var rig = new Rig(helper, -1200);
        helper.runAfterDelay(10, () -> {
            rig.configure();
            var engine = rig.engines.getFirst();
            engine.setRedstoneInput(15);
            boolean rejected = false;
            try { rig.session = new AutopilotSession(rig.body, AutopilotSession.position(rig.body)); }
            catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains("inputs off"); }
            helper.assertTrue(rejected, "Active manual input was not rejected");
            helper.assertTrue(rig.engines.stream().allMatch(e -> e.saveWithFullMetadata(rig.level.registryAccess()).getInt("ControlMode") == 0), "Preflight failure took partial engine ownership");
            engine.setRedstoneInput(0);
            engine.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL);
            engine.setDigitalInput(0.75f);
            var saved = engine.saveWithFullMetadata(rig.level.registryAccess());
            engine.loadWithComponents(saved, rig.level.registryAccess());
            helper.assertTrue(engine.saveWithFullMetadata(rig.level.registryAccess()).getInt("ControlMode") == 0 && engine.getPower() == 0,
                "Pinned native reload retained active peripheral ownership");
            helper.succeed();
        });
    }

    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 80, batch = "propulsion_authority")
    public static void rejectsMissingLiftAuthority(GameTestHelper helper) {
        var rig = new Rig(helper, 600, new Direction[]{Direction.EAST});
        helper.runAfterDelay(10, () -> {
            rig.configure();
            boolean rejected = false;
            try { rig.session = new AutopilotSession(rig.body, AutopilotSession.position(rig.body)); }
            catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains("cannot balance gravity"); }
            helper.assertTrue(rejected, "A ship with only horizontal engines passed hover preflight");
            helper.assertTrue(rig.engines.stream().allMatch(e -> e.getPower() == 0 &&
                e.saveWithFullMetadata(rig.level.registryAccess()).getInt("ControlMode") == 0),
                "Missing authority rejection acquired an engine");
            helper.succeed();
        });
    }
}
