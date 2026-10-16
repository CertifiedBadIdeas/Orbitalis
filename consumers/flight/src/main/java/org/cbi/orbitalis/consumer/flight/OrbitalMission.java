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

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.cbi.orbitalis.core.planet.CartesianState;
import org.cbi.orbitalis.core.planet.Vector3;
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace;
import org.cbi.orbitalis.minecraft.integration.sable.SableShipSnapshot;
import org.cbi.orbitalis.minecraft.integration.sable.SableShipTransfer;
import org.joml.Vector3d;
import java.util.Locale;

/** Disposable stand mission. Commands engines; the shared journal remains the only transfer owner. */
final class OrbitalMission implements AutoCloseable {
    enum Phase { ASCENT, CLIMB, INSERTION, COAST, RETURN, DESCENT, APPROACH, TOUCHDOWN, LANDED, FADE, TRANSFER }
    private static final FlightTestSpace SPACE = FlightTestSpace.INSTANCE;
    private static final Vector3 CENTRE = SPACE.getCENTRE();
    private static final double RADIUS = OrbitalGuidance.RADIUS;
    private static final double MU = SPACE.getGravity().getSurfaceAcceleration() * Math.pow(SPACE.getGravity().getHalfExtent(), 2);
    private static final double CIRCULAR_SPEED = Math.sqrt(MU / RADIUS);
    private static final double HANDOFF_Y = 1000;
    private final LaunchSites.Site site;
    private final SableShipTransfer transfer;
    private final Vector3 crown;
    private final Vector3 homeRadial;
    private final Vector3 normal;
    private ServerSubLevel body;
    private AutopilotSession engines;
    private Phase phase, afterTransfer;
    private int phaseTicks, settled, readyTicks;
    private double previousAngle, sweptAngle, returnAngle;
    private boolean returning, closed;

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }

    OrbitalMission(ServerPlayer pilot, ServerSubLevel body, LaunchSites.Site site,
                   SableShipTransfer transfer, boolean land) {
        this.body = body; this.site = site; this.transfer = transfer;
        require(site.ship().equals(body.getUniqueId()), "Launch bookmark belongs to another ship");
        require(!transfer.getBusy(), "Wait for transfer/recovery to finish");
        double x = site.position().getX(), z = site.position().getZ();
        crown = CENTRE.plus(new Vector3(x, Math.sqrt(RADIUS * RADIUS - x * x - z * z), z));
        homeRadial = crown.minus(CENTRE).normalized();
        var east = new Vector3(1, 0, 0);
        normal = homeRadial.cross(east.minus(homeRadial.times(east.dot(homeRadial))).normalized()).normalized();
        if (!land) {
            require(body.getLevel().dimension().equals(SPACE.getSURFACE()), "Launch from the flight test surface");
            SableShipSnapshot.INSTANCE.preflight(body, pilot);
            require(velocity().length() < 0.3, "Let the ship settle on its launch pad first");
            var bounds = body.boundingBox();
            int terrainTop = body.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
            require(Math.abs(bounds.minY() - terrainTop) < 1, "Start on a flat, clear launch pad on the terrain");
            phase = Phase.ASCENT;
        } else {
            require(body.getLevel().dimension().equals(SPACE.getSPACE()), "Return requires the full-space frame and a saved launch bookmark");
            phase = Phase.COAST;
            requestLanding();
        }
    }

    ServerSubLevel body() { return body; }
    Phase phase() { return phase; }
    private Vector3 position() { return SPACE.globalPosition(body.getLevel(), AutopilotSession.position(body)); }
    private Vector3 velocity() {
        return AutopilotSession.vector(SubLevelPhysicsSystem.require(body.getLevel()).getPhysicsHandle(body).getLinearVelocity(new Vector3d()));
    }
    private CartesianState state() { return new CartesianState(position(), velocity()); }
    private Vector3 handoff() { return new Vector3(site.position().getX(), HANDOFF_Y, site.position().getZ()); }
    private void enter(Phase next) { phase = next; phaseTicks = 0; settled = 0; }
    private void cut() { if (engines != null) { engines.close(); engines = null; } }
    private void drive(Vector3 acceleration) {
        if (engines == null) engines = new AutopilotSession(body, AutopilotSession.position(body), site.attitude(), true);
        engines.update(acceleration);
    }
    private boolean reached(Vector3 target, double tolerance) {
        return position().minus(target).length() < tolerance && velocity().length() < 0.25;
    }
    private void prepareTransfer(Phase next) {
        cut(); afterTransfer = next; enter(Phase.FADE);
    }

    void requestLanding() {
        require(phase == Phase.COAST, "Wait for orbital insertion to finish before requesting landing");
        var o = OrbitalGuidance.observe(state(), CENTRE, normal);
        require(Math.abs(o.radius() - RADIUS) < 1000 && Math.abs(o.planeError()) < 500,
            "Return requires the launch orbital plane near the 18 km orbit");
        previousAngle = OrbitalGuidance.angle(o.radial(), homeRadial, normal);
        sweptAngle = previousAngle;
        double arc = -previousAngle;
        while (arc < 0) arc += 2 * Math.PI;
        double stoppingArc = Math.max(0, o.tangentialSpeed()) * Math.max(0, o.tangentialSpeed()) / (2 * 1.2) / RADIUS;
        if (arc < stoppingArc + 0.05) arc += 2 * Math.PI;
        returnAngle = sweptAngle + arc;
        returning = true; enter(Phase.RETURN);
    }

    /** True only after engines are off and the native terrain has held the craft for 60 ticks. */
    boolean tick(ServerPlayer pilot) {
        require(!closed && pilot != null && pilot.isAlive(), "Pilot is unavailable");
        if (++phaseTicks > 40000) throw new IllegalStateException("Mission stage timed out: " + phase);
        if (transfer.getError() != null) throw new IllegalStateException("Transfer stopped: " + transfer.getError());
        if (phase == Phase.TRANSFER) {
            if (transfer.getBusy()) { readyTicks = 0; return false; }
            var target = afterTransfer == Phase.CLIMB ? SPACE.getSPACE() : SPACE.getSURFACE();
            var level = pilot.getServer().getLevel(target);
            var restored = (ServerSubLevel) SubLevelContainer.getContainer(level).getSubLevel(site.ship());
            require(restored != null && pilot.level() == level, "Transfer did not establish the destination ship and pilot");
            // The service has already fenced native tracking, vanilla teleport acknowledgment and
            // deck contact. Give newly loaded Propulsion connectivity its first resumed ticks too.
            if (++readyTicks < 5 || Sable.HELPER.getTrackingSubLevel(pilot) != restored) return false;
            body = restored; enter(afterTransfer); return false;
        }
        require(!body.isRemoved() && pilot.level() == body.getLevel() && Sable.HELPER.getTrackingSubLevel(pilot) == body,
            "Pilot must remain on the mission ship, with creative flight disabled");
        require(!SubLevelPhysicsSystem.require(body.getLevel()).getPaused(), "Native physics was paused outside a mission handoff");
        body.getLevel().resetEmptyTime();
        var state = state();
        require(state.getVelocity().length() < 220, "Mission speed exceeded 220 m/s");
        switch (phase) {
            case ASCENT -> {
                drive(OrbitalGuidance.point(state, handoff(), 60));
                if (reached(handoff(), 0.5)) prepareTransfer(Phase.CLIMB);
            }
            case FADE -> {
                require(phaseTicks < 200, "Native engines did not finish fading before handoff");
                if (AutopilotSession.enginesFaded(body)) {
                    pilot.closeContainer();
                    transfer.begin(pilot, body, afterTransfer == Phase.CLIMB ? SPACE.getSPACE() : SPACE.getSURFACE());
                    readyTicks = 0; enter(Phase.TRANSFER);
                }
            }
            case CLIMB -> {
                drive(OrbitalGuidance.point(state, crown, 80));
                // Start insertion while still moving. Requiring an exact far-field hover
                // stalls on the pinned float Rapier integration at large local Y.
                if (state.getPosition().minus(crown).length() < 100 && state.getVelocity().length() < 10) enter(Phase.INSERTION);
            }
            case INSERTION -> {
                var o = OrbitalGuidance.observe(state, CENTRE, normal);
                drive(OrbitalGuidance.orbit(state, CENTRE, normal, RADIUS, CIRCULAR_SPEED));
                boolean ready = Math.abs(o.radius() - RADIUS) < 5 && Math.abs(o.radialSpeed()) < 0.1 &&
                    Math.abs(o.tangentialSpeed() - CIRCULAR_SPEED) < 0.15 && Math.abs(o.planeError()) < 2 &&
                    SubLevelPhysicsSystem.require(body.getLevel()).getPhysicsHandle(body).getAngularVelocity(new Vector3d()).length() < 0.001;
                settled = ready ? settled + 1 : 0;
                if (settled >= 60) { cut(); enter(Phase.COAST); }
            }
            case COAST -> {
                var o = OrbitalGuidance.observe(state, CENTRE, normal);
                require(Math.abs(o.radius() - RADIUS) < 500, "Coast drifted outside the tested orbit; manual control required");
            }
            case RETURN -> {
                var o = OrbitalGuidance.observe(state, CENTRE, normal);
                double angle = OrbitalGuidance.angle(o.radial(), homeRadial, normal);
                sweptAngle += OrbitalGuidance.angleStep(previousAngle, angle); previousAngle = angle;
                double remaining = (returnAngle - sweptAngle) * RADIUS;
                drive(OrbitalGuidance.orbit(state, CENTRE, normal, RADIUS, OrbitalGuidance.returnSpeed(remaining, CIRCULAR_SPEED)));
                // As at insertion, begin the next vertical leg without an exact far-field hover.
                if (state.getPosition().minus(crown).length() < 10 && state.getVelocity().length() < 2) enter(Phase.DESCENT);
            }
            case DESCENT -> {
                drive(OrbitalGuidance.point(state, handoff(), 80));
                // The journal preserves motion; only a safe bounded handoff is needed here.
                if (state.getPosition().minus(handoff()).length() < 5 && state.getVelocity().length() < 2) prepareTransfer(Phase.APPROACH);
            }
            case APPROACH -> {
                // The remembered COM and attitude preserve landing-leg clearance. Reduce speed
                // near the ground; terrain collisions still belong entirely to native Sable.
                double clearance = state.getPosition().getY() - site.position().getY();
                Vector3 touchdown = site.position().plus(new Vector3(0, -0.08, 0));
                drive(OrbitalGuidance.point(state, touchdown, clearance < 5 ? 0.4 : clearance < 40 ? 3 : 40));
                if (reached(site.position(), 0.2)) { cut(); enter(Phase.TOUCHDOWN); }
            }
            case TOUCHDOWN -> {
                require(position().minus(site.position()).length() < 1, "Terrain did not support touchdown at the launch point");
                if (phaseTicks >= 60 && AutopilotSession.enginesFaded(body) && velocity().length() < 0.2) {
                    enter(Phase.LANDED); return true;
                }
                require(phaseTicks < 200, "Touchdown did not settle on native terrain");
            }
            case LANDED -> { return true; }
            default -> throw new IllegalStateException("Unexpected mission stage");
        }
        return false;
    }

    String status() {
        if (body.isRemoved()) return "Mission " + phase + "; waiting for journaled handoff";
        var p = position().minus(CENTRE); var v = velocity();
        double radius = p.length();
        double energy = v.dot(v) / 2 - MU / radius;
        double eccentricity = v.cross(p.cross(v)).times(1 / MU).minus(p.times(1 / radius)).length();
        return String.format(Locale.ROOT, "Mission %s%s; radius=%.1f m; speed=%.2f m/s; radial=%.2f m/s; far-field energy=%.2f J/kg; e=%.5f; launch error=%.1f m. %s",
            phase, returning ? " (return to launch)" : "", radius, v.length(), v.dot(p) / radius, energy, eccentricity,
            position().minus(site.position()).length(), phase == Phase.COAST ? "Engines off; free orbit." : "Real Propulsion engines only.");
    }
    @Override public void close() {
        if (closed) return;
        closed = true; cut();
    }
}
