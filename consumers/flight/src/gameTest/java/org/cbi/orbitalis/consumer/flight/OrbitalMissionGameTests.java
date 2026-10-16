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

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace;
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer;
import org.cbi.orbitalis.minecraft.integration.sable.ShipTransferPilotSync;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@PrefixGameTestTemplate(false)
public final class OrbitalMissionGameTests {
    @GameTest(template = "lab_empty", templateNamespace = "orbitalis", timeoutTicks = 40000, batch = "orbital_mission")
    public static void nativeOrbitalInsertionAndReturn(GameTestHelper helper) throws Exception {
        var rig = new PropulsionGameTests.Rig(helper, -1400);
        var pilot = new InertPilot(helper);
        var frame = FlightTestSpace.INSTANCE;
        var worlds = java.util.List.of(frame.getSURFACE(), frame.getORBIT(), frame.getSPACE()).stream()
            .map(key -> helper.getLevel().getServer().getLevel(key)).toList();
        Map<BlockPos, BlockState> terrain = new HashMap<>();
        for (int x = -1405; x <= -1395; x++) for (int z = -5; z <= 5; z++) {
            var pos = new BlockPos(x, 230, z); terrain.put(pos, rig.level.getBlockState(pos));
            rig.level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        var transfer = new SableShipTransfer(helper.getLevel().getServer(), Files.createTempDirectory("orbitalis-mission-test-"), new ShipTransferPilotSync() {
            public boolean hasShip(ServerPlayer player, ServerSubLevel body) { return true; }
            public boolean isReady(ServerPlayer player, ServerSubLevel body) {
                pilot.acknowledge(body);
                return ShipTransferPilotSync.Companion.getNATIVE().isReady(player, body);
            }
        });
        class Sequence implements Runnable, AutoCloseable {
            OrbitalMission mission;
            LaunchSites.Site site;
            int tick, coastTicks;
            boolean askedToLand, cleaned;
            double firstCoastEnergy, previousCoastAngle, coastAngle;
            java.util.Set<OrbitalMission.Phase> phases = new HashSet<>();
            @Override public void close() {
                if (cleaned) return; cleaned = true;
                if (mission != null) mission.close();
                transfer.close(); pilot.close();
                for (var world : worlds) {
                    var container = SubLevelContainer.getContainer(world);
                    var body = (ServerSubLevel) container.getSubLevel(rig.body.getUniqueId());
                    if (body != null) {
                        container.removeForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
                        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    }
                    SubLevelPhysicsSystem.require(world).setPaused(false);
                }
                terrain.forEach((pos, state) -> rig.level.setBlock(pos, state, 3));
            }
            public void run() {
                tick++;
                for (var world : worlds) world.resetEmptyTime();
                transfer.tick();
                helper.assertTrue(transfer.getError() == null, "Native mission transfer failed: " + transfer.getError());
                if (!transfer.getBusy()) {
                    for (var world : worlds) {
                        var body = (ServerSubLevel) SubLevelContainer.getContainer(world).getSubLevel(rig.body.getUniqueId());
                        if (body != null) {
                            SubLevelContainer.getContainer(world).addForceLoadTicket(body, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
                            if (pilot.player.level() == world) pilot.attach(body);
                        }
                    }
                }
                if (tick == 100) {
                    rig.configure();
                    helper.assertTrue(SubLevelPhysicsSystem.require(rig.level).getPhysicsHandle(rig.body).getLinearVelocity(new Vector3d()).length() < 0.3,
                        "Native rig did not settle on the test terrain");
                    var chestPos = new BlockPos(0, 1, 0);
                    rig.body.getPlot().getEmbeddedLevelAccessor().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
                    ((ChestBlockEntity)rig.body.getPlot().getEmbeddedLevelAccessor().getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                }
                if (tick == 120) {
                    var p = AutopilotSession.position(rig.body);
                    helper.assertTrue(pilot.player.teleportTo(rig.level, p.getX(), p.getY() + 3, p.getZ(), java.util.Set.of(), 0, 0), "Could not place inert pilot");
                    pilot.acknowledge(rig.body);
                    site = new LaunchSites.Site(rig.body.getUniqueId(), p, new Quaterniond(rig.body.logicalPose().orientation()));
                    mission = new OrbitalMission(pilot.player, rig.body, site, transfer, false);
                }
                if (mission != null) {
                    boolean landed = mission.tick(pilot.player);
                    if (phases.add(mission.phase())) System.out.println("ORBITALIS_MISSION tick=" + tick + " " + mission.status());
                    if (tick % 1000 == 0) System.out.println("ORBITALIS_MISSION tick=" + tick + " " + mission.status());
                    if (mission.phase() == OrbitalMission.Phase.COAST && !askedToLand && AutopilotSession.enginesFaded(mission.body())) {
                        var body = mission.body();
                        var radial = frame.globalPosition(body.getLevel(), AutopilotSession.position(body)).minus(frame.getCENTRE());
                        var velocity = SubLevelPhysicsSystem.require(body.getLevel()).getPhysicsHandle(body).getLinearVelocity(new Vector3d());
                        double energy = velocity.lengthSquared() / 2 - 353160000 / radial.length();
                        double angle = Math.atan2(radial.getX(), radial.getY());
                        if (coastTicks++ == 0) { firstCoastEnergy = energy; previousCoastAngle = angle; }
                        coastAngle += OrbitalGuidance.angleStep(previousCoastAngle, angle); previousCoastAngle = angle;
                        helper.assertTrue(energy < 0 && Math.abs((energy - firstCoastEnergy) / firstCoastEnergy) < 0.001,
                            "Engines-off orbit did not preserve a bound orbit energy");
                        if (coastTicks >= 200) {
                            helper.assertTrue(coastAngle > 0.05, "Orbit did not advance after insertion");
                            helper.assertTrue(AutopilotSession.enginesFaded(body), "Coast retained real engine power");
                            var bookmarks = new LaunchSites(); bookmarks.put(pilot.player.getUUID(), site);
                            var restored = LaunchSites.load(bookmarks.save(new net.minecraft.nbt.CompoundTag(), rig.level.registryAccess()), rig.level.registryAccess())
                                .require(pilot.player.getUUID(), body.getUniqueId());
                            mission.close();
                            // Exercise manual return from a restored bookmark, with no resumed controller.
                            mission = new OrbitalMission(pilot.player, body, restored, transfer, true);
                            askedToLand = true;
                        }
                    }
                    if (landed) {
                        helper.assertTrue(phases.containsAll(java.util.List.of(OrbitalMission.Phase.CLIMB, OrbitalMission.Phase.INSERTION,
                            OrbitalMission.Phase.COAST, OrbitalMission.Phase.RETURN, OrbitalMission.Phase.DESCENT,
                            OrbitalMission.Phase.APPROACH, OrbitalMission.Phase.TOUCHDOWN)), "Mission skipped flight stages: " + phases);
                        helper.assertTrue(mission.body().getUniqueId().equals(site.ship()) && mission.body().getLevel() == rig.level,
                            "Mission changed ship identity or failed to return");
                        helper.assertTrue(AutopilotSession.position(mission.body()).minus(site.position()).length() < 0.5,
                            "Return missed launch point");
                        var cargo = mission.body().getPlot().getLoadedChunks().stream()
                            .flatMap(holder -> holder.getChunk().getBlockEntities().values().stream())
                            .filter(be -> be instanceof ChestBlockEntity).map(be -> (ChestBlockEntity)be).findFirst().orElseThrow();
                        helper.assertTrue(cargo.getItem(0).is(Items.COBBLESTONE) && cargo.getItem(0).getCount() == 16, "Mission changed cargo");
                        helper.assertTrue(AutopilotSession.enginesFaded(mission.body()), "Landing retained engine support");
                        System.out.println("ORBITALIS_MISSION_LANDED tick=" + tick + " " + mission.status());
                        helper.succeed(); return;
                    }
                }
                helper.runAfterDelay(1, () -> this.run());
            }
        }
        var sequence = new Sequence();
        helper.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo test) {}
            public void testPassed(GameTestInfo test, GameTestRunner runner) { sequence.close(); }
            public void testFailed(GameTestInfo test, GameTestRunner runner) { sequence.close(); }
            public void testAddedForRerun(GameTestInfo test, GameTestInfo retry, GameTestRunner runner) {}
        });
        helper.runAfterDelay(1, sequence);
    }
}
