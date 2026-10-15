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

import org.cbi.orbitalis.core.planet.Vector3;
import java.util.Arrays;
import java.util.List;

/** Bounded nonnegative least squares over native engine wrenches in body axes. */
final class ThrusterAllocation {
    record Actuator(Vector3 force, Vector3 torque) {}
    record Result(List<Double> throttle, Vector3 forceResidual, Vector3 torqueResidual) {}

    private static double[] row(Vector3 force, Vector3 torque, double lever) {
        return new double[]{force.getX(), force.getY(), force.getZ(),
            torque.getX() / lever, torque.getY() / lever, torque.getZ() / lever};
    }

    static Result solve(List<Actuator> actuators, Vector3 force, Vector3 torque, double lever) {
        if (actuators.isEmpty() || actuators.size() > 64 || !Double.isFinite(lever) || lever <= 0)
            throw new IllegalArgumentException("Invalid actuator count or lever length");
        var columns = actuators.stream().map(a -> row(a.force, a.torque, lever)).toList();
        double[] norms = columns.stream().mapToDouble(c -> Arrays.stream(c).map(v -> v * v).sum()).toArray();
        if (Arrays.stream(norms).anyMatch(v -> !Double.isFinite(v)))
            throw new IllegalArgumentException("Invalid actuator wrench");
        double regularization = Arrays.stream(norms).max().orElseThrow() * 1e-10;
        double[] residual = row(force, torque, lever);
        double[] throttle = new double[actuators.size()];
        for (int iteration = 0; iteration < 256; iteration++) {
            double largestChange = 0;
            for (int i = 0; i < columns.size(); i++) {
                if (norms[i] == 0) continue;
                double[] column = columns.get(i);
                double dot = 0;
                for (int j = 0; j < 6; j++) dot += column[j] * residual[j];
                double next = Math.clamp(throttle[i] + (dot - regularization * throttle[i]) /
                    (norms[i] + regularization), 0.0, 1.0);
                double change = next - throttle[i];
                throttle[i] = next;
                for (int j = 0; j < 6; j++) residual[j] -= column[j] * change;
                largestChange = Math.max(largestChange, Math.abs(change));
            }
            if (largestChange < 1e-8) break;
        }
        var actualForce = Vector3.Companion.getZERO();
        var actualTorque = Vector3.Companion.getZERO();
        for (int i = 0; i < actuators.size(); i++) {
            actualForce = actualForce.plus(actuators.get(i).force.times(throttle[i]));
            actualTorque = actualTorque.plus(actuators.get(i).torque.times(throttle[i]));
        }
        return new Result(Arrays.stream(throttle).boxed().toList(), force.minus(actualForce), torque.minus(actualTorque));
    }
}
