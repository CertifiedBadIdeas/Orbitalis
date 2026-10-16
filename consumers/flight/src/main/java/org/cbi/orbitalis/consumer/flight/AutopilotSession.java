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

import dev.propulsionteam.propulsionsimulated.PropulsionConfig;
import dev.propulsionteam.propulsionsimulated.content.thruster.AbstractThrusterBlockEntity;
import dev.propulsionteam.propulsionsimulated.content.thruster.ThrusterForceProvider;
import dev.propulsionteam.propulsionsimulated.content.thruster.thruster.creative_thruster.CreativeThrusterBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.cbi.orbitalis.core.planet.CartesianState;
import org.cbi.orbitalis.core.planet.Vector3;
import org.cbi.orbitalis.minecraft.flight.FlightTestSpace;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Ephemeral consumer-only owner of real engine inputs; native engines alone apply propulsion. */
public final class AutopilotSession implements AutoCloseable {
    public final ServerSubLevel body;
    public final Vector3 target;
    private final Quaterniond attitude;
    private final List<Engine> engines = new ArrayList<>();
    private ThrusterAllocation.Result allocation;
    private boolean closed;
    private static final class Engine {
        final CreativeThrusterBlockEntity block;
        double capacity;
        Engine(CreativeThrusterBlockEntity block) {
            this.block = block;
            capacity = block.getTargetThrustNewtons();
        }
    }

    public static Vector3 vector(Vector3dc v) { return new Vector3(v.x(), v.y(), v.z()); }
    private static Vector3d joml(Vector3 v) { return new Vector3d(v.getX(), v.getY(), v.getZ()); }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }

    public static Vector3 position(ServerSubLevel body) {
        return vector(body.logicalPose().transformPosition(body.getMassTracker().getCenterOfMass(), new Vector3d()));
    }

    public AutopilotSession(ServerSubLevel body, Vector3 target) {
        this(body, target, new Quaterniond(body.logicalPose().orientation()), false);
    }

    AutopilotSession(ServerSubLevel body, Vector3 target, Quaterniond desiredAttitude, boolean mission) {
        this.body = body;
        this.target = target;
        require(FlightTestSpace.INSTANCE.contains(body.getLevel()), "Use the flight/orbit test spaces");
        require(body.getMassTracker().getMass() > 0 && body.getMassTracker().getCenterOfMass() != null, "Wait for native mass initialization");
        require(body.getPlot().getLoadedChunks().size() <= 4, "Autopilot supports at most four native plot chunks");
        attitude = new Quaterniond(desiredAttitude);
        for (var holder : body.getPlot().getLoadedChunks()) {
            for (BlockEntity block : holder.getChunk().getBlockEntities().values()) {
                if (!(block instanceof AbstractThrusterBlockEntity thruster)) continue;
                require(thruster instanceof CreativeThrusterBlockEntity, "This test adapter supports creative Propulsion engines only");
                if (!thruster.isController()) continue;
                require(thruster.getPower() == 0.0f, "Turn the linked controller and all engine inputs off first");
                require(thruster.saveWithFullMetadata(body.getLevel().registryAccess()).getInt("ControlMode") ==
                    AbstractThrusterBlockEntity.ControlMode.NORMAL.ordinal(), "An engine already has a peripheral owner");
                engines.add(new Engine((CreativeThrusterBlockEntity) thruster));
            }
        }
        engines.sort(Comparator.comparingLong(e -> e.block.getBlockPos().asLong()));
        require(!engines.isEmpty() && engines.size() <= 64, "Autopilot requires 1..64 creative engine controllers");
        require(engines.stream().allMatch(e -> Double.isFinite(e.capacity) && e.capacity > 0), "An engine has no configured thrust");
        validateBounds();
        var gravity = gravity();
        var localForce = attitude.transformInverse(joml(gravity).mul(-body.getMassTracker().getMass()));
        var hover = ThrusterAllocation.solve(actuators(), vector(localForce), Vector3.Companion.getZERO(), lever());
        require(hover.forceResidual().length() / body.getMassTracker().getMass() < 0.25,
            String.format(Locale.ROOT, "Installed engines cannot balance gravity at this attitude (mass=%.2f; total capacity=%.2f; residual=%.2f m/s²); add/reorient engines or increase their configured thrust",
                body.getMassTracker().getMass(), engines.stream().mapToDouble(e -> e.capacity).sum(),
                hover.forceResidual().length() / body.getMassTracker().getMass()));
        if (mission) {
            for (var axis : List.of(new Vector3(1, 0, 0), new Vector3(0, 1, 0), new Vector3(0, 0, 1))) {
                for (double sign : new double[]{-1, 1}) {
                    var force = attitude.transformInverse(joml(axis.times(sign * OrbitalGuidance.MAX_ACCELERATION).minus(gravity))
                        .mul(body.getMassTracker().getMass()));
                    var margin = ThrusterAllocation.solve(actuators(), vector(force), Vector3.Companion.getZERO(), lever());
                    require(margin.forceResidual().length() / body.getMassTracker().getMass() < 0.25,
                        "Mission needs balanced thrust authority for 3 m/s² in all six directions in addition to gravity");
                }
            }
        }
        allocation = hover;
        // No engine is touched until every preflight check succeeds.
        for (Engine e : engines) {
            e.block.setDigitalInput(0);
            e.block.setControlMode(AbstractThrusterBlockEntity.ControlMode.PERIPHERAL);
        }
    }

    private double lever() {
        double length = 1;
        var com = body.getMassTracker().getCenterOfMass();
        for (Engine e : engines) {
            length = Math.max(length, ThrusterForceProvider.createSample(e.block, 1.0).pointLocal().distance(com));
        }
        return length;
    }

    private List<ThrusterAllocation.Actuator> actuators() {
        var result = new ArrayList<ThrusterAllocation.Actuator>();
        var com = body.getMassTracker().getCenterOfMass();
        for (Engine e : engines) {
            var sample = ThrusterForceProvider.createSample(e.block, 1.0);
            var force = new Vector3d(e.block.getThrustDirectionLocal()).mul(e.capacity);
            var arm = new Vector3d(sample.pointLocal()).sub(com);
            result.add(new ThrusterAllocation.Actuator(vector(force), vector(arm.cross(force, new Vector3d()))));
        }
        return result;
    }

    private Vector3 gravity() {
        var global = FlightTestSpace.INSTANCE.globalPosition(body.getLevel(), position(body));
        return FlightTestSpace.INSTANCE.getGravity().acceleration(global.minus(FlightTestSpace.INSTANCE.getCENTRE()));
    }

    private void validateBounds() {
        require(position(body).getY() >= 64 && target.getY() >= 64,
            "Keep ship and target above Y=64 in the current dimension; orbit altitude must be at least 256");
        var global = FlightTestSpace.INSTANCE.globalPosition(body.getLevel(), position(body));
        var goal = FlightTestSpace.INSTANCE.globalPosition(body.getLevel(), target);
        for (Vector3 p : List.of(global, goal)) {
            if (body.getLevel().dimension().equals(FlightTestSpace.INSTANCE.getSPACE())) {
                double radius = p.minus(FlightTestSpace.INSTANCE.getCENTRE()).length();
                require(radius >= 6500 && radius <= 24000, "Outside the full-space autopilot envelope");
                continue;
            }
            require(Math.abs(p.getX()) <= 2048 && Math.abs(p.getZ()) <= 2048 && p.getY() >= 64 && p.getY() <= 16000,
                "Autopilot left the diagnostic top-face patch or height bounds");
        }
    }

    /** Call once per server tick before native block-entity ticking. */
    public void update() { update(null); }

    /** A mission supplies net inertial acceleration; gravity compensation remains measured here. */
    void update(Vector3 netAcceleration) {
        require(!closed && !body.isRemoved(), "Ship/controller is no longer available");
        validateBounds();
        for (Engine e : engines) {
            require(!e.block.isRemoved() && e.block.isController() &&
                body.getLevel().getBlockEntity(e.block.getBlockPos()) == e.block,
                "Engine layout changed; engage again after assembly settles");
            // Native measured steady capacity includes atmospheric/configuration multipliers.
            // Do not recreate Propulsion's private thrust formula or overwrite its power setting.
            float effective = e.block.getEffectiveThrottle();
            if (effective > 1e-5 && e.block.getCurrentThrust() > 0) {
                e.capacity = e.block.getCurrentThrust() / effective / PropulsionConfig.getThrustUnitsPerKnOrDefault();
            }
        }
        var physics = SubLevelPhysicsSystem.require(body.getLevel());
        var handle = physics.getPhysicsHandle(body);
        var pose = body.logicalPose();
        var velocity = handle.getLinearVelocity(new Vector3d());
        var omega = pose.orientation().transformInverse(handle.getAngularVelocity(new Vector3d()), new Vector3d());
        require(velocity.length() <= (body.getLevel().dimension().equals(FlightTestSpace.INSTANCE.getSPACE()) ? 220 : 120) && omega.length() <= 2.0, "Speed/rotation exceeded test autopilot limits");
        var error = new Quaterniond(pose.orientation()).conjugate().mul(attitude).normalize();
        if (error.w < 0) error.mul(-1);
        double sine = Math.sqrt(error.x * error.x + error.y * error.y + error.z * error.z);
        double factor = sine > 1e-8 ? 2 * Math.atan2(sine, error.w) / sine : 2.0;
        var demand = FlightHoldGuidance.command(new CartesianState(position(body), vector(velocity)), target,
            gravity(), new Vector3(error.x * factor, error.y * factor, error.z * factor), vector(omega));
        var acceleration = netAcceleration == null ? demand.thrustAcceleration() : netAcceleration.minus(gravity());
        var force = pose.orientation().transformInverse(joml(acceleration).mul(body.getMassTracker().getMass()));
        var inertia = body.getMassTracker().getInertiaTensor();
        var torque = inertia.transform(joml(demand.angularAcceleration()), new Vector3d());
        torque.add(new Vector3d(omega).cross(inertia.transform(omega, new Vector3d())));
        allocation = ThrusterAllocation.solve(actuators(), vector(force), vector(torque), lever());
        for (int i = 0; i < engines.size(); i++) engines.get(i).block.setDigitalInput(allocation.throttle().get(i).floatValue());
    }

    static boolean enginesFaded(ServerSubLevel body) {
        for (var holder : body.getPlot().getLoadedChunks()) for (var block : holder.getChunk().getBlockEntities().values()) {
            if (block instanceof AbstractThrusterBlockEntity engine && engine.isController() && (engine.getPower() != 0.0f || engine.getCurrentThrust() != 0.0)) return false;
        }
        return true;
    }

    public String status() {
        double mass = body.getMassTracker().getMass();
        return String.format(Locale.ROOT,
            "Autopilot: %d engines; target COM=(%.2f,%.2f,%.2f); error=%.2f m; predicted force residual=%.3f m/s²; torque residual=%.3f. Missing authority is not replaced by virtual forces.",
            engines.size(), target.getX(), target.getY(), target.getZ(), position(body).minus(target).length(),
            allocation == null ? 0 : allocation.forceResidual().length() / mass,
            allocation == null ? 0 : allocation.torqueResidual().length());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (Engine e : engines) {
            if (e.block.isRemoved()) continue;
            e.block.setDigitalInput(0);
            e.block.setRedstoneInput(0);
            e.block.setControlMode(AbstractThrusterBlockEntity.ControlMode.NORMAL);
            e.block.dirtyThrust();
            e.block.notifyUpdate();
        }
    }
}
